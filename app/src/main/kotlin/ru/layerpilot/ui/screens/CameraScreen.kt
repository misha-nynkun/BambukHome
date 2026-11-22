package ru.layerpilot.ui.screens

import ru.layerpilot.ui.components.ExpressiveLoadingIndicator
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import ru.layerpilot.ui.locale.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.ui.AppViewModel

@Composable
fun CameraScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.snapshot.collectAsStateWithLifecycle()
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }

    val available = settings.demoMode || settings.model.hasCamera

    LaunchedEffect(Unit) {
        if (settings.cameraAutoLight && state.lightOn == false) vm.setLight(true)
    }

    LaunchedEffect(settings.host, settings.accessCode, settings.demoMode) {
        if (!available) return@LaunchedEffect
        error = null
        vm.cameraFrames()
            .catch { error = it.message ?: "поток недоступен" }
            .collectLatest { frame ->
                jpeg = frame
                busy = false
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
                    IconButton(onClick = { fullscreen = true }) {
                        Icon(Icons.Rounded.Fullscreen, "Во весь экран",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Box(
                Modifier
                    .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(bitmap, contentDescription = "Камера принтера",
                        modifier = Modifier.fillMaxSize(), contentScale = if (fullscreen) ContentScale.Fit else ContentScale.Crop)
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
                            onClick = { vm.setLight(state.lightOn != true) },
                        )
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
                            onClick = { vm.setLight(state.lightOn != true) },
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
