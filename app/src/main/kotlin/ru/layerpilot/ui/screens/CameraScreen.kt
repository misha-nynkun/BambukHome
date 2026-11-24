package ru.layerpilot.ui.screens

import ru.layerpilot.ui.components.ExpressiveLoadingIndicator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import ru.layerpilot.ui.locale.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.snapshot.collectAsStateWithLifecycle()
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var controlMode by remember { mutableStateOf(false) }
    var spaghetti by remember { mutableStateOf(false) }
    val analyzer = remember { FrameAnalyzer() }
    val phaseNow by rememberUpdatedState(state.phase)
    val stateNow by rememberUpdatedState(state)
    val lifecycleOwner = LocalLifecycleOwner.current
    var lightManaged by remember { mutableStateOf(false) }
    val lightManagedNow by rememberUpdatedState(lightManaged)
    var lightChecked by remember { mutableStateOf(false) }

    val available = settings.demoMode || settings.model.hasCamera

    // Умный свет: включаем только если пользователь сам оставил его выключенным.
    LaunchedEffect(settings.cameraAutoLight, state.lightOn, state.phase) {
        if (lightChecked) return@LaunchedEffect
        val light = state.lightOn ?: return@LaunchedEffect
        lightChecked = true
        val active = state.phase == PrintPhase.PRINTING || state.phase == PrintPhase.PAUSED
        if (settings.cameraAutoLight && !light && active) {
            lightManaged = true
            vm.setLight(true)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (lightManagedNow) vm.setLight(false)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (lightManagedNow) vm.setLight(false)
                Lifecycle.Event.ON_START -> {
                    if (lightManagedNow && stateNow.lightOn == false) vm.setLight(true)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun applyZoom(next: Float) {
        val value = next.coerceIn(1f, 4f)
        zoom = value
        if (value <= 1.01f) {
            pan = Offset.Zero
        } else {
            val maxX = viewport.width * (value - 1f) / 2f
            val maxY = viewport.height * (value - 1f) / 2f
            pan = Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
        }
    }
    fun toggleCameraLight() {
        val next = state.lightOn != true
        if (lightManaged) lightManaged = false
        vm.setLight(next)
    }

    LaunchedEffect(settings.host, settings.accessCode, settings.demoMode, settings.spaghettiDetection) {
        if (!available) return@LaunchedEffect
        error = null
        vm.cameraFrames()
            .catch { error = it.message ?: "поток недоступен" }
            .collect { frame ->
                jpeg = frame
                busy = false
                if (settings.spaghettiDetection &&
                    phaseNow in listOf(PrintPhase.PRINTING, PrintPhase.PAUSED)
                ) {
                    val alert = withContext(Dispatchers.Default) { analyzer.push(frame) }
                    if (alert) spaghetti = true
                }
            }
    }

    val bitmap = remember(jpeg) {
        jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    Box(Modifier.fillMaxSize().background(if (fullscreen) Color.Black else MaterialTheme.colorScheme.background)) {
        if (!available) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.VideocamOff, null, Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("У этой модели нет камеры", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    "Камера есть у P1S, P1P, X1 и H2D. В демо-режиме поток имитируется.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Surface(onClick = onBack, shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text("Назад", Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
            return@Box
        }

        Column(Modifier.fillMaxSize()) {
            if (!fullscreen) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Назад", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Камера", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text(
                            if (settings.demoMode) "демо-поток" else "${settings.printerName} · 6000/TLS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { controlMode = true }) {
                        Icon(Icons.Rounded.Tune, "Управление столом",
                            tint = if (controlMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { fullscreen = true }) {
                        Icon(Icons.Rounded.Fullscreen, "Во весь экран",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Box(
                Modifier
                    .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    .background(Color.Black)
                    .clipToBounds()
                    .onSizeChanged { viewport = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, gestureZoom, _ ->
                            applyZoom(zoom * gestureZoom)
                            if (zoom > 1.01f) {
                                val maxX = viewport.width * (zoom - 1f) / 2f
                                val maxY = viewport.height * (zoom - 1f) / 2f
                                pan = Offset(
                                    (pan.x + panChange.x).coerceIn(-maxX, maxX),
                                    (pan.y + panChange.y).coerceIn(-maxY, maxY),
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(bitmap, contentDescription = "Камера принтера",
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        },
                        contentScale = if (fullscreen) ContentScale.Fit else ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (error != null) "Поток не открылся" else "Подключаемся к камере…",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        ExpressiveLoadingIndicator(size = 52.dp, contained = false, indicatorColor = Color(0xFF8FE39A))
                        error?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }

                if (spaghetti) {
                    Surface(
                        Modifier.align(Alignment.TopCenter).padding(12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.WarningAmber, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "Возможные сопли в зоне печати. Проверьте камеру.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            TextButton(onClick = { spaghetti = false; analyzer.reset() }) {
                                Text("Скрыть", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                Row(
                    Modifier.align(Alignment.BottomCenter).padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { applyZoom(zoom - 0.25f) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Rounded.Remove, "Уменьшить", Modifier.size(16.dp), tint = Color.White)
                    }
                    Text(
                        String.format(java.util.Locale.US, "%.1f×", zoom),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = { applyZoom(zoom + 0.25f) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Rounded.Add, "Приблизить", Modifier.size(16.dp), tint = Color.White)
                    }
                }

                if (!fullscreen) {
                    Row(
                        Modifier.align(Alignment.TopStart).padding(14.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF6E66)))
                        Text("LIVE", color = Color.White, fontSize = 11.sp, lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }

                if (fullscreen) {
                    Row(
                        Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CameraAction(
                            icon = if (state.lightOn == true) Icons.Rounded.Lightbulb else Icons.Outlined.Lightbulb,
                            label = if (state.lightOn == true) "Свет" else "Темно",
                            onClick = { toggleCameraLight() },
                        )
                        Spacer(Modifier.width(8.dp))
                        CameraAction(Icons.Rounded.Tune, "Стол", onClick = { controlMode = true })
                        Spacer(Modifier.width(8.dp))
                        CameraAction(Icons.Rounded.CameraAlt, "Снимок", onClick = { jpeg?.let(vm::saveSnapshot) })
                        Spacer(Modifier.width(8.dp))
                        CameraAction(Icons.Rounded.FullscreenExit, "Свернуть", onClick = { fullscreen = false })
                    }
                }
            }

            if (!fullscreen) {
                Column(
                    Modifier.weight(1f).padding(horizontal = 18.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CameraAction(
                            icon = if (state.lightOn == true) Icons.Rounded.Lightbulb else Icons.Outlined.Lightbulb,
                            label = if (state.lightOn == true) "Свет вкл" else "Свет выкл",
                            onClick = { toggleCameraLight() },
                            modifier = Modifier.weight(1f),
                        )
                        CameraAction(
                            icon = Icons.Rounded.CameraAlt,
                            label = "Снимок",
                            enabled = jpeg != null,
                            onClick = { jpeg?.let(vm::saveSnapshot) },
                            modifier = Modifier.weight(1f),
                        )
                        CameraAction(
                            icon = Icons.Rounded.Fullscreen,
                            label = "Весь экран",
                            onClick = { fullscreen = true },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (state.phase) {
                                        PrintPhase.PRINTING -> "Печать · ${state.progress}%"
                                        PrintPhase.PAUSED -> "Пауза · ${state.progress}%"
                                        PrintPhase.PREPARE -> "Подготовка"
                                        PrintPhase.FINISHED -> "Готово"
                                        else -> "Принтер свободен"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (state.layer > 0) "слой ${state.layer} · ${state.remainingLabel}"
                                    else "кадры идут напрямую с принтера",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.totalLayers > 0) {
                                Text(
                                    "слой ${state.layer}/${state.totalLayers}",
                                    fontSize = 12.sp, lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Text(
                        "Камера P1S отдаёт кадры по TLS на порту 6000. В темноте включите свет камеры — иначе видно только контур.",
                        fontSize = 11.sp, lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (controlMode) {
        ModalBottomSheet(
            onDismissRequest = { controlMode = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp).padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Управление столом и головой",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    "Только перемещение и парковка. Температуры и вентиляторы — в разделе «Управление».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Movement(
                    state = state,
                    busy = busy,
                    onMove = vm::move,
                    onHome = { vm.home() },
                    onExtrude = vm::extrude,
                    showExtrude = false,
                )
            }
        }
    }
}

@Composable
private fun CameraAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, label, Modifier.size(22.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(5.dp))
            Text(label, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Простой локальный детектор: резкая устойчивая смена кадра в зоне стола. */
private class FrameAnalyzer {
    private var last: ByteArray? = null
    private var lastAt = 0L
    private var strikes = 0

    fun push(jpeg: ByteArray): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAt < 2_500) return false
        lastAt = now
        val options = BitmapFactory.Options().apply { inSampleSize = 16 }
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) ?: return false
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) { bitmap.recycle(); return false }
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()
        val gray = ByteArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            gray[i] = (((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3).toByte()
        }
        val previous = last
        last = gray
        if (previous == null || previous.size != gray.size) return false
        var changed = 0
        var sum = 0
        for (i in gray.indices) {
            val d = kotlin.math.abs((gray[i].toInt() and 0xFF) - (previous[i].toInt() and 0xFF))
            if (d > 35) changed++
            sum += d
        }
        val ratio = changed.toFloat() / gray.size
        val average = sum.toFloat() / gray.size
        strikes = if (ratio > 0.32f && average > 22f) strikes + 1 else 0
        return strikes >= 3
    }

    fun reset() {
        last = null
        strikes = 0
        lastAt = 0L
    }
}
