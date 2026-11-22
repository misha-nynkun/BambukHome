package ru.layerpilot.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import ru.layerpilot.ui.locale.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.layerpilot.ui.theme.NdotFamily

/**
 * Полоса прогресса Material 3 Expressive: тонкий трек с точкой на конце и волнистая
 * активная часть. Толщина и амплитуда берутся из настроек оформления.
 */
@Composable
fun WavyProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    animated: Boolean = false,
) {
    val style = LocalWaveStyle.current
    ExpressiveLinearProgress(
        progress = progress,
        modifier = modifier,
        height = height,
        color = color,
        trackColor = trackColor,
        animated = animated && style.animated,
        thickness = style.thickness,
        amplitude = style.amplitude,
    )
}

/**
 * Круговой датчик Material 3 Expressive: тонкое кольцо-трек и волнистая активная дуга,
 * внутри — проценты и подпись.
 */
@Composable
fun WaveGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    animated: Boolean = true,
    caption: String = "",
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val style = LocalWaveStyle.current
    val target = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, tween(800), label = "gauge")
    val stroke = (size.value / 30f).dp.coerceIn(3.4.dp, 5.5.dp)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        ExpressiveCircularProgress(
            progress = target,
            modifier = Modifier.size(size),
            strokeWidth = stroke,
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            animated = animated && style.animated,
            amplitudeScale = style.amplitude,
            wavy = false,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = (shown * 100).toInt().toString(),
                fontFamily = NdotFamily,
                fontSize = (size.value * 0.30f).sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = "%",
                fontSize = (size.value * 0.11f).sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    fontSize = (size.value * 0.085f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
