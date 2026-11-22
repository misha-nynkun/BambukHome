package ru.layerpilot.data.files

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

/**
 * Минимальный implicit-FTPS клиент для принтеров Bambu.
 *
 * Зачем не commons-net: принтер требует повторного использования TLS-сессии
 * (session reuse) на data-соединении. Если открыть новый data-сокет с чистой
 * сессией, handshake обрывается. Здесь сессия берётся с control-сокета и
 * подставляется в каждый data-сокет — как в Bambu Studio.
 */
class FtpsClient(
    private val host: String,
    private val port: Int = 990,
    private val timeoutMs: Int = 20_000,
) {
    private var control: SSLSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var controlSession: SSLSession? = null
    private val factory: SSLSocketFactory = insecureContext().socketFactory

    fun connectAndLogin(user: String, password: String) {
        connect()
        val userReply = command("USER $user")
        val passReply = if (userReply.code in 230..232) userReply else command("PASS $password")
        if (passReply.code !in 200..299) {
            throw FtpException("неверный код доступа (FTPS ${passReply.code})")
        }
        // Не все прошивки понимают OPTS; ошибку игнорируем.
        runCatching { command("OPTS UTF8 ON") }
        expectOk(command("PBSZ 0"), "PBSZ")
        expectOk(command("PROT P"), "PROT")
        expectOk(command("TYPE I"), "TYPE I")
    }

    private fun connect() {
        val raw = Socket()
        raw.soTimeout = timeoutMs
        raw.connect(InetSocketAddress(host, port), 10_000)
        val ssl = factory.createSocket(raw, host, port, true) as SSLSocket
        ssl.soTimeout = timeoutMs
        ssl.enabledProtocols = arrayOf("TLSv1.2")
        ssl.startHandshake()
        control = ssl
        controlSession = ssl.session
        input = ssl.inputStream
        output = ssl.outputStream
        val greeting = readReply()
        if (greeting.code !in 200..299 && greeting.code != 120) {
            throw FtpException("FTPS отказал: ${greeting.message}")
        }
    }

    fun list(path: String): List<RawEntry> {
        val data = withDataConnection(if (path == "/") "LIST /" else "LIST $path") { socket ->
            socket.inputStream.readBytes().toString(Charsets.UTF_8)
        }
        return data.lineSequence()
            .mapNotNull(::parseEntry)
            .filter { it.name != "." && it.name != ".." && !it.name.contains('/') }
            .toList()
    }

    fun retrieve(path: String, target: OutputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }): Long {
        val total = sizeOf(path)
        var sent = 0L
        withDataConnection("RETR $path") { socket ->
            val input = socket.inputStream
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                target.write(buffer, 0, read)
                sent += read
                onProgress(sent, total)
            }
        }
        return if (total > 0) total else sent
    }

    fun retrievePrefix(path: String, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(limit, 256 * 1024))
        withDataConnection("RETR $path") { socket ->
            val input = socket.inputStream
            val buffer = ByteArray(32 * 1024)
            while (out.size() < limit) {
                val read = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    fun store(path: String, source: InputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }): Long {
        var sent = 0L
        withDataConnection("STOR $path") { socket ->
            val out = socket.outputStream
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                sent += read
                onProgress(sent, -1)
            }
            out.flush()
        }
        return sent
    }

    fun delete(path: String): Boolean = command("DELE $path").code == 250

    fun mkdir(path: String): Boolean {
        val reply = command("MKD $path")
        return reply.code == 257 || reply.code == 550 // 550 — уже существует
    }

    fun sizeOf(path: String): Long {
        val reply = command("SIZE $path")
        return if (reply.code == 213) reply.message.trim().substringBefore(' ').toLongOrNull() ?: -1 else -1
    }

    fun close() {
        runCatching { command("QUIT") }
        runCatching { control?.close() }
        control = null
        input = null
        output = null
    }

    // ------------------------------------------------------------ внутреннее

    private fun <T> withDataConnection(commandText: String, block: (SSLSocket) -> T): T {
        // TCP подключаем сразу после PASV (сервер держит его в backlog), а TLS-handshake
        // делаем только после ответа 150 — иначе часть серверов ещё не приняла соединение.
        val raw = openPassiveSocket()
        var dataSocket: SSLSocket? = null
        var result: T? = null
        try {
            val reply = command(commandText)
            if (reply.code !in listOf(125, 150)) {
                throw FtpException("${commandText.substringBefore(' ')}: ${reply.message}")
            }
            dataSocket = wrapData(raw)
            result = block(dataSocket)
        } finally {
            runCatching { dataSocket?.close() }
            runCatching { raw.close() }
        }
        val done = runCatching { readReply() }.getOrNull()
        if (done != null && done.code !in 200..299 && done.code != 426) {
            throw FtpException("передача не завершилась: ${done.message}")
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun openPassiveSocket(): Socket {
        val reply = command("PASV")
        if (reply.code != 227) throw FtpException("PASV: ${reply.message}")
        val match = PASV.find(reply.message) ?: throw FtpException("PASV без адреса")
        val groups = match.groupValues
        val dataPort = groups[5].toInt() * 256 + groups[6].toInt()
        val raw = Socket()
        raw.soTimeout = timeoutMs
        raw.connect(InetSocketAddress(host, dataPort), 10_000)
        return raw
    }

    private fun wrapData(raw: Socket): SSLSocket {
        val ssl = factory.createSocket(raw, host, raw.port, true) as SSLSocket
        ssl.soTimeout = timeoutMs
        ssl.enabledProtocols = arrayOf("TLSv1.2")
        controlSession?.let { session -> applySession(ssl, session) }
        ssl.startHandshake()
        return ssl
    }

    /**
     * Kotlin/Android прячут SSLSocket.setSession, но именно он включает
     * повторное использование сессии. Вызываем точечно через reflection.
     */
    private fun applySession(ssl: SSLSocket, session: SSLSession) {
        runCatching {
            ssl.javaClass.methods
                .firstOrNull { it.name == "setSession" && it.parameterTypes.size == 1 }
                ?.invoke(ssl, session)
        }
    }

    private fun command(text: String): Reply {
        val stream = output ?: throw FtpException("FTPS не подключён")
        stream.write((text + "\r\n").toByteArray(Charsets.UTF_8))
        stream.flush()
        return readReply()
    }

    private fun expectOk(reply: Reply, name: String) {
        if (reply.code !in 200..299) throw FtpException("$name: ${reply.message}")
    }

    private fun readReply(): Reply {
        val stream = input ?: throw FtpException("FTPS не подключён")
        val first = readLine(stream) ?: throw FtpException("сервер закрыл соединение")
        if (first.length < 3) throw FtpException("непонятный ответ: $first")
        val code = first.take(3).toIntOrNull() ?: throw FtpException("непонятный ответ: $first")
        val builder = StringBuilder(first.drop(4))
        if (first.length > 3 && first[3] == '-') {
            while (true) {
                val line = readLine(stream) ?: break
                if (line.length >= 4 && line.take(3) == code.toString() && line[3] == ' ') {
                    builder.append('\n').append(line.drop(4))
                    break
                }
                builder.append('\n').append(line)
            }
        }
        return Reply(code, builder.toString().trim())
    }

    private fun readLine(stream: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream(96)
        while (true) {
            val b = stream.read()
            if (b < 0) return if (bytes.size() == 0) null else bytes.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) break
            if (b == '\r'.code) continue
            bytes.write(b)
            if (bytes.size() > 8192) break
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    data class Reply(val code: Int, val message: String)

    data class RawEntry(val name: String, val isDir: Boolean, val size: Long, val modified: Long)

    companion object {
        private val PASV = Regex("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)")
        private val LIST_LINE = Regex(
            "^([-dl])\\S*\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+" +
                "(\\w{3}\\s+\\d+\\s+[\\d:]+)\\s+(.+)$"
        )
        private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec")

        private fun parseEntry(line: String): RawEntry? {
            val m = LIST_LINE.find(line.trim()) ?: return null
            val (kind, size, date, name) = m.destructured
            return RawEntry(
                name = name.trim(),
                isDir = kind == "d",
                size = size.toLongOrNull() ?: 0,
                modified = parseListTime(date),
            )
        }

        private fun parseListTime(date: String): Long {
            return runCatching {
                val parts = date.trim().split(Regex("\\s+"))
                val month = MONTHS.indexOf(parts[0].take(3).lowercase(Locale.US)) + 1
                val day = parts[1].toInt()
                val rest = parts[2]
                val now = java.util.Calendar.getInstance()
                val cal = java.util.Calendar.getInstance()
                if (rest.contains(':')) {
                    val (hh, mm) = rest.split(":").map { it.toInt() }
                    cal.set(now.get(java.util.Calendar.YEAR), month - 1, day, hh, mm, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis - now.timeInMillis > 2L * 24 * 3600 * 1000) {
                        cal.add(java.util.Calendar.YEAR, -1)
                    }
                } else {
                    cal.clear()
                    cal.set(rest.toInt(), month - 1, day, 0, 0, 0)
                }
                cal.timeInMillis
            }.getOrDefault(0L)
        }

        private fun insecureContext(): SSLContext {
            val manager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            return SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(manager), SecureRandom())
            }
        }

        fun formatListingDate(millis: Long): String {
            if (millis <= 0) return "—"
            return SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(java.util.Date(millis))
        }
    }
}
