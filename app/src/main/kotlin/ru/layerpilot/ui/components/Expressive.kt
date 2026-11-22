@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.layerpilot.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.delay
import ru.layerpilot.ui.shapes.ExpressiveShapes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Загрузка: материальная «морфящаяся» фигура (Material 3 Expressive loading indicator).
// ---------------------------------------------------------------------------------------------

private val loadingMorphs: List<Morph> by lazy {
    val sequence = ExpressiveShapes.LoadingSequence
    sequence.indices.map { index ->
        Morph(sequence[index], sequence[(index + 1) % sequence.size])
    }
}

private val determinateMorph: Morph by lazy {
    Morph(ExpressiveShapes.Circle, ExpressiveShapes.SoftBurst)
}

/**
 * Индикатор загрузки Material 3 Expressive: плавно перетекает между фирменными фигурами
 * (soft burst → cookie → pentagon → pill → sunny → cookie → oval) и медленно вращается.
 *
 * @param contained true — фигура в цветном круге 48 dp, как в официальных примерах;
 * false — только фигура, без подложки (для мест, где нужен компактный спиннер).
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contained: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorColor: Color = if (contained) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.primary,
    animated: Boolean = LocalWaveStyle.current.animated,
) {
    val morphProgress = remember { Animatable(0f) }
    var morphIndex by remember { mutableIntStateOf(0) }
    var stepRotation by remember { mutableFloatStateOf(90f) }
    val rotation = rememberInfiniteTransition(label = "loading-rotation")
    val globalRotation by rotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(4666, easing = LinearEasing),
        ),
        label = "loading-global-rotation",
    )
    val rotationDegrees = if (animated) globalRotation else 0f
    LaunchedEffect(animated) {
        if (!animated) {
            morphProgress.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = 0.05f,
                ),
            )
            morphIndex = (morphIndex + 1) % loadingMorphs.size
            morphProgress.snapTo(0f)
            stepRotation = (stepRotation + 90f) % 360f
            delay(120)
        }
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val diameter = this.size.minDimension
            if (contained) {
                drawCircle(color = containerColor, radius = diameter / 2f)
            }
            val inner = diameter * if (contained) 0.74f else 0.92f
            val path = Path()
            loadingMorphs[morphIndex].morphTo(path, morphProgress.value)
            val matrix = Matrix().apply { scale(inner, inner) }
            path.transform(matrix)
            val bounds = path.getBounds()
            path.translate(
                Offset(
                    diameter / 2f - bounds.center.x,
                    diameter / 2f - bounds.center.y,
                )
            )
            rotate(
                degrees = stepRotation + morphProgress.value * 90f + rotationDegrees,
                pivot = Offset(diameter / 2f, diameter / 2f),
            ) {
                drawPath(path = path, color = indicatorColor)
            }
        }
    }
}

/** Детерминированный вариант: фигура «растёт» вместе с прогрессом. */
@Composable
fun ExpressiveLoadingIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contained: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorColor: Color = if (contained) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.primary,
) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "morph")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val diameter = this.size.minDimension
            if (contained) drawCircle(color = containerColor, radius = diameter / 2f)
            val inner = diameter * if (contained) 0.74f else 0.92f
            val path = Path()
            determinateMorph.morphTo(path, shown)
            path.transform(Matrix().apply { scale(inner, inner) })
            val bounds = path.getBounds()
            path.translate(Offset(diameter / 2f - bounds.center.x, diameter / 2f - bounds.center.y))
            rotate(degrees = -180f * shown, pivot = Offset(diameter / 2f, diameter / 2f)) {
                drawPath(path = path, color = indicatorColor)
            }
        }
    }
}

/** Компактный спиннер для кнопок и строк состояния. */
@Composable
fun ExpressiveSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    ExpressiveLoadingIndicator(
        modifier = modifier,
        size = size,
        contained = false,
        indicatorColor = color,
    )
}

private fun Morph.morphTo(destination: Path, progress: Float) {
    destination.rewind()
    var first = true
    forEachCubic(progress.coerceIn(0f, 1f)) { cubic ->
        if (first) {
            destination.moveTo(cubic.anchor0X, cubic.anchor0Y)
            first = false
        }
        destination.cubicTo(
            cubic.control0X, cubic.control0Y,
            cubic.control1X, cubic.control1Y,
            cubic.anchor1X, cubic.anchor1Y,
        )
    }
    if (!first) destination.close()
}

// ---------------------------------------------------------------------------------------------
// Линейный прогресс: тонкий трек с точкой на конце + волнистая активная часть.
// ---------------------------------------------------------------------------------------------

/**
 * Линейный индикатор Material 3 Expressive.
 *
 * Высота контейнера 10 dp, активная линия 4 dp, волна 3 dp, длина волны 40 dp —
 * как в спецификации M3. Волна включается только между 10 % и 95 %: в начале и в конце
 * линия остаётся прямой, чтобы не «дёргалась» на краях.
 */
@Composable
fun ExpressiveLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    animated: Boolean = true,
    thickness: Float = 1f,
    amplitude: Float = 1f,
    showStop: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, tween(600, easing = LinearEasing), label = "linear")
    val wavePhase by rememberInfiniteTransition(label = "linear-wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "linear-phase",
    )
    val targetAmplitude = if (shown <= 0.1f || shown >= 0.95f) 0f else amplitude.coerceIn(0f, 1.8f)
    val amp by animateFloatAsState(targetAmplitude, tween(500), label = "linear-amp")
    val phase = if (animated) wavePhase else 0f

    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val activeStroke = min(4.dp.toPx() * thickness, h * 0.55f).coerceAtLeast(1f)
        val trackStroke = min(4.dp.toPx() * thickness, h * 0.55f).coerceAtLeast(1f) * 0.8f
        val wavelength = 40.dp.toPx()
        val capInset = activeStroke / 2f
        val gap = 4.dp.toPx()

        val progressPx = shown * w
        val activeEnd = (progressPx - capInset).coerceIn(0f, w - capInset)
        val activeStart = capInset
        val hasActive = progressPx > 0.5f

        // Трек с точкой на конце.
        if (showStop && shown < 0.995f) {
            val stopSize = min(4.dp.toPx() * thickness, trackStroke)
            val stopX = w - stopSize / 2f
            val trackFrom = if (hasActive) {
                (activeEnd + capInset + gap).coerceAtLeast(activeStart)
            } else {
                activeStart
            }
            if (trackFrom < stopX - stopSize) {
                drawLine(
                    color = trackColor,
                    start = Offset(trackFrom, cy),
                    end = Offset(stopX - stopSize / 2f - 1f, cy),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(
                color = color.copy(alpha = 0.72f),
                radius = stopSize / 2f,
                center = Offset(stopX, cy),
            )
        } else if (shown < 0.005f) {
            drawLine(
                color = trackColor,
                start = Offset(activeStart, cy),
                end = Offset(w - capInset, cy),
                strokeWidth = trackStroke,
                cap = StrokeCap.Round,
            )
        }

        if (!hasActive) return@Canvas

        // Активная часть. Совсем маленький прогресс — аккуратная точка.
        if (activeEnd - activeStart <= activeStroke * 0.75f) {
            drawCircle(
                color = color,
                radius = activeStroke / 2f,
                center = Offset(activeStart, cy),
            )
            return@Canvas
        }

        val amplitudePx = ((h - activeStroke) / 2f) * amp
        val path = Path()
        val phasePx = phase * wavelength
        var x = activeStart
        var first = true
        while (x <= activeEnd) {
            val y = if (amplitudePx > 0.01f) {
                cy + sin((x - phasePx) / wavelength * 2f * PI.toFloat()) * amplitudePx
            } else {
                cy
            }
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            x += 1.4f
        }
        if (amplitudePx <= 0.01f) path.lineTo(activeEnd, cy)
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = activeStroke, cap = StrokeCap.Round),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Круговой прогресс: тонкое кольцо + волнистая активная дуга.
// ---------------------------------------------------------------------------------------------

/**
 * Круговой индикатор Material 3 Expressive: активная дуга «дышит» волной, трек — тонкое
 * кольцо с разрывом у концов дуги. Подходит и для маленьких спиннеров, и для больших датчиков.
 */
@Composable
fun ExpressiveCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    animated: Boolean = true,
    amplitudeScale: Float = 1f,
    startAngle: Float = -90f,
    wavy: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, tween(700, easing = LinearEasing), label = "circle")
    val phase by rememberInfiniteTransition(label = "circle-wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "circle-phase",
    )
    val targetAmplitude = if (!wavy || shown <= 0.08f || shown >= 0.97f) 0f
    else amplitudeScale.coerceIn(0f, 1.8f)
    val amp by animateFloatAsState(targetAmplitude, tween(500), label = "circle-amp")
    val wavePhase = if (animated) phase else 0f

    Canvas(modifier.size(size)) {
        val d = this.size.minDimension
        val stroke = strokeWidth.toPx().coerceAtMost(d * 0.18f)
        val radius = d / 2f - stroke / 2f - 0.5f
        val center = Offset(d / 2f, d / 2f)
        val wavelength = (15.dp.toPx() * (d / 48.dp.toPx()).coerceIn(1f, 1.7f))
            .coerceAtLeast(8.dp.toPx())
        val gapDeg = (4.dp.toPx() / (2f * PI.toFloat() * radius)) * 360f
        val sweep = shown * 360f
        val amplitudePx = 1.6.dp.toPx() * amp

        // Трек.
        if (shown < 0.995f) {
            val trackSweep = 360f - sweep - gapDeg * 2f
            if (trackSweep > 1f) {
                drawArc(
                    color = trackColor,
                    startAngle = startAngle + sweep + gapDeg,
                    sweepAngle = trackSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = stroke * 0.85f, cap = StrokeCap.Round),
                )
            }
        }

        if (sweep < 0.5f) {
            // Точка старта.
            if (shown > 0f) {
                val a = Math.toRadians(startAngle.toDouble())
                drawCircle(
                    color = color,
                    radius = stroke / 2f,
                    center = Offset(
                        center.x + radius * cos(a).toFloat(),
                        center.y + radius * sin(a).toFloat(),
                    ),
                )
            }
            return@Canvas
        }

        // Волна по дуге. Длину волны подгоняем так, чтобы на дуге укладывалось целое число
        // периодов, а у концов плавно гасим амплитуду — так дуга «вырастает» из кольца
        // и возвращается в него, как в Material 3 Expressive.
        val arcLength = (sweep / 360f) * 2f * PI.toFloat() * radius
        val waves = if (sweep < 34f) 1 else max(1, Math.round(arcLength / wavelength))
        val phaseRad = wavePhase * 2f * PI.toFloat()
        val steps = max(180, (sweep * 2f).toInt())
        val path = Path()
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angleDeg = startAngle + sweep * t
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
            // У самых концов дуги гасим волну, чтобы круглая шапка легла ровно на кольцо.
            val edge = 0.20f
            val envRaw = when {
                t < edge -> t / edge
                t > 1f - edge -> (1f - t) / edge
                else -> 1f
            }.coerceIn(0f, 1f)
            val env = envRaw * envRaw * (3f - 2f * envRaw)
            val mod = if (amplitudePx > 0.01f) {
                sin(t * waves * 2f * PI.toFloat() - phaseRad) * amplitudePx * env
            } else {
                0f
            }
            val r = radius + mod
            val px = center.x + r * cos(angleRad)
            val py = center.y + r * sin(angleRad)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
