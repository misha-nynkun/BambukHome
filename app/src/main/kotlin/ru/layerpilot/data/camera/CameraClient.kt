package ru.layerpilot.data.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import ru.layerpilot.data.printer.MqttPrinterClient
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLSocket

/**
 * Камера P1/X1: TLS-сокет на порт 6000, 80-байтный пакет авторизации,
 * дальше бесконечный поток кадров «16 байт заголовка + JPEG».
 */
class CameraClient {

    fun frames(host: String, accessCode: String, port: Int = 6000): Flow<ByteArray> = callbackFlow {
        val socketRef = AtomicReference<Socket?>(null)
        val context = MqttPrinterClient.trustAllContext()
        val worker = Thread({
            var ssl: SSLSocket? = null
            try {
                val raw = Socket()
                raw.soTimeout = 15_000
                raw.connect(InetSocketAddress(host, port), 10_000)
                socketRef.set(raw)
                ssl = context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
                ssl.soTimeout = 15_000
                ssl.startHandshake()
                socketRef.set(ssl)
                ssl.outputStream.write(authPacket(accessCode))
                ssl.outputStream.flush()
                val input = ssl.inputStream
                while (!isClosedForSend) {
                    val header = readExact(input, 16) ?: break
                    val size = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    if (size <= 4 || size > 20_000_000) break
                    val jpeg = readExact(input, size) ?: break
                    if (jpeg.size >= 4 &&
                        jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte() &&
                        jpeg[jpeg.size - 2] == 0xFF.toByte() && jpeg[jpeg.size - 1] == 0xD9.toByte()
                    ) {
                        if (trySend(jpeg).isFailure) break
                    }
                }
                close()
            } catch (e: Exception) {
                close(e)
            } finally {
                runCatching { ssl?.close() }
                runCatching { socketRef.get()?.close() }
            }
        }, "layerpilot-camera").apply { isDaemon = true }

        worker.start()
        awaitClose {
            runCatching { socketRef.get()?.close() }
            worker.interrupt()
        }
    }.flowOn(Dispatchers.IO).buffer(1)

    suspend fun snapshot(host: String, accessCode: String, timeoutMs: Long = 20_000): ByteArray =
        withTimeout(timeoutMs) { frames(host, accessCode).first() }

    private fun readExact(input: InputStream, size: Int): ByteArray? {
        val data = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(data, offset, size - offset)
            if (read < 0) return null
            offset += read
        }
        return data
    }

    private fun authPacket(accessCode: String): ByteArray {
        val buffer = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x40)
        buffer.putInt(0x3000)
        buffer.putInt(0)
        buffer.putInt(0)
        val user = "bblp".toByteArray(Charsets.US_ASCII)
        buffer.put(user)
        repeat(32 - user.size) { buffer.put(0) }
        val code = accessCode.toByteArray(Charsets.US_ASCII).take(31).toByteArray()
        buffer.put(code)
        repeat(32 - code.size) { buffer.put(0) }
        return buffer.array()
    }

    companion object {
        fun saveToGallery(context: Context, jpeg: ByteArray): Boolean {
            val name = "BambukHome_${System.currentTimeMillis()}.jpg"
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: context.filesDir
                val dir = java.io.File(base, "BambukHome").apply { mkdirs() }
                return runCatching {
                    java.io.File(dir, name).writeBytes(jpeg)
                    true
                }.getOrDefault(false)
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/BambukHome")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            return runCatching {
                resolver.openOutputStream(uri)?.use { it.write(jpeg) } ?: return false
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            }.getOrDefault(false)
        }
    }
}
