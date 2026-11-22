package ru.layerpilot.data.files

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Кладёт скачанный файл в общую папку «Загрузки/BambukHome», без разрешений. */
object Downloads {

    fun save(context: Context, file: File, displayName: String = file.name): Boolean {
        val mime = when {
            displayName.endsWith(".3mf", true) -> "model/3mf"
            displayName.endsWith(".gcode", true) -> "text/x-gcode"
            displayName.endsWith(".avi", true) -> "video/x-msvideo"
            displayName.endsWith(".mp4", true) -> "video/mp4"
            else -> "application/octet-stream"
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BambukHome")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out, 128 * 1024) }
            } ?: return false
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            true
        }.getOrDefault(false)
    }
}
