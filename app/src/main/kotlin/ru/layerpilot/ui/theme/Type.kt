package ru.layerpilot.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import ru.layerpilot.R

/** Ndot 77 JP Extended — личный пиксельный шрифт, лежит в ресурсах. */
val NdotFamily = FontFamily(Font(R.font.ndot77, weight = FontWeight.Normal))

fun customFontFamily(path: String): FontFamily? {
    if (path.isBlank()) return null
    return runCatching {
        val file = File(path)
        if (!file.exists() || file.length() < 1024) null
        else FontFamily(Typeface.createFromFile(file))
    }.getOrNull()
}

/**
 * Подменяет семейство во всех стилях Material 3.
 * null — системный шрифт, иначе Ndot или загруженный пользователем.
 */
fun appTypography(family: FontFamily?): Typography {
    val base = Typography()
    family ?: return base
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}
