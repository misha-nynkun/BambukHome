package ru.layerpilot.data.makerworld

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Аккаунт Bambu Lab, он же аккаунт MakerWorld. */
data class MakerWorldAccount(
    val uid: Long,
    val name: String,
    val handle: String,
    val avatar: String,
    val bio: String,
)

data class MakerWorldSession(
    val accessToken: String,
    val refreshToken: String,
    val email: String,
    val expiresAt: Long,
)

data class MakerWorldModel(
    val id: Long,
    val title: String,
    val slug: String,
    val cover: String,
    val author: String,
    val authorAvatar: String = "",
    val likes: Int = 0,
    val downloads: Int = 0,
    val prints: Int = 0,
    val license: String = "",
    val createTime: String = "",
) {
    val pageUrl: String
        get() = "https://makerworld.com/ru/models/$id" + if (slug.isBlank()) "" else "-$slug"
}

data class MakerWorldFile(
    val type: String,
    val color: String,
    val grams: Float,
)

data class MakerWorldPlate(
    val index: Int,
    val name: String,
    val thumbnail: String,
    val seconds: Int,
    val grams: Int,
    val files: List<MakerWorldFile> = emptyList(),
)

data class MakerWorldInstance(
    val id: Long,
    val profileId: Long,
    val title: String,
    val cover: String,
    val needAms: Boolean,
    val seconds: Int,
    val grams: Int,
    val printer: String,
    val files: List<MakerWorldFile> = emptyList(),
    val plates: List<MakerWorldPlate> = emptyList(),
)

data class MakerWorldDetail(
    val model: MakerWorldModel,
    val description: String,
    val tags: List<String>,
    val instances: List<MakerWorldInstance>,
    val defaultInstanceId: Long,
)

data class MakerWorldPage(
    val models: List<MakerWorldModel>,
    val total: Int,
)

data class MakerWorldCollection(
    val id: Long,
    val name: String,
    val cover: String,
    val count: Int,
)

data class MakerWorldCollections(
    val items: List<MakerWorldCollection>,
    val total: Int,
)

data class MakerWorldDownload(
    val file: File,
    val name: String,
)

sealed class MakerWorldLogin {
    data class Done(val account: MakerWorldAccount? = null) : MakerWorldLogin()
    data class NeedCode(val email: String) : MakerWorldLogin()
    data class NeedTotp(val tfaKey: String) : MakerWorldLogin()
    data class Error(val message: String, val captcha: Boolean = false) : MakerWorldLogin()
}

class MakerWorldException(message: String, val status: Int = 0) : Exception(message)

/**
 * Клиент MakerWorld поверх Bambu Cloud.
 *
 * Публичные поиск и карточки моделей не требуют входа. Для «Моих моделей»,
 * избранного и скачивания 3MF нужен Bearer-токен Bambu Cloud. Токен получаем
 * через официальный `/v1/user-service/user/login` (email + код письма или TOTP),
 * храним зашифрованным ключом Android Keystore.
 */
class MakerWorldRepository(private val context: Context) {

    private val api = "https://api.bambulab.com/v1"
    private val web = "https://bambulab.com"
    private val tokenStore = TokenStore(context)
    private val _session = MutableStateFlow(tokenStore.load())
    val session: StateFlow<MakerWorldSession?> = _session.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val cdn = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // ------------------------------------------------------------------ auth

    suspend fun login(email: String, password: String): MakerWorldLogin = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("account", email.trim())
                .put("password", password)
            val response = post("$api/user-service/user/login", body)
            handleLoginResponse(response, email.trim())
        }.getOrElse { error -> loginError(error) }
    }

    suspend fun verifyCode(email: String, code: String): MakerWorldLogin = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("account", email.trim())
                .put("code", code.trim())
            val response = post("$api/user-service/user/login", body)
            handleLoginResponse(response, email.trim())
        }.getOrElse { error -> loginError(error) }
    }

    suspend fun resendCode(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("email", email.trim())
                .put("type", "codeLogin")
            val response = post("$api/user-service/user/sendemail/code", body)
            if (response.code !in 200..299) {
                throw MakerWorldException(errorText(response.body, response.code), response.code)
            }
        }
    }

    suspend fun verifyTotp(tfaKey: String, code: String): MakerWorldLogin = withContext(Dispatchers.IO) {
        runCatching {
            val csrf = fetchCsrf()
            val body = JSONObject()
                .put("tfaKey", tfaKey)
                .put("tfaCode", code.trim())
            val request = Request.Builder()
                .url("$web/api/sign-in/tfa")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .apply {
                    if (csrf != null) {
                        header("x-bbl-csrf-token", csrf)
                        header("Cookie", "bbl_csrf_token=$csrf")
                    }
                }
                .post(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.code !in 200..299) {
                    throw MakerWorldException(errorText(raw, response.code), response.code)
                }
                val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
                var token = json.optString("accessToken").ifBlank { json.optString("token") }
                if (token.isBlank()) {
                    token = response.headers("Set-Cookie")
                        .firstOrNull { it.substringBefore('=').contains("token", true) }
                        ?.substringAfter('=')?.substringBefore(';').orEmpty()
                }
                if (token.isBlank()) throw MakerWorldException("Bambu не вернул токен входа")
                save(MakerWorldSession(token, json.optString("refreshToken"), "", 0))
                MakerWorldLogin.Done()
            }
        }.getOrElse { error -> loginError(error) }
    }

    fun logout() {
        tokenStore.clear()
        _session.value = null
    }

    private fun handleLoginResponse(response: HttpResult, email: String): MakerWorldLogin {
        val json = runCatching { JSONObject(response.body) }.getOrNull()
            ?: return MakerWorldLogin.Error(errorText(response.body, response.code), response.code == 418)
        if (response.code == 418) {
            return MakerWorldLogin.Error(
                "Bambu просит подтвердить, что вы не робот. Откройте вход в браузере или попробуйте позже.",
                captcha = true,
            )
        }
        if (response.code !in 200..299) {
            return MakerWorldLogin.Error(errorText(response.body, response.code))
        }
        val loginType = json.optString("loginType")
        if (loginType == "verifyCode") return MakerWorldLogin.NeedCode(email)
        if (loginType == "tfa") {
            val key = json.optString("tfaKey")
            if (key.isNotBlank()) return MakerWorldLogin.NeedTotp(key)
        }
        val token = json.optString("accessToken")
        if (token.isNotBlank()) {
            save(
                MakerWorldSession(
                    accessToken = token,
                    refreshToken = json.optString("refreshToken"),
                    email = email,
                    expiresAt = expiresAt(json),
                ),
            )
            return MakerWorldLogin.Done()
        }
        return MakerWorldLogin.Error(errorText(response.body, response.code))
    }

    private fun expiresAt(json: JSONObject): Long {
        val seconds = json.optLong("expiresIn", 0)
        return if (seconds > 0) System.currentTimeMillis() + seconds * 1000 else 0
    }

    private fun save(session: MakerWorldSession) {
        tokenStore.save(session)
        _session.value = session
    }

    private suspend fun fetchCsrf(): String? {
        val request = Request.Builder()
            .url("$web/api/csrf")
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                response.headers("Set-Cookie")
                    .firstOrNull { it.startsWith("bbl_csrf_token=") }
                    ?.substringAfter('=')?.substringBefore(';')
            }
        }.getOrNull()
    }

    // ----------------------------------------------------------------- account

    suspend fun account(): MakerWorldAccount = withContext(Dispatchers.IO) {
        val token = requireToken()
        val first = get("$api/design-user-service/my/preference", token)
        if (first.code == 200) {
            val json = runCatching { JSONObject(first.body) }.getOrDefault(JSONObject())
            if (json.optLong("uid", 0) > 0 || json.optString("name").isNotBlank()) {
                return@withContext MakerWorldAccount(
                    uid = json.optLong("uid"),
                    name = json.optString("name").ifBlank { json.optString("handle") },
                    handle = json.optString("handle"),
                    avatar = json.optString("avatar"),
                    bio = json.optString("bio"),
                )
            }
        }
        if (first.code == 401) {
            expired()
            throw MakerWorldException("Сессия истекла. Войдите снова.", 401)
        }
        if (first.code !in 200..404 && first.code != 405) {
            throw MakerWorldException(errorText(first.body, first.code), first.code)
        }
        val second = get("$api/user-service/my/profile", token)
        if (second.code == 401) {
            expired()
            throw MakerWorldException("Сессия истекла. Войдите снова.", 401)
        }
        val root = runCatching { JSONObject(second.body) }.getOrDefault(JSONObject())
        val data = root.optJSONObject("data") ?: root
        MakerWorldAccount(
            uid = data.optLong("uid", data.optLong("user_id", 0)),
            name = data.optString("username").ifBlank { data.optString("name") },
            handle = data.optString("handle"),
            avatar = data.optString("avatar_url").ifBlank { data.optString("avatar") },
            bio = data.optString("bio"),
        )
    }

    suspend fun myDesigns(offset: Int = 0, limit: Int = 24): MakerWorldPage = withContext(Dispatchers.IO) {
        val token = requireToken()
        val response = getMaker("design-service/my/design/published?limit=$limit&offset=$offset", token)
        parsePage(response, token)
    }

    suspend fun myFavorites(offset: Int = 0, limit: Int = 24): MakerWorldPage = withContext(Dispatchers.IO) {
        val token = requireToken()
        val response = getMaker("design-service/my/favorites/v2/like?limit=$limit&offset=$offset", token)
        parsePage(response, token)
    }

    suspend fun myCollections(offset: Int = 0, limit: Int = 24): MakerWorldCollections =
        withContext(Dispatchers.IO) {
            val token = requireToken()
            val response = getMaker("design-service/my/favorites/listlite?limit=$limit&offset=$offset", token)
            if (response.code == 401) expired()
            if (response.code !in 200..299) {
                throw MakerWorldException(errorText(response.body, response.code), response.code)
            }
            val root = runCatching { JSONObject(response.body) }.getOrDefault(JSONObject())
            val data = root.optJSONObject("data")
            val array = root.optJSONArray("hits")
                ?: root.optJSONArray("list")
                ?: root.optJSONArray("collections")
                ?: data?.optJSONArray("hits")
                ?: data?.optJSONArray("list")
                ?: data?.optJSONArray("collections")
                ?: JSONArray()
            val items = ArrayList<MakerWorldCollection>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optLong("id", obj.optLong("collectionId", 0))
                val name = obj.optString("name").ifBlank { obj.optString("title") }
                if (id == 0L && name.isBlank()) continue
                items.add(
                    MakerWorldCollection(
                        id = id,
                        name = name.ifBlank { "Коллекция" },
                        cover = obj.optString("cover")
                            .ifBlank { obj.optString("coverUrl") }
                            .ifBlank { obj.optString("thumbnail") },
                        count = obj.optInt("designCount", obj.optInt("count", obj.optInt("total", 0))),
                    ),
                )
            }
            MakerWorldCollections(items, root.optInt("total", data?.optInt("total", items.size) ?: items.size))
        }

    private fun parsePage(response: HttpResult, token: String): MakerWorldPage {
        if (response.code == 401) expired()
        if (response.code !in 200..299) {
            throw MakerWorldException(errorText(response.body, response.code), response.code)
        }
        val root = runCatching { JSONObject(response.body) }.getOrDefault(JSONObject())
        val data = root.optJSONObject("data")
        val array = root.optJSONArray("hits")
            ?: root.optJSONArray("list")
            ?: root.optJSONArray("designs")
            ?: data?.optJSONArray("hits")
            ?: data?.optJSONArray("list")
            ?: data?.optJSONArray("designs")
            ?: root.optJSONArray("data")
            ?: JSONArray()
        val models = ArrayList<MakerWorldModel>()
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { obj -> modelFrom(obj)?.let(models::add) }
        }
        return MakerWorldPage(models, root.optInt("total", data?.optInt("total", models.size) ?: models.size))
    }

    // ------------------------------------------------------------------ search

    suspend fun search(
        query: String,
        sort: String = "downloadCount",
        offset: Int = 0,
        limit: Int = 24,
    ): MakerWorldPage = withContext(Dispatchers.IO) {
        val response = get(
            "$api/search-service/select/design2?keyword=${enc(query)}&limit=$limit&offset=$offset&orderBy=$sort",
            null,
        )
        if (response.code !in 200..299) {
            throw MakerWorldException(errorText(response.body, response.code), response.code)
        }
        val root = runCatching { JSONObject(response.body) }.getOrDefault(JSONObject())
        if (root.optBoolean("keywordBlock")) {
            throw MakerWorldException(root.optString("blockedMessage").ifBlank { "Поиск заблокирован" })
        }
        val hits = root.optJSONArray("hits") ?: JSONArray()
        val models = ArrayList<MakerWorldModel>()
        for (i in 0 until hits.length()) {
            hits.optJSONObject(i)?.let { obj ->
                if (!obj.optBoolean("nsfw")) modelFrom(obj)?.let(models::add)
            }
        }
        MakerWorldPage(models, root.optInt("total", models.size))
    }

    // ------------------------------------------------------------------ detail

    suspend fun detail(designId: Long): MakerWorldDetail = withContext(Dispatchers.IO) {
        val response = get("$api/design-service/design/$designId", null)
        if (response.code !in 200..299) {
            throw MakerWorldException(errorText(response.body, response.code), response.code)
        }
        val root = runCatching { JSONObject(response.body) }
            .getOrElse { throw MakerWorldException("MakerWorld вернул неожиданный ответ") }
        val model = modelFrom(root) ?: throw MakerWorldException("Модель не найдена")
        val instances = ArrayList<MakerWorldInstance>()
        val array = root.optJSONArray("instances") ?: JSONArray()
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { obj -> instanceFrom(obj)?.let(instances::add) }
        }
        val tags = ArrayList<String>()
        root.optJSONArray("tags")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let(tags::add)
            }
        }
        val description = Jsoup.parse(root.optString("summary")).text().trim()
        MakerWorldDetail(
            model = model,
            description = description,
            tags = tags,
            instances = instances,
            defaultInstanceId = root.optLong("defaultInstanceId", 0),
        )
    }

    // ---------------------------------------------------------------- download

    suspend fun download(instanceId: Long, onProgress: (Float) -> Unit = {}): MakerWorldDownload =
        withContext(Dispatchers.IO) {
            val token = requireToken()
            val response = get("$api/design-service/instance/$instanceId/f3mf?type=download", token)
            if (response.code == 401) expired()
            if (response.code !in 200..299) {
                throw MakerWorldException(errorText(response.body, response.code), response.code)
            }
            val manifest = runCatching { JSONObject(response.body) }.getOrDefault(JSONObject())
            val url = manifest.optString("url")
            if (url.isBlank()) throw MakerWorldException("MakerWorld не выдал ссылку на 3MF")
            val rawName = manifest.optString("name")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "*/*")
                .build()
            cdn.newCall(request).execute().use { fileResponse ->
                if (!fileResponse.isSuccessful) {
                    throw MakerWorldException("CDN MakerWorld: код ${fileResponse.code}", fileResponse.code)
                }
                val body = fileResponse.body ?: throw MakerWorldException("CDN вернул пустой файл")
                val length = body.contentLength()
                val dir = File(context.cacheDir, "makerworld").apply { mkdirs() }
                val name = safeName(rawName.ifBlank { "model_$instanceId.3mf" })
                val target = File(dir, name)
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var sent = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            sent += read
                            if (sent > MAX_FILE) throw MakerWorldException("3MF больше 200 МБ")
                            if (length > 0) onProgress((sent.toFloat() / length).coerceIn(0f, 1f))
                        }
                    }
                }
                MakerWorldDownload(target, name)
            }
        }

    // ------------------------------------------------------------------ utils

    private fun modelFrom(raw: JSONObject): MakerWorldModel? {
        val obj = raw.optJSONObject("design") ?: raw
        val id = obj.optLong("id", obj.optLong("designId", 0))
        val title = obj.optString("title").ifBlank { obj.optString("name") }
        if (id <= 0 || title.isBlank()) return null
        val creator = obj.optJSONObject("designCreator")
            ?: obj.optJSONObject("instanceCreator")
            ?: obj.optJSONObject("creator")
        return MakerWorldModel(
            id = id,
            title = title,
            slug = obj.optString("slug"),
            cover = obj.optString("cover")
                .ifBlank { obj.optString("coverUrl") }
                .ifBlank { obj.optString("coverLandscape") },
            author = creator?.optString("name").orEmpty()
                .ifBlank { creator?.optString("handle").orEmpty() },
            authorAvatar = creator?.optString("avatar").orEmpty(),
            likes = obj.optInt("likeCount"),
            downloads = obj.optInt("downloadCount"),
            prints = obj.optInt("printCount"),
            license = obj.optString("license"),
            createTime = obj.optString("createTime"),
        )
    }

    private fun instanceFrom(obj: JSONObject): MakerWorldInstance? {
        val id = obj.optLong("id", 0)
        val profileId = obj.optLong("profileId", 0)
        if (id <= 0 && profileId <= 0) return null
        val modelInfo = obj.optJSONObject("extention")?.optJSONObject("modelInfo")
        val compatibility = modelInfo?.optJSONObject("compatibility")
        val files = ArrayList<MakerWorldFile>()
        obj.optJSONArray("instanceFilaments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                files.add(
                    MakerWorldFile(
                        type = f.optString("type"),
                        color = f.optString("color"),
                        grams = f.optString("usedG").toFloatOrNull() ?: 0f,
                    ),
                )
            }
        }
        val plates = ArrayList<MakerWorldPlate>()
        modelInfo?.optJSONArray("plates")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val thumb = p.optJSONObject("thumbnail")?.optString("url").orEmpty()
                    .ifBlank { p.optJSONObject("top_picture")?.optString("url").orEmpty() }
                val plateFiles = ArrayList<MakerWorldFile>()
                p.optJSONArray("filaments")?.let { fs ->
                    for (j in 0 until fs.length()) {
                        val f = fs.optJSONObject(j) ?: continue
                        plateFiles.add(
                            MakerWorldFile(
                                type = f.optString("type"),
                                color = f.optString("color"),
                                grams = f.optString("usedG").toFloatOrNull() ?: 0f,
                            ),
                        )
                    }
                }
                plates.add(
                    MakerWorldPlate(
                        index = p.optInt("index", i + 1),
                        name = p.optString("name"),
                        thumbnail = thumb,
                        seconds = p.optInt("prediction", 0),
                        grams = p.optInt("weight", 0),
                        files = plateFiles,
                    ),
                )
            }
        }
        return MakerWorldInstance(
            id = id,
            profileId = profileId,
            title = obj.optString("title").ifBlank { "Профиль печати" },
            cover = obj.optString("cover").ifBlank { plates.firstOrNull()?.thumbnail.orEmpty() },
            needAms = obj.optBoolean("needAms"),
            seconds = obj.optInt("prediction", 0),
            grams = obj.optInt("weight", 0),
            printer = compatibility?.optString("devProductName").orEmpty(),
            files = files,
            plates = plates,
        )
    }

    private fun requireToken(): String {
        val session = _session.value ?: throw MakerWorldException("Войдите в аккаунт Bambu Lab", 401)
        if (session.expiresAt > 0 && System.currentTimeMillis() > session.expiresAt) {
            expired()
            throw MakerWorldException("Сессия истекла. Войдите снова.", 401)
        }
        return session.accessToken
    }

    private fun expired() {
        tokenStore.clear()
        _session.value = null
    }

    /** Мои списки: сначала официальный API-хост, при 404 — web-хост MakerWorld. */
    private fun getMaker(path: String, token: String): HttpResult {
        val response = get("$api/$path", token)
        if (response.code == 404) {
            return get("https://makerworld.com/api/v1/$path", token)
        }
        return response
    }

    private fun get(url: String, token: String?): HttpResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "ru,en;q=0.8")
            .header("Referer", "https://makerworld.com/ru")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()
        http.newCall(request).execute().use { response ->
            return HttpResult(response.code, response.body?.string().orEmpty())
        }
    }

    private fun post(url: String, body: JSONObject): HttpResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            return HttpResult(response.code, response.body?.string().orEmpty())
        }
    }

    private fun errorText(raw: String, code: Int): String {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val upstream = json?.optString("error").orEmpty()
            .ifBlank { json?.optString("message").orEmpty() }
        return when {
            code == 401 -> "Сессия истекла. Войдите снова."
            code == 403 -> upstream.ifBlank { "MakerWorld закрыл доступ к этой модели" }
            code == 418 -> "Bambu просит подтвердить, что вы не робот. Попробуйте позже."
            code == 429 -> "Слишком много запросов. Подождите немного."
            upstream.isNotBlank() -> upstream
            code == 400 -> "Неверный email, пароль или код"
            else -> "Bambu Cloud: код $code"
        }
    }

    private fun loginError(error: Throwable): MakerWorldLogin.Error {
        val message = when (error) {
            is MakerWorldException -> error.message.orEmpty()
            else -> error.message ?: "Не удалось войти"
        }
        return MakerWorldLogin.Error(message, error is MakerWorldException && error.status == 418)
    }

    private fun safeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_")
            .trim()
        val withExt = if (base.endsWith(".3mf", true)) base else "$base.3mf"
        return withExt.ifBlank { "makerworld.3mf" }.take(120)
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private data class HttpResult(val code: Int, val body: String)

    private companion object {
        const val UA = "BambukHome/1.0.4 (Android)"
        const val MAX_FILE = 200L * 1024 * 1024
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** Шифрует токен ключом Android Keystore; в prefs лежит только Base64(iv+cipher). */
private class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("makerworld_session", Context.MODE_PRIVATE)

    fun save(session: MakerWorldSession) {
        runCatching {
            val json = JSONObject()
                .put("access", session.accessToken)
                .put("refresh", session.refreshToken)
                .put("email", session.email)
                .put("expires", session.expiresAt)
                .toString()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
            val blob = cipher.iv + encrypted
            prefs.edit().putString("blob", Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        }
    }

    fun load(): MakerWorldSession? = runCatching {
        val raw = prefs.getString("blob", null) ?: return null
        val blob = Base64.decode(raw, Base64.NO_WRAP)
        if (blob.size < 13) return null
        val iv = blob.copyOfRange(0, 12)
        val data = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(data), Charsets.UTF_8))
        val token = json.optString("access")
        if (token.isBlank()) return null
        MakerWorldSession(
            accessToken = token,
            refreshToken = json.optString("refresh"),
            email = json.optString("email"),
            expiresAt = json.optLong("expires", 0),
        )
    }.getOrNull()

    fun clear() {
        prefs.edit().remove("blob").apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "bambukhome_makerworld"
    }
}
