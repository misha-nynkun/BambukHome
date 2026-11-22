package ru.layerpilot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.layerpilot.data.models.ThreeMfScene
import ru.layerpilot.data.models.ThreeMfSceneReader
import ru.layerpilot.ui.components.ExpressiveSpinner
import ru.layerpilot.ui.components.Model3DView
import ru.layerpilot.ui.locale.Text
import java.io.File

private sealed interface ViewerState {
    data object Loading : ViewerState
    data class Ready(val scene: ThreeMfScene) : ViewerState
    data class Failed(val message: String) : ViewerState
}

/** Полноэкранный 3D-просмотр пластины и модели из 3MF. */
@Composable
fun Model3DViewerScreen(
    file: File,
    bedWidth: Float,
    bedHeight: Float,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var state by remember(file) { mutableStateOf<ViewerState>(ViewerState.Loading) }
    var reset by remember { mutableIntStateOf(0) }

    LaunchedEffect(file, bedWidth, bedHeight) {
        state = ViewerState.Loading
        val result = withContext(Dispatchers.IO) {
            runCatching { ThreeMfSceneReader.load(file, bedWidth, bedHeight) }
        }
        state = result.fold(
            onSuccess = { ViewerState.Ready(it) },
            onFailure = { ViewerState.Failed(it.message ?: "Не удалось прочитать 3MF") },
        )
    }

    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(0.dp)) {
        Surface(Modifier.fillMaxSize(), color = scheme.surfaceContainerLowest) {
            when (val current = state) {
                ViewerState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ExpressiveSpinner(Modifier.size(42.dp), color = scheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Строим 3D-пластину…", color = scheme.onSurfaceVariant)
                    }
                }
                is ViewerState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(44.dp),
                            tint = scheme.error)
                        Spacer(Modifier.height(10.dp))
                        Text("Не удалось открыть 3D", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(current.message, style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { reset++ }, shape = RoundedCornerShape(14.dp)) {
                            Text("Повторить")
                        }
                    }
                }
                is ViewerState.Ready -> {
                    key(reset) {
                        Model3DView(
                            scene = current.scene,
                            modifier = Modifier.fillMaxSize(),
                            background = scheme.surfaceContainerLowest,
                            bedColor = scheme.surfaceContainerHigh,
                            gridColor = scheme.outlineVariant,
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = scheme.surface.copy(alpha = 0.88f),
                tonalElevation = 0.dp,
            ) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.ArrowBack, "Назад", tint = scheme.onSurface)
                    }
                    Icon(Icons.Rounded.ViewInAr, null, Modifier.size(22.dp), tint = scheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.padding(end = 6.dp)) {
                        Text(
                            (state as? ViewerState.Ready)?.scene?.title ?: file.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        (state as? ViewerState.Ready)?.let { ready ->
                            Text(
                                "${ready.scene.triangles} треугольников · ${ready.scene.parts} деталей",
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state is ViewerState.Ready) {
                        IconButton(onClick = { reset++ }) {
                            Icon(Icons.Rounded.Refresh, "Сбросить вид", Modifier.size(20.dp),
                                tint = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.surface.copy(alpha = 0.82f),
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding().padding(16.dp),
        ) {
            Text(
                "Один палец — вращать · два пальца — масштаб",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
