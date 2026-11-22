package ru.layerpilot.data.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.sin

/**
 * Генерирует маленький MJPEG-AVI для демо-режима: можно проверить плеер
 * и посмотреть, как выглядит таймлапс, без реального принтера.
 */
object DemoTimelapse {

    private const val WIDTH = 640
    private const val HEIGHT = 360
    private const val FPS = 24
    private const val FRAMES = 120

    fun ensure(context: Context, remoteName: String): File {
        val safe = remoteName.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").take(60)
            .ifBlank { "demo" }
        val dir = File(context.cacheDir, "timelapse").apply { mkdirs() }
        val target = File(dir, "demo_$safe")
        if (target.exists() && target.length() > 10_000) return target
        val frames = ArrayList<ByteArray>(FRAMES)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until FRAMES) {
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawFrame(canvas, paint, i)
            val out = ByteArrayOutputStream(48 * 1024)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            bitmap.recycle()
            frames += out.toByteArray()
        }
        writeAvi(target, frames)
        return target
    }

    private fun drawFrame(canvas: Canvas, paint: Paint, frame: Int) {
        val progress = frame / (FRAMES - 1f)
        paint.shader = LinearGradient(
            0f, 0f, 0f, HEIGHT.toFloat(),
            Color.rgb(6, 12, 8), Color.rgb(22, 42, 26), Shader.TileMode.CLAMP,
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(36, 58, 40)
        canvas.drawRect(30f, 22f, WIDTH - 30f, HEIGHT - 22f, paint)

        // стол
        val bed = Path().apply {
            moveTo(90f, 300f); lineTo(550f, 300f); lineTo(470f, 235f); lineTo(170f, 235f); close()
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(26, 46, 30)
        canvas.drawPath(bed, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.rgb(58, 92, 64)
        canvas.drawPath(bed, paint)
        for (i in 1..7) {
            val t = i / 8f
            canvas.drawLine(170f + (470f - 170f) * t, 235f, 90f + (550f - 90f) * t, 300f, paint)
        }

        // растущая ваза
        val grow = 0.18f + progress * 0.62f
        val topY = 300f - 190f * grow
        val width = 34f + 20f * grow
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            320f - width, 0f, 320f + width, 0f,
            Color.rgb(160, 240, 170), Color.rgb(48, 118, 62), Shader.TileMode.CLAMP,
        )
        val vase = Path().apply {
            moveTo(320f - width, 298f)
            cubicTo(320f - width - 8f, 250f, 320f - width + 6f, topY + 30f, 320f - width * 0.7f, topY)
            lineTo(320f + width * 0.7f, topY)
            cubicTo(320f + width - 6f, topY + 30f, 320f + width + 8f, 250f, 320f + width, 298f)
            close()
        }
        canvas.drawPath(vase, paint)
        paint.shader = null

        // блик и «слой»
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(120, 220, 255, 220)
        canvas.drawLine(320f - width * 0.5f, topY + 20f, 320f - width * 0.2f, 290f, paint)
        paint.color = Color.argb(90, 140, 230, 150)
        val layerY = 298f - 190f * grow * 0.55f
        canvas.drawLine(320f - width + 6f, layerY, 320f + width - 6f, layerY, paint)

        // подпись
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(200, 235, 205)
        paint.textSize = 17f
        canvas.drawText("BambukHome · демо-таймлапс", 34f, 46f, paint)
        paint.textSize = 34f
        paint.color = Color.rgb(150, 230, 160)
        canvas.drawText("${(progress * 100).toInt()}%", 34f, 80f, paint)
        paint.textSize = 13f
        paint.color = Color.rgb(120, 165, 130)
        canvas.drawText("слой ${(progress * 235).toInt() + 1} / 235 · кадр ${frame + 1}/$FRAMES",
            34f, HEIGHT - 32f, paint)
    }

    private fun writeAvi(target: File, frames: List<ByteArray>) {
        val moviData = frames.sumOf { 8 + it.size + (it.size and 1) }
        val maxFrame = frames.maxOfOrNull { it.size } ?: 0
        val moviSize = 4 + moviData
        val hdrlSize = 4 + 8 + 56 + 8 + 4 + 8 + 56 + 8 + 40
        val riffSize = 4 + (8 + hdrlSize) + (8 + moviSize)

        target.outputStream().buffered(256 * 1024).use { out ->
            val d = DataOutputStream(out)
            fun four(s: String) = d.write(s.toByteArray(Charsets.US_ASCII))
            fun u32(v: Int) {
                d.write(v and 0xFF); d.write((v ushr 8) and 0xFF)
                d.write((v ushr 16) and 0xFF); d.write((v ushr 24) and 0xFF)
            }
            fun u16(v: Int) { d.write(v and 0xFF); d.write((v ushr 8) and 0xFF) }

            four("RIFF"); u32(riffSize); four("AVI ")

            four("LIST"); u32(hdrlSize); four("hdrl")
            four("avih"); u32(56)
            u32(1_000_000 / FPS)          // micros per frame
            u32(0)                         // max bytes/sec
            u32(0)                         // padding
            u32(0x10)                      // AVIF_HASINDEX не ставим, но флаг безвреден
            u32(frames.size)               // total frames
            u32(0)                         // initial frames
            u32(1)                         // streams
            u32(maxFrame)                  // suggested buffer
            u32(WIDTH); u32(HEIGHT)
            u32(0); u32(0); u32(0); u32(0)

            four("LIST"); u32(4 + 8 + 56 + 8 + 40); four("strl")
            four("strh"); u32(56)
            four("vids"); four("MJPG")
            u32(0); u16(0); u16(0)
            u32(0); u32(1); u32(FPS); u32(0); u32(frames.size)
            u32(maxFrame); u32(-1); u32(0)
            u16(0); u16(0); u16(WIDTH); u16(HEIGHT)

            four("strf"); u32(40)
            u32(40); u32(WIDTH); u32(HEIGHT)
            u16(1); u16(24); four("MJPG"); u32(maxFrame)
            u32(0); u32(0); u32(0); u32(0)

            four("LIST"); u32(moviSize); four("movi")
            frames.forEach { frame ->
                four("00dc"); u32(frame.size); d.write(frame)
                if (frame.size and 1 == 1) d.write(0)
            }
            d.flush()
        }
    }
}
