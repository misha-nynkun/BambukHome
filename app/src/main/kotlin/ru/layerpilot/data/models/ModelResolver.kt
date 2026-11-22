package ru.layerpilot.data.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.util.concurrent.TimeUnit

enum class ModelSource(val label: String) {
    MAKERWORLD("MakerWorld"),
    THINGIVERSE("Thingiverse"),
    PRINTABLES("Printables"),
    DIRECT("Файл по ссылке"),
    OTHER("Интернет"),
}

data class RemoteModelFile(
    val name: String,
    val url: String,
    val size: Long = -1,
    val note: String = "",
    val downloadable: Boolean = true,
)

data class ModelLink(
    val source: ModelSource,
    val pageUrl: String,
    val title: String,
    val author: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val license: String = "",
    val files: List<RemoteModelFile> = emptyList(),
    val note: String = "",
) {
    val canDownload: Boolean get() = files.isNotEmpty()
}

class ModelResolver(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val cacheDir: File
        get() = File(context.cacheDir, "models").apply { mkdirs() }

    suspend fun resolve(rawUrl: String, thingiverseToken: String = ""): ModelLink = withContext(Dispatchers.IO) {
        val url = normalize(rawUrl)
        val host = runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")
        when {
            url.matches(Regex(".*\\.(3mf|gcode|stl|obj|step|stp)(\\?.*)?$", RegexOption.IGNORE_CASE)) ->
                directFile(url)
            host.contains("makerworld", true) -> parsePage(url, ModelSource.MAKERWORLD)
            host.contains("thingiverse", true) -> thingiverse(url, thingiverseToken)
            host.contains("printables", true) -> {
                val id = Regex("/model/(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
                if (id != null) printables(id, url) else parsePage(url, ModelSource.PRINTABLES)
            }
            else -> parsePage(url, ModelSource.OTHER)
        }
    }

    private fun normalize(raw: String): String {
        val s = raw.trim()
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        return if (s.isBlank()) s else "https://$s"
    }

    private fun directFile(url: String): ModelLink {
        val name = url.substringBefore('?').substringAfterLast('/').ifBlank { "model.3mf" }
        return ModelLink(
            source = ModelSource.DIRECT,
            pageUrl = url,
            title = name.substringBeforeLast('.'),
            files = listOf(RemoteModelFile(name, url)),
            note = "Прямая ссылка на файл",
        )
    }

    /** Карточка-заглушка, когда сайт закрылся от автоматического запроса. */
    fun fallbackLink(rawUrl: String, reason: String?, sourceHint: ModelSource? = null): ModelLink {
        val url = normalize(rawUrl)
        val host = runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")
        val source = sourceHint ?: when {
            host.contains("makerworld", true) -> ModelSource.MAKERWORLD
            host.contains("thingiverse", true) -> ModelSource.THINGIVERSE
            host.contains("printables", true) -> ModelSource.PRINTABLES
            else -> ModelSource.OTHER
        }
        val slug = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
        val pretty = slug.replace('-', ' ').replace('_', ' ').trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale("ru")) else it.toString() }
        return ModelLink(
            source = source,
            pageUrl = url,
            title = pretty.ifBlank { "Модель по ссылке" },
            note = buildString {
                append("Сайт не отдал файл автоматически")
                reason?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                append(". Откройте страницу, скачайте готовый .3mf и вернитесь через «Файл».")
            },
        )
    }

    private fun fetch(url: String): Pair<Document, String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            .header("Accept-Language", "ru,en;q=0.8")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ModelException(
                    if (response.code == 403 || response.code == 503) "защита сайта, код ${response.code}"
                    else "код ${response.code}"
                )
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw ModelException("Сайт вернул пустую страницу")
            return Jsoup.parse(body, response.request.url.toString()) to response.request.url.toString()
        }
    }

    private fun parsePage(url: String, source: ModelSource): ModelLink {
        val (doc, finalUrl) = fetch(url)
        val title = meta(doc, "og:title").ifBlank { doc.title() }
        val image = meta(doc, "og:image")
        val description = meta(doc, "og:description")
        val author = doc.selectFirst("a[href*=\"/@\"]")?.text()?.trim()
            ?: doc.selectFirst("[class*=author]")?.text()?.trim().orEmpty()
        val direct = extractFileLinks(doc, finalUrl)
        val note = when (source) {
            ModelSource.PRINTABLES ->
                "Printables отдаёт файлы после входа в аккаунт. Если список пуст — скачайте 3MF на сайте и откройте через «Импорт файла»."
            ModelSource.MAKERWORLD ->
                "MakerWorld защищает скачивание входом в аккаунт и Cloudflare. Откройте страницу, скачайте 3MF и используйте «Импорт файла»."
            else -> ""
        }
        return ModelLink(
            source = source,
            pageUrl = finalUrl,
            title = title.ifBlank { "Модель" },
            author = author,
            imageUrl = image,
            description = description.take(500),
            files = direct,
            note = note,
        )
    }

    /** Публичный GraphQL Printables: метаданные и имена файлов без Cloudflare. */
    private fun printables(id: Long, pageUrl: String): ModelLink {
        val query = """
            query PrintInfo(${'$'}id: ID!) {
              print(id: ${'$'}id) {
                id name summary description filesCount downloadCount likesCount slug
                stls { id name fileSize folder }
                gcodes { id name fileSize folder }
              }
            }
        """.trimIndent()
        val body = JSONObject().put("query", query).put("variables", JSONObject().put("id", id.toString()))
        val request = Request.Builder()
            .url("https://api.printables.com/graphql/")
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), body.toString()))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) BambukHome/1.0.1")
            .header("Accept", "application/json")
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ModelException("API Printables: код ${response.code}")
                val root = JSONObject(response.body?.string().orEmpty())
                val print = root.optJSONObject("data")?.optJSONObject("print")
                    ?: throw ModelException("Printables не вернул модель")
                val files = ArrayList<RemoteModelFile>()
                listOf("stls", "gcodes").forEach { key ->
                    val arr = print.optJSONArray(key) ?: return@forEach
                    for (i in 0 until arr.length()) {
                        val f = arr.optJSONObject(i) ?: continue
                        val name = f.optString("name").ifBlank { "file_$i" }
                        files += RemoteModelFile(
                            name = name,
                            url = pageUrl,
                            size = f.optLong("fileSize", -1),
                            downloadable = false,
                        )
                    }
                }
                val description = Jsoup.parse(print.optString("description")).text()
                ModelLink(
                    source = ModelSource.PRINTABLES,
                    pageUrl = pageUrl,
                    title = print.optString("name").ifBlank { "Модель Printables" },
                    description = (print.optString("summary").ifBlank { description }).take(500),
                    files = files,
                    note = "Printables отдаёт список файлов через API, но прямая загрузка закрыта Cloudflare. " +
                        "Нажмите «Открыть на сайте», скачайте .3mf и добавьте через «Файл».",
                )
            }
        } catch (e: Exception) {
            fallbackLink(pageUrl, e.message, ModelSource.PRINTABLES)
        }
    }

    private fun thingiverse(url: String, token: String): ModelLink {
        val id = Regex("(?:thing:|things?/|thing/)(\\d+)").find(url)?.groupValues?.get(1)
        val page = parsePage(url, ModelSource.THINGIVERSE)
        if (id == null || token.isBlank()) {
            return page.copy(note = if (id == null) page.note else
                "Чтобы скачивать файлы с Thingiverse, добавьте токен API в настройках. Ссылку можно открыть в браузере.")
        }
        return runCatching {
            val thing = apiJson("https://api.thingiverse.com/things/$id", token)
            val files = apiJsonArray("https://api.thingiverse.com/things/$id/files", token)
            val list = ArrayList<RemoteModelFile>()
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val download = f.optString("download_url")
                val name = f.optString("name").ifBlank { "file_$i" }
                if (download.isNotBlank()) {
                    list += RemoteModelFile(
                        name = if (name.contains('.')) name else "$name.${download.substringBefore('?').substringAfterLast('.', "stl")}",
                        url = download,
                        size = f.optLong("size", -1),
                    )
                }
            }
            val creator = thing.optJSONObject("creator")?.optString("name").orEmpty()
            page.copy(
                title = thing.optString("name").ifBlank { page.title },
                author = creator.ifBlank { page.author },
                imageUrl = thing.optString("thumbnail").ifBlank { page.imageUrl },
                files = list,
                note = if (list.isEmpty()) "У модели нет доступных файлов" else "Файлы Thingiverse доступны для скачивания",
            )
        }.getOrElse { e ->
            page.copy(note = "Thingiverse API: ${e.message ?: "ошибка"}. Проверьте токен в настройках.")
        }
    }

    private fun apiJson(url: String, token: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw ModelException("код ${r.code}")
            return JSONObject(r.body?.string().orEmpty())
        }
    }

    private fun apiJsonArray(url: String, token: String): org.json.JSONArray {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw ModelException("код ${r.code}")
            return org.json.JSONArray(r.body?.string().orEmpty())
        }
    }

    private fun meta(doc: Document, property: String): String =
        doc.selectFirst("meta[property=$property]")?.attr("content")
            ?: doc.selectFirst("meta[name=$property]")?.attr("content")
            ?: ""

    private fun extractFileLinks(doc: Document, baseUrl: String): List<RemoteModelFile> {
        val found = LinkedHashMap<String, RemoteModelFile>()
        val pattern = Regex("https?://[^\"'\\s<>]+?\\.(3mf|stl|obj|step|stp)(\\?[^\"'\\s<>]*)?", RegexOption.IGNORE_CASE)
        doc.html().let { html ->
            pattern.findAll(html).forEach { match ->
                val url = match.value.replace("&amp;", "&")
                if (url.contains("thumbnail") || url.contains("preview")) return@forEach
                val name = url.substringBefore('?').substringAfterLast('/').ifBlank { "model.${match.groupValues[1]}" }
                if (!found.containsKey(name)) found[name] = RemoteModelFile(name, url)
            }
        }
        doc.select("a[href]").forEach { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            if (pattern.matches(href)) {
                val name = href.substringBefore('?').substringAfterLast('/')
                if (name.isNotBlank()) found.putIfAbsent(name, RemoteModelFile(name, href))
            }
        }
        return found.values.toList()
    }

    suspend fun download(
        file: RemoteModelFile,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val safe = file.name.replace(Regex("[^\\p{L}\\p{N}._\\-]+"), "_").take(90)
            .ifBlank { "model.3mf" }
        val target = File(cacheDir, safe)
        val request = Request.Builder()
            .url(file.url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            .header("Referer", "https://www.printables.com/")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ModelException("скачивание: код ${response.code}")
            val body = response.body ?: throw ModelException("пустой ответ")
            val total = body.contentLength()
            if (total > 400L * 1024 * 1024) throw ModelException("файл больше 400 МБ")
            target.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(128 * 1024)
                    var sent = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        out.write(buf, 0, read)
                        sent += read
                        onProgress(sent, total)
                    }
                }
            }
        }
        target
    }

    companion object {
        fun isPrintableFile(name: String): Boolean =
            name.endsWith(".3mf", true) || name.endsWith(".gcode", true)
    }
}

class ModelException(message: String) : Exception(message)
