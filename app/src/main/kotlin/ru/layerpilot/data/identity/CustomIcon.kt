package ru.layerpilot.data.identity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ru.layerpilot.MainActivity
import java.io.File

/**
 * Пользовательская иконка. Android не позволяет подменить иконку уже
 * установленного приложения без переустановки, поэтому сохраняем фото
 * и закрепляем его как отдельный ярлык на рабочем столе.
 */
object CustomIconStore {
    private fun file(context: Context): File = File(context.filesDir, "custom_icon.png")

    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val square = cropSquare(bitmap)
        file(context).outputStream().use { out ->
            square.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun load(context: Context): Bitmap? = runCatching {
        if (file(context).exists()) BitmapFactory.decodeFile(file(context).absolutePath) else null
    }.getOrNull()

    fun pin(context: Context, label: String): Boolean = runCatching {
        val bitmap = load(context) ?: return false
        val shortcut = ShortcutInfoCompat.Builder(context, "bambuk_custom_icon")
            .setShortLabel(label.take(24))
            .setLongLabel(label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(bitmap))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                },
            )
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        true
    }.getOrDefault(false)

    private fun cropSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, x, y, side, side)
    }
}
