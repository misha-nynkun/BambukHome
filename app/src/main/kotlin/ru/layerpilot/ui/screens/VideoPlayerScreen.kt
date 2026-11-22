package ru.layerpilot.ui.screens

import kotlinx.coroutines.launch
import ru.layerpilot.data.files.Downloads
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.Download
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import ru.layerpilot.ui.locale.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.layerpilot.data.files.RemoteFile
import ru.layerpilot.data.video.AviMjpeg
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.WavyProgress
import java.io.File

/**
 * Плеер таймлапсов: AVI/MJPG играем своим декодером, MP4/MKV — через Media3.
 */
@Composable
fun VideoPlayerScreen(vm: AppViewModel, remote: RemoteFile, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var file by remember(remote.path) { mutableStateOf<File?>(null) }
    var loading by remember(remote.path) { mutableStateOf(true) }
    var progress by remember(remote.path) { mutableFloatStateOf(0f) }
    var error by remember(remote.path) { mutableStateOf<String?>(null) }
    var avi by remember(remote.path) { mutableStateOf<AviMjpeg?>(null) }
    var showDownload by remember(remote.path) { mutableStateOf(false) }
    var saving by remember(remote.path) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(remote.path) {
        loading = true
        error = null
        vm.prepareVideo(remote) { progress = it }
            .onSuccess { local ->
                file = local
                avi = if (remote.name.endsWith(".avi", true) || true) AviMjpeg.open(local) else null
                if (avi == null && !remote.name.endsWith(".mp4", true) &&
                    !remote.name.endsWith(".mkv", true) && !remote.name.endsWith(".mov", true)
                ) {
                    error = "Не удалось прочитать AVI: возможно, файл ещё пишется или повреждён"
                }
            }
            .onFailure { error = it.message ?: "Не удалось скачать видео" }
        loading = false
    }

    val openedPlayer = avi
    DisposableEffect(openedPlayer) {
        onDispose { openedPlayer?.close() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    remote.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(if (remote.isVideoAvi) "AVI · MJPG" else "видео")
                        append(" · нажмите, чтобы управлять")
                    },
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            IconButton(onClick = { showDownload = true }, enabled = file != null && !saving) {
                Icon(Icons.Rounded.Download, "Скачать", tint = Color.White)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Готовим таймлапс…", color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.width(220.dp)) {
                        WavyProgress(progress = progress.coerceAtLeast(0.02f), height = 10.dp, animated = true)
                    }
                    if (progress > 0f) {
                        Spacer(Modifier.height(6.dp))
                        Text("${(progress * 100).toInt()} %",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
                error != null -> Column(
                    Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Видео не открылось", color = Color.White,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "", color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        onClick = onClose,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.12f),
                    ) {
                        Text("Закрыть", color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                }
                avi != null -> AviPlayback(avi!!)
                file != null -> ExoVideo(file!!, Modifier.fillMaxSize())
            }
        }

        if (avi == null) {
            Text(
                "Если формат не открылся, скачайте файл и посмотрите в галерее — там системный плеер.",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    if (showDownload && file != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) showDownload = false },
            icon = { Icon(Icons.Rounded.Download, null) },
            title = { Text("Скачать таймлапс?") },
            text = {
                Column {
                    Text(remote.name, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Файл будет сохранён в «Загрузки/BambukHome».",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Загрузки/BambukHome/${remote.name}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(remote.size / 1024 / 1024).coerceAtLeast(1)} МБ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (saving) {
                        Spacer(Modifier.height(10.dp))
                        Text("Сохраняем…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val local = file
                            val saved = local != null && withContext(Dispatchers.IO) {
                                Downloads.save(context, local, remote.name)
                            }
                            saving = false
                            showDownload = false
                            if (!saved) error = "Не удалось сохранить файл"
                        }
                    },
                ) { Text("Скачать") }
            },
            dismissButton = {
                TextButton(onClick = { showDownload = false }, enabled = !saving) { Text("Отмена") }
            },
        )
    }
}

private val RemoteFile.isVideoAvi: Boolean get() = name.endsWith(".avi", true)

@Composable
private fun AviPlayback(player: AviMjpeg) {
    var playing by remember { mutableStateOf(true) }
    var frame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var position by remember { mutableIntStateOf(0) }
    var seekTick by remember { mutableIntStateOf(0) }
    var drag by remember { mutableFloatStateOf(-1f) }

    val frameMs = (1000f / player.fps.coerceAtLeast(1f))

    LaunchedEffect(playing, seekTick, player) {
        if (player.frameCount == 0) return@LaunchedEffect
        if (!playing) {
            frame = withContext(Dispatchers.Default) { player.decode(position.coerceIn(0, player.frameCount - 1)) }
            return@LaunchedEffect
        }
        if (position >= player.frameCount - 1) position = 0
        while (position < player.frameCount) {
            val started = SystemClock.elapsedRealtime()
            val index = position
            frame = withContext(Dispatchers.Default) { player.decode(index) }
            if (position == index) position = index + 1
            if (position >= player.frameCount) {
                playing = false
                break
            }
            val elapsed = SystemClock.elapsedRealtime() - started
            val wait = frameMs.toLong() - elapsed
            if (wait > 0) delay(wait)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            frame?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Кадр таймлапса",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!playing) {
                Surface(
                    onClick = { playing = true },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, "Смотреть", Modifier.size(38.dp), tint = Color.White)
                    }
                }
            }
        }

        Surface(color = Color(0xFF07100A), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                val shownFraction = if (drag >= 0f) drag
                else (position.toFloat() / player.frameCount.coerceAtLeast(1)).coerceIn(0f, 1f)
                Slider(
                    value = shownFraction,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        val target = (drag * player.frameCount).toInt().coerceIn(0, player.frameCount - 1)
                        position = target
                        seekTick++
                        drag = -1f
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF8FE39A),
                        activeTrackColor = Color(0xFF8FE39A),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { playing = !playing }) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Пауза" else "Смотреть",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = {
                        position = 0
                        seekTick++
                        playing = true
                    }) {
                        Icon(Icons.Rounded.Replay, "Сначала", tint = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${timeText(position / player.fps)} / ${timeText(player.frameCount / player.fps)}",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${player.frameCount} кадров · ${player.fps.toInt()} fps",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ExoVideo(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            }
        },
        modifier = modifier,
    )
}

private fun timeText(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
