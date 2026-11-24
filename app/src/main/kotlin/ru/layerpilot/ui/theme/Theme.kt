package ru.layerpilot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import ru.layerpilot.data.settings.AppSettings

private val Green = Color(0xFF8FE39A)
private val OnGreen = Color(0xFF003910)
private val GreenContainer = Color(0xFF1B4D26)
private val OnGreenContainer = Color(0xFFB6F5BE)
private val Secondary = Color(0xFFB3CCB5)
private val OnSecondary = Color(0xFF1E3423)
private val SecondaryContainer = Color(0xFF344B38)
private val OnSecondaryContainer = Color(0xFFCFE8CF)
private val Tertiary = Color(0xFF9ED8DE)
private val OnTertiary = Color(0xFF00363B)
private val TertiaryContainer = Color(0xFF1B4C51)
private val OnTertiaryContainer = Color(0xFFBAECF2)
private val Error = Color(0xFFFFB4AB)
private val OnError = Color(0xFF690005)
private val ErrorContainer = Color(0xFF93000A)
private val OnErrorContainer = Color(0xFFFFDAD6)
private val Ink = Color(0xFFE4EAE3)
private val InkMuted = Color(0xFFB8C4B9)
private val Outline = Color(0xFF839585)
private val OutlineVariant = Color(0xFF3A4A3C)

val BrandDarkScheme = darkColorScheme(
    primary = Green,
    onPrimary = OnGreen,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Color(0xFF0C110D),
    onBackground = Ink,
    surface = Color(0xFF0C110D),
    onSurface = Ink,
    surfaceVariant = Color(0xFF2B382D),
    onSurfaceVariant = InkMuted,
    outline = Outline,
    outlineVariant = OutlineVariant,
    surfaceContainerLowest = Color(0xFF080C09),
    surfaceContainerLow = Color(0xFF121813),
    surfaceContainer = Color(0xFF161D18),
    surfaceContainerHigh = Color(0xFF1C241E),
    surfaceContainerHighest = Color(0xFF232C25),
    inverseSurface = Ink,
    inverseOnSurface = Color(0xFF25302A),
    inversePrimary = Color(0xFF1B6B32),
)

val BrandLightScheme = lightColorScheme(
    primary = Color(0xFF1B6B32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2B2),
    onPrimaryContainer = Color(0xFF002108),
    secondary = Color(0xFF4F6353),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E8D4),
    onSecondaryContainer = Color(0xFF0D1F12),
    tertiary = Color(0xFF39656B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCECE8),
    onTertiaryContainer = Color(0xFF001F22),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF4),
    onBackground = Color(0xFF171D18),
    surface = Color(0xFFF6FBF4),
    onSurface = Color(0xFF171D18),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5EE),
    surfaceContainer = Color(0xFFEAEFE8),
    surfaceContainerHigh = Color(0xFFE4EAE2),
    surfaceContainerHighest = Color(0xFFDFE4DC),
)

/** Чуть больше насыщенности и света для акцентных цветов. */
private fun vivid(color: Color, saturation: Float, lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[1] = (hsl[1] + saturation).coerceIn(0f, 1f)
    hsl[2] = (hsl[2] + lightness).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

fun ColorScheme.withVividAccent(): ColorScheme = copy(
    primary = vivid(primary, 0.06f, 0.025f),
    primaryContainer = vivid(primaryContainer, 0.05f, 0.02f),
    secondary = vivid(secondary, 0.05f, 0.02f),
    secondaryContainer = vivid(secondaryContainer, 0.05f, 0.015f),
    tertiary = vivid(tertiary, 0.06f, 0.02f),
    tertiaryContainer = vivid(tertiaryContainer, 0.05f, 0.02f),
)

/** Чёрные поверхности для AMOLED: экономят батарею и дают «бесконечный» контраст. */
fun ColorScheme.withAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050705),
    surfaceContainer = Color(0xFF080B08),
    surfaceContainerHigh = Color(0xFF0C100C),
    surfaceContainerHighest = Color(0xFF111511),
    surfaceVariant = Color(0xFF1B231C),
)

private fun hue(h: Float, shift: Float = 0f): Float = ((h + shift) % 360f + 360f) % 360f

private fun tint(h: Float, s: Float, l: Float): Color =
    Color.hsl(hue(h), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/**
 * Генерирует цельную схему из одного оттенка: 12 тональных уровней,
 * контейнеры и контрастный текст. Так пользователь получает любой цвет,
 * а не только те, что есть в системе.
 */
fun schemeFromHue(h: Float, dark: Boolean): ColorScheme {
    val secondaryHue = hue(h, 22f)
    val tertiaryHue = hue(h, 68f)
    return if (dark) {
        darkColorScheme(
            primary = tint(h, 0.58f, 0.73f),
            onPrimary = tint(h, 0.9f, 0.09f),
            primaryContainer = tint(h, 0.4f, 0.26f),
            onPrimaryContainer = tint(h, 0.72f, 0.92f),
            secondary = tint(secondaryHue, 0.24f, 0.74f),
            onSecondary = tint(secondaryHue, 0.24f, 0.13f),
            secondaryContainer = tint(secondaryHue, 0.2f, 0.25f),
            onSecondaryContainer = tint(secondaryHue, 0.32f, 0.9f),
            tertiary = tint(tertiaryHue, 0.42f, 0.75f),
            onTertiary = tint(tertiaryHue, 0.8f, 0.11f),
            tertiaryContainer = tint(tertiaryHue, 0.3f, 0.25f),
            onTertiaryContainer = tint(tertiaryHue, 0.66f, 0.92f),
            error = Error,
            onError = OnError,
            errorContainer = ErrorContainer,
            onErrorContainer = OnErrorContainer,
            background = tint(h, 0.2f, 0.045f),
            onBackground = tint(h, 0.1f, 0.92f),
            surface = tint(h, 0.2f, 0.045f),
            onSurface = tint(h, 0.1f, 0.92f),
            surfaceVariant = tint(h, 0.15f, 0.23f),
            onSurfaceVariant = tint(h, 0.11f, 0.78f),
            outline = tint(h, 0.1f, 0.56f),
            outlineVariant = tint(h, 0.12f, 0.3f),
            surfaceContainerLowest = tint(h, 0.2f, 0.02f),
            surfaceContainerLow = tint(h, 0.16f, 0.06f),
            surfaceContainer = tint(h, 0.14f, 0.085f),
            surfaceContainerHigh = tint(h, 0.13f, 0.12f),
            surfaceContainerHighest = tint(h, 0.13f, 0.16f),
            inverseSurface = tint(h, 0.1f, 0.92f),
            inverseOnSurface = tint(h, 0.2f, 0.1f),
            inversePrimary = tint(h, 0.55f, 0.42f),
        )
    } else {
        lightColorScheme(
            primary = tint(h, 0.62f, 0.36f),
            onPrimary = Color.White,
            primaryContainer = tint(h, 0.75f, 0.88f),
            onPrimaryContainer = tint(h, 0.82f, 0.16f),
            secondary = tint(secondaryHue, 0.24f, 0.38f),
            onSecondary = Color.White,
            secondaryContainer = tint(secondaryHue, 0.35f, 0.9f),
            onSecondaryContainer = tint(secondaryHue, 0.5f, 0.15f),
            tertiary = tint(tertiaryHue, 0.42f, 0.36f),
            onTertiary = Color.White,
            tertiaryContainer = tint(tertiaryHue, 0.5f, 0.86f),
            onTertiaryContainer = tint(tertiaryHue, 0.62f, 0.15f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            background = tint(h, 0.3f, 0.985f),
            onBackground = tint(h, 0.2f, 0.1f),
            surface = tint(h, 0.3f, 0.985f),
            onSurface = tint(h, 0.2f, 0.1f),
            surfaceVariant = tint(h, 0.2f, 0.9f),
            onSurfaceVariant = tint(h, 0.11f, 0.28f),
            outline = tint(h, 0.1f, 0.45f),
            outlineVariant = tint(h, 0.14f, 0.78f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tint(h, 0.3f, 0.97f),
            surfaceContainer = tint(h, 0.28f, 0.945f),
            surfaceContainerHigh = tint(h, 0.27f, 0.92f),
            surfaceContainerHighest = tint(h, 0.24f, 0.89f),
        )
    }
}

@Composable
fun BambukHomeTheme(
    settings: AppSettings,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dark = when (settings.themeMode.name) {
        "LIGHT" -> false
        "DARK" -> true
        else -> darkTheme
    }
    var scheme = when {
        settings.accentMode == "SYSTEM" && supportsDynamic && dark -> dynamicDarkColorScheme(context)
        settings.accentMode == "SYSTEM" && supportsDynamic -> dynamicLightColorScheme(context)
        settings.accentMode == "CUSTOM" -> schemeFromHue(settings.accentHue.toFloat(), dark)
        dark -> BrandDarkScheme
        else -> BrandLightScheme
    }
    if (dark && settings.amoled) scheme = scheme.withAmoled()
    scheme = scheme.withVividAccent()

    val family = when (settings.fontMode) {
        "NDOT" -> NdotFamily
        "CUSTOM" -> customFontFamily(settings.customFontPath)
        else -> null
    }
    val baseDensity = LocalDensity.current
    val scale = settings.uiScale.coerceIn(0.8f, 1.35f)

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density * scale,
            fontScale = baseDensity.fontScale * scale,
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = appTypography(family),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(30.dp),
            ),
            content = content,
        )
    }
}
