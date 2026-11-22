package ru.layerpilot.data.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.RandomAccessFile

/**
 * Читает MJPEG-AVI, которые пишут принтеры Bambu (папка /timelapse).
 * Android не умеет играть такой контейнер системным плеером, поэтому
 * индексируем кадры и декодируем JPEG по мере показа.
 */
class AviMjpeg private constructor(
    private val file: File,
    private val frameOffsets: IntArray,
    private val frameSizes: IntArray,
    val fps: Float,
    val width: Int,
    val height: Int,
) {
    val frameCount: Int get() = frameOffsets.size
    val durationMs: Long get() = (frameCount * 1000f / fps.coerceAtLeast(1f)).toLong()

    private val raf = RandomAccessFile(file, "r")
    private val lock = Any()

    fun decode(index: Int, targetWidth: Int = 1280): Bitmap? {
        if (index !in frameOffsets.indices) return null
        synchronized(lock) {
            return runCatching {
                raf.seek(frameOffsets[index].toLong())
                val bytes = ByteArray(frameSizes[index])
                raf.readFully(bytes)
                if (bytes.size < 4 ||
                    bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()
                ) {
                    return null
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(targetWidth, width)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }.getOrNull()
        }
    }

    fun close() {
        runCatching { raf.close() }
    }

    private fun sampleSize(target: Int, source: Int): Int {
        if (target <= 0 || source <= target) return 1
        var sample = 1
        var w = source
        while (w / 2 >= target) {
            w /= 2
            sample *= 2
        }
        return sample
    }

    companion object {
        fun open(file: File): AviMjpeg? = runCatching {
            val raf = RandomAccessFile(file, "r")
            try {
                if (raf.length() < 64) return null
                if (readFour(raf) != "RIFF") return null
                readLe32(raf)
                if (readFour(raf) != "AVI ") return null

                var microsPerFrame = 0
                var width = 0
                var height = 0
                val offsets = ArrayList<Int>(300)
                val sizes = ArrayList<Int>(300)

                while (raf.filePointer + 8 <= raf.length()) {
                    val id = readFour(raf)
                    val size = readLe32(raf)
                    if (size < 0) break
                    val next = raf.filePointer + size + (size and 1)
                    when {
                        id == "LIST" -> {
                            val type = readFour(raf)
                            val bodyStart = raf.filePointer
                            val bodySize = (size - 4).coerceAtLeast(0)
                            when (type) {
                                "hdrl" -> {
                                    val avih = findChunk(raf, bodyStart, bodySize.toLong(), "avih")
                                    if (avih != null) {
                                        raf.seek(avih)
                                        val header = ByteArray(56)
                                        raf.readFully(header)
                                        microsPerFrame = le32(header, 0)
                                        width = le32(header, 32)
                                        height = le32(header, 36)
                                    }
                                }
                                "movi" -> collectFrames(raf, bodyStart, bodyStart + bodySize.toLong(), offsets, sizes)
                            }
                            raf.seek(next)
                        }
                        id == "avih" -> {
                            val end = raf.filePointer + size
                            val header = ByteArray(minOf(size, 56))
                            raf.readFully(header)
                            microsPerFrame = le32(header, 0)
                            width = le32(header, 32)
                            height = le32(header, 36)
                            raf.seek(end)
                        }
                        else -> raf.seek(next)
                    }
                }
                if (offsets.isEmpty()) return null
                val fps = if (microsPerFrame in 1..1_000_000) 1_000_000f / microsPerFrame else 24f
                AviMjpeg(file, offsets.toIntArray(), sizes.toIntArray(), fps, width, height)
            } finally {
                raf.close()
            }
        }.getOrNull()

        private fun collectFrames(
            raf: RandomAccessFile,
            start: Long,
            end: Long,
            offsets: MutableList<Int>,
            sizes: MutableList<Int>,
        ) {
            raf.seek(start)
            while (raf.filePointer + 8 <= end) {
                val id = readFour(raf)
                val size = readLe32(raf)
                if (size < 0) return
                val dataStart = raf.filePointer
                val next = dataStart + size + (size and 1)
                if (id == "LIST") {
                    val type = readFour(raf)
                    if (type == "rec ") {
                        collectFrames(raf, raf.filePointer, dataStart + size, offsets, sizes)
                    }
                } else if (id.length == 4 && id[2] == 'd' && (id[3] == 'c' || id[3] == 'b') && size > 4) {
                    if (dataStart + size <= end) {
                        offsets += dataStart.toInt()
                        sizes += size
                    }
                }
                raf.seek(next)
            }
        }

        private fun findChunk(raf: RandomAccessFile, start: Long, size: Long, wanted: String): Long? {
            raf.seek(start)
            val end = start + size
            while (raf.filePointer + 8 <= end) {
                val id = readFour(raf)
                val length = readLe32(raf)
                if (length < 0) return null
                if (id == wanted) return raf.filePointer
                raf.seek(raf.filePointer + length + (length and 1))
            }
            return null
        }

        private fun readFour(raf: RandomAccessFile): String {
            val b = ByteArray(4)
            raf.readFully(b)
            return String(b, Charsets.US_ASCII)
        }

        private fun le32(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)

        private fun readLe32(raf: RandomAccessFile): Int {
            val b = ByteArray(4)
            raf.readFully(b)
            return (b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or
                ((b[3].toInt() and 0xFF) shl 24)
        }
    }
}
