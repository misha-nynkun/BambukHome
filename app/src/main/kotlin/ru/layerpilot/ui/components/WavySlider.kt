package ru.layerpilot.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import ru.layerpilot.data.settings.AppSettings
import kotlin.math.PI
import kotlin.math.sin

/**
 * M3-слайдер с волной только на активной (левой) части.
 * Справа — ровная линия трека, как в Bambu Studio / Material 3 Expressive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
    onValueChangeFinished: (() -> Unit)? = null,
    thickness: Float = 1f,
    amplitude: Float = 1f,
    animated: Boolean = true,
) {
    val colors = SliderDefaults.colors(
        thumbColor = accent,
        activeTrackColor = accent,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    val transition = rememberInfiniteTransition(label = "wavy-slider")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "wavy-phase",
    )

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        track = { state ->
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height((10f * thickness).dp),
            ) {
                val w = size.width
                val h = size.height
                val cy = h / 2f
                val stroke = (4f * thickness).dp.toPx()
                val range = state.valueRange
                val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
                val fraction = ((state.value - range.start) / span).coerceIn(0f, 1f)
                val filled = (w * fraction).coerceAtLeast(1f)
                val waveAmp = ((h - stroke) / 2f).coerceAtLeast(0f) * amplitude
                val wavelength = (40f * thickness).dp.toPx()
                val shift = if (animated) phase else 0f

                // неактивная часть — ровная линия
                drawLine(
                    color = colors.inactiveTrackColor,
                    start = Offset(filled, cy),
                    end = Offset(w, cy),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )

                if (fraction > 0.001f) {
                    val path = Path()
                    var x = 0f
                    while (x <= filled) {
                        val y = cy + sin(x / wavelength * 2f * PI.toFloat() + shift) * waveAmp
                        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                        x += 1.5f
                    }
                    if (waveAmp <= 0.01f) path.lineTo(filled, cy)
                    drawPath(
                        path = path,
                        color = accent,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke,
                            cap = StrokeCap.Round,
                        ),
                    )
                }
            }
        },
    )
}

fun AppSettings.waveParams(): Pair<Float, Float> =
    waveThickness.coerceIn(0.6f, 1.6f) to waveAmplitude.coerceIn(0f, 1.8f)
