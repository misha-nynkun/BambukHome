package ru.layerpilot.ui.screens

import ru.layerpilot.ui.components.ExpressiveLoadingIndicator
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import ru.layerpilot.ui.locale.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.layerpilot.data.files.RemoteFile
import ru.layerpilot.data.printer.PrintOptions
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.EmptyState
import ru.layerpilot.ui.components.ProgressOverlay
import ru.layerpilot.ui.components.ScreenHeader
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onPlayVideo: (RemoteFile) -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.snapshot.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var path by remember { mutableStateOf("/") }
    var files by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<RemoteFile?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRemote by remember { mutableStateOf<RemoteFile?>(null) }
    var deleteTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingUri = uri
    }

    LaunchedEffect(path, reloadKey, settings.host, settings.demoMode, settings.ftpPort) {
        loading = true
        error = null
        vm.listFiles(path)
            .onSuccess { files = it }
            .onFailure { error = it.message ?: "Не удалось прочитать папку" }
        loading = false
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        ScreenHeader(
            title = "Файлы",
            subtitle = if (settings.demoMode) "демо · ${path}" else path,
            onBack = {
                if (path == "/" || path.isBlank()) onBack()
                else path = path.substringBeforeLast('/').ifBlank { "/" }
            },
            onRefresh = { reloadKey++ },
        )

        Column(Modifier.padding(horizontal = 18.dp)) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(
                            if (path == "/") "Карта принтера" else path.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                loading -> "читаем…"
                                files.isEmpty() -> "пусто"
                                else -> "${files.size} объект(ов)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (path != "/") {
                        IconButton(onClick = { path = path.substringBeforeLast('/').ifBlank { "/" } }) {
                            Icon(Icons.Rounded.ArrowBackIosNew, "Вверх",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                    FilledTonalButton(
                        onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Загрузить")
                    }
                }
            }

            transfer?.let {
                Spacer(Modifier.height(12.dp))
                ProgressOverlay(it)
            }

            Spacer(Modifier.height(12.dp))

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
                error != null -> Box(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("SD-карта недоступна", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(error ?: "", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { reloadKey++ }) { Text("Повторить") }
                        }
                    }
                }
                files.isEmpty() -> EmptyState(
                    Icons.Rounded.InsertDriveFile,
                    "Папка пуста",
                    "Загрузите готовый 3MF с телефона — принтер сразу увидит его в списке.",
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(files, key = { it.path }) { file ->
                        FileRow(file, onClick = {
                            when {
                                file.isDir -> path = file.path
                                file.isVideo -> onPlayVideo(file)
                                else -> selected = file
                            }
                        })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }

    selected?.let { file ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 34.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(52.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (file.name.endsWith(".avi", true)) Icons.Rounded.Movie else Icons.Rounded.Description,
                                null, Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatSize(file.size)} · ${formatDate(file.modified)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (file.isVideo) {
                    Button(
                        onClick = { selected = null; onPlayVideo(file) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Смотреть таймлапс")
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (file.isPrintable) {
                    Button(
                        onClick = {
                            val target = file
                            selected = null
                            pendingRemote = target
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Печатать")
                    }
                    Spacer(Modifier.height(10.dp))
                }
                FilledTonalButton(
                    onClick = {
                        val target = file
                        selected = null
                        scope.launch {
                            vm.downloadFile(target) { }
                                .onFailure { vm.message(it.message ?: "Не удалось скачать") }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Скачать на телефон")
                }
                if (!file.isFileProtected) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { deleteTarget = file; selected = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    pendingRemote?.let { file ->
        PrintOptionsSheet(
            title = file.name,
            subtitle = "${formatSize(file.size)} · ${if (settings.demoMode) "демо" else "с SD-карты"}",
            filamentCount = 1,
            amsSlots = state.usedFilaments,
            initialProfileId = settings.printProfile,
            defaults = PrintOptions(
                timelapse = settings.timelapseDefault,
                bedLeveling = settings.bedLevelingDefault,
                flowCali = settings.flowCaliDefault,
                vibrationCali = settings.vibrationCaliDefault,
                useAms = state.hasAms,
            ),
            onDismiss = { pendingRemote = null },
            onStart = { options ->
                pendingRemote = null
                scope.launch {
                    vm.printRemote(file.path, options)
                        .onSuccess { vm.message("Печать: ${file.name}") }
                        .onFailure { vm.message(it.message ?: "Принтер не принял команду") }
                }
            },
        )
    }

    pendingUri?.let { uri ->
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "model.3mf"
        PrintOptionsSheet(
            title = "Отправить на принтер",
            subtitle = name,
            filamentCount = 1,
            amsSlots = state.usedFilaments,
            initialProfileId = settings.printProfile,
            defaults = PrintOptions(
                timelapse = settings.timelapseDefault,
                bedLeveling = settings.bedLevelingDefault,
                flowCali = settings.flowCaliDefault,
                vibrationCali = settings.vibrationCaliDefault,
                useAms = state.hasAms,
            ),
            onDismiss = { pendingUri = null },
            onStart = { options ->
                val target = uri
                pendingUri = null
                scope.launch {
                    vm.uploadAndPrint(target, options, startPrint = true)
                        .onSuccess { vm.message("Отправлено и запущено") }
                        .onFailure { vm.message(it.message ?: "Ошибка отправки") }
                    reloadKey++
                }
            },
        )
    }

    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить файл?") },
            text = { Text(file.name) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        vm.deleteFile(file)
                            .onSuccess { vm.message("Удалено"); reloadKey++ }
                            .onFailure { vm.message(it.message ?: "Не удалось удалить") }
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun FileRow(file: RemoteFile, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when {
                            file.isDir -> Icons.Rounded.Folder
                            file.name.endsWith(".avi", true) -> Icons.Rounded.Movie
                            else -> Icons.Rounded.Description
                        },
                        null,
                        Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (file.isDir) "папка" else "${formatSize(file.size)} · ${formatDate(file.modified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (file.isPrintable) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp).padding(4.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    if (bytes < 1024) return "$bytes Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale("ru", "RU"), if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
}

private fun formatDate(millis: Long): String {
    if (millis <= 0) return "—"
    val fmt = java.text.SimpleDateFormat("d MMM, HH:mm", Locale("ru", "RU"))
    return fmt.format(java.util.Date(millis))
}

private val RemoteFile.isVideo: Boolean
    get() = !isDir && (name.endsWith(".avi", true) || name.endsWith(".mp4", true) ||
        name.endsWith(".mkv", true) || name.endsWith(".mov", true))

private val RemoteFile.isFileProtected: Boolean
    get() = path.startsWith("/cache/") && name.startsWith(".")
