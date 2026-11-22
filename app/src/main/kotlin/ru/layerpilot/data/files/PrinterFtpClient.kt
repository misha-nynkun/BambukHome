package ru.layerpilot.data.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class RemoteFile(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
) {
    val isPrintable: Boolean
        get() = !isDir && (name.endsWith(".3mf", true) || name.endsWith(".gcode", true))
}

class FtpException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Обёртка над [FtpsClient]: пути, прогресс и человеческие ошибки.
 * Каждая операция открывает своё соединение — так же ведёт себя Bambu Studio.
 */
class PrinterFtpClient(
    private val host: String,
    private val accessCode: String,
    private val port: Int = 990,
) {

    suspend fun list(path: String): List<RemoteFile> = withFtp { ftp ->
        val dir = normalize(path)
        ftp.list(dir)
            .map { entry ->
                RemoteFile(
                    name = entry.name,
                    path = join(dir, entry.name),
                    isDir = entry.isDir,
                    size = entry.size,
                    modified = entry.modified,
                )
            }
            .sortedWith(compareByDescending<RemoteFile> { it.isDir }.thenByDescending { it.modified })
    }

    suspend fun upload(
        folder: String,
        name: String,
        input: InputStream,
        total: Long = -1,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): String = withFtp { ftp ->
        val dir = normalize(folder)
        val safeName = name.replace('/', '_').replace('\\', '_')
        val path = join(dir, safeName)
        ftp.store(path, input) { sent, _ -> onProgress(sent, total) }
        path
    }

    suspend fun download(
        path: String,
        target: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): File = withFtp { ftp ->
        target.parentFile?.mkdirs()
        target.outputStream().use { out -> ftp.retrieve(normalize(path), out, onProgress) }
        target
    }

    suspend fun readBytes(path: String, limit: Int): ByteArray = withFtp { ftp ->
        ftp.retrievePrefix(normalize(path), limit)
    }

    suspend fun delete(path: String): Boolean = withFtp { ftp ->
        ftp.delete(normalize(path))
    }

    suspend fun mkdir(path: String): Boolean = withFtp { ftp ->
        val dir = normalize(path)
        if (dir == "/") true else ftp.mkdir(dir)
    }

    private suspend fun <T> withFtp(block: (FtpsClient) -> T): T = withContext(Dispatchers.IO) {
        val ftp = FtpsClient(host, port)
        try {
            ftp.connectAndLogin("bblp", accessCode)
            block(ftp)
        } catch (e: FtpException) {
            throw e
        } catch (e: Exception) {
            throw FtpException(friendly(e), e)
        } finally {
            runCatching { ftp.close() }
        }
    }

    private fun friendly(e: Exception): String = when {
        e.message?.contains("Connection refused", true) == true ->
            "порт $port закрыт — включите LAN-режим на принтере"
        e.message?.contains("timeout", true) == true ->
            "принтер не ответил по FTPS (порт $port) — проверьте Wi-Fi и IP"
        e.message?.contains("session", true) == true ->
            "TLS-сессия не принята принтером"
        e is javax.net.ssl.SSLException ->
            "TLS-соединение не установилось (порт 990)"
        e.message?.contains("530", true) == true ->
            "неверный код доступа"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun normalize(path: String): String {
        val p = path.trim()
        if (p.isEmpty() || p == "/") return "/"
        return "/" + p.trim('/')
    }

    private fun join(dir: String, name: String): String =
        if (dir == "/") "/$name" else "$dir/$name"
}
