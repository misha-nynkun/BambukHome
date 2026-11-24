package ru.layerpilot.data.files

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Кладёт скачанный файл в «Загрузки/BambukHome». */
object Downloads {

    fun save(context: Context, file: File, displayName: String = file.name): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return saveLegacy(context, file, displayName)
        }
        val mime = mimeFor(displayName)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BambukHome")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // Не плодим «файл (1)», «файл (2)» при повторном скачивании.
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(values.getAsString(MediaStore.Downloads.RELATIVE_PATH), displayName),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    resolver.delete(
                        ContentUris.withAppendedId(collection, cursor.getLong(0)),
                        null,
                        null,
                    )
                }
            }
        }
        val uri = resolver.insert(collection, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out, 128 * 1024) }
            } ?: return false
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            true
        }.getOrDefault(false)
    }

    /**
     * Android 8–9 не умеет MediaStore.Downloads. Кладём файл в
     * app-specific папку — без разрешений на общую память.
     */
    private fun saveLegacy(context: Context, file: File, displayName: String): Boolean {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val dir = File(base, "BambukHome").apply { mkdirs() }
        return runCatching {
            file.copyTo(File(dir, displayName), overwrite = true)
            true
        }.getOrDefault(false)
    }

    private fun mimeFor(name: String): String = when {
        name.endsWith(".3mf", true) -> "model/3mf"
        name.endsWith(".gcode", true) -> "text/x-gcode"
        name.endsWith(".avi", true) -> "video/x-msvideo"
        name.endsWith(".mp4", true) -> "video/mp4"
        else -> "application/octet-stream"
    }
}
