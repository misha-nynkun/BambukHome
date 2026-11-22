package ru.layerpilot.ui.screens

import ru.layerpilot.ui.components.ExpressiveSpinner
import ru.layerpilot.ui.components.WavyProgress
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import ru.layerpilot.ui.locale.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.layerpilot.data.printer.PrintOptions
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.LocalModel
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.SectionTitle
import ru.layerpilot.ui.components.TonalCard

@Composable
fun ModelsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onMakerWorld: () -> Unit = {},
    onOpen3D: (java.io.File) -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.snapshot.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var showOptions by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importShared(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        ScreenHeader(
            title = "Модели",
            subtitle = "ссылка или файл с телефона",
            onBack = onBack,
        )
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            TonalCard(Modifier.fillMaxWidth()) {
                Text("ВСТАВЬТЕ ССЫЛКУ", fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("MakerWorld, Thingiverse, Printables…") },
                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    trailingIcon = {
                        if (input.isBlank()) {
                            IconButton(onClick = {
                                input = clipboard.getText()?.text.orEmpty()
                            }) {
                                Icon(Icons.Rounded.ContentPaste, "Вставить", Modifier.size(20.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val directFile = input.substringBefore('?').substringBefore('#')
                        .matches(Regex(".*\\.(3mf|gcode|stl|obj|step|stp)$", RegexOption.IGNORE_CASE))
                    val makerWorld = !directFile && input.contains("makerworld", true)
                    Button(
                        onClick = {
                            if (makerWorld) {
                                val link = Regex("https?://\\S+").find(input.trim())?.value
                                    ?.trimEnd('.', ',', ')', ';')
                                val target = (link ?: input.trim()).let {
                                    if (it.startsWith("http://") || it.startsWith("https://")) it
                                    else "https://$it"
                                }
                                val opened = runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                                }.isSuccess
                                vm.message(
                                    if (opened) "MakerWorld открыт в браузере. Войдите и скачайте .3mf, затем «Файл»."
                                    else "Не удалось открыть браузер",
                                )
                            } else {
                                vm.resolveLink(input)
                            }
                        },
                        enabled = input.isNotBlank() && !models.resolving,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        if (models.resolving) {
                            ExpressiveSpinner(Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(
                                if (makerWorld) Icons.Rounded.OpenInBrowser
                                else Icons.Rounded.CloudDownload,
                                null, Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            when {
                                models.resolving -> "Разбираем…"
                                makerWorld -> "Открыть в браузере"
                                else -> "Разобрать ссылку"
                            },
                        )
                    }
                    FilledTonalButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.Rounded.FileOpen, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Файл")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Приложение скачивает только готовый .3mf. STL нужно нарезать в слайсере — принтер не умеет печатать модели без G-code.",
                    fontSize = 11.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TonalCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Public, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MakerWorld", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text("Личный кабинет, поиск и скачивание 3MF",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onMakerWorld) { Text("Открыть") }
                }
                Text(
                    "Войдите по email Bambu Lab или через браузер на вкладке MakerWorld. " +
                        "Пароль не сохраняется, токен входа хранится зашифрованным.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            models.error?.let { message ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(message, Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            models.link?.let { link ->
                LinkCard(
                    link = link,
                    downloadName = models.downloadName,
                    downloadProgress = models.downloadProgress,
                    onDownload = { vm.downloadModel(it) },
                    onOpenBrowser = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.pageUrl)))
                        }
                    },
                )
            }

            models.local?.let { local ->
                SectionTitle("Выбранная модель")
                LocalModelCard(
                    model = local,
                    onPrint = { showOptions = true },
                    on3D = { onOpen3D(local.file) },
                )
            }

            transfer?.let { ru.layerpilot.ui.components.ProgressOverlay(it) }

            if (models.history.isEmpty() && models.link == null && models.local == null) {
                TonalCard(Modifier.fillMaxWidth()) {
                    Text("ЧТО ПОДДЕРЖИВАЕТСЯ", fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    SourceRow("MakerWorld", "превью, описание и файлы, если сайт их отдаёт")
                    SourceRow("Thingiverse", "полный список файлов через бесплатный API-токен")
                    SourceRow("Printables", "превью и прямые ссылки, когда доступны без входа")
                    SourceRow("Прямая ссылка", "любой .3mf или .gcode из интернета")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Если сайт закрыт Cloudflare или требует вход, BambukHome покажет карточку " +
                            "и откроет страницу в браузере. Скачанный 3MF добавьте кнопкой «Файл» — " +
                            "он сразу уйдёт на принтер.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (models.history.isNotEmpty()) {
                SectionTitle("Недавние файлы")
                models.history.forEach { item ->
                    HistoryRow(
                        model = item,
                        selected = models.local?.file?.absolutePath == item.file.absolutePath,
                        onClick = { vm.selectLocal(item.file) },
                        on3D = { onOpen3D(item.file) },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showOptions) {
        val local = models.local
        if (local == null) {
            showOptions = false
        } else {
            PrintOptionsSheet(
                title = local.name,
                subtitle = buildString {
                    append("${local.info?.filamentCount ?: 1} цвет(а)")
                    local.info?.plateCount?.takeIf { it > 1 }?.let { append(" · $it пластин") }
                    append(" · ${formatMb(local.size)}")
                },
                filamentCount = local.info?.filamentCount ?: 1,
                amsSlots = state.usedFilaments,
                initialProfileId = settings.printProfile,
                defaults = PrintOptions(
                    timelapse = settings.timelapseDefault,
                    bedLeveling = settings.bedLevelingDefault,
                    flowCali = settings.flowCaliDefault,
                    vibrationCali = settings.vibrationCaliDefault,
                    useAms = state.hasAms,
                ),
                onDismiss = { showOptions = false },
                onStart = { options ->
                    showOptions = false
                    scope.launch {
                        vm.sendModelToPrinter(options)
                            .onFailure { vm.message(it.message ?: "Не удалось отправить") }
                    }
                },
            )
        }
    }
}

@Composable
private fun LinkCard(
    link: ru.layerpilot.data.models.ModelLink,
    downloadName: String?,
    downloadProgress: Float,
    onDownload: (ru.layerpilot.data.models.RemoteModelFile) -> Unit,
    onOpenBrowser: () -> Unit,
) {
    TonalCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    link.source.label.take(2).uppercase(),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(link.source.label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenBrowser) {
                Icon(Icons.Rounded.OpenInNew, "Открыть в браузере", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (link.imageUrl.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            AsyncImage(
                model = link.imageUrl,
                contentDescription = link.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(link.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (link.author.isNotBlank()) {
            Text("автор: ${link.author}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (link.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(link.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4,
                overflow = TextOverflow.Ellipsis)
        }

        if (link.files.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    link.note.ifBlank { "Сайт не отдал прямую ссылку на файл." },
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenBrowser, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Скачать на сайте")
                }
            }
            Text(
                "После скачивания нажмите «Файл» и выберите .3mf — он уйдёт на принтер.",
                fontSize = 11.sp, lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Spacer(Modifier.height(12.dp))
            link.files.forEach { file ->
                Surface(
                    onClick = { if (file.downloadable) onDownload(file) else onOpenBrowser() },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when {
                                !file.downloadable -> Icons.Rounded.OpenInNew
                                file.name.endsWith(".3mf", true) -> Icons.Rounded.Description
                                else -> Icons.Rounded.CloudDownload
                            },
                            null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildString {
                                    if (file.size > 0) append(formatMb(file.size)).append(" · ")
                                    append(
                                        when {
                                            !file.downloadable -> "открыть на сайте"
                                            file.name.endsWith(".3mf", true) -> "готов к печати"
                                            else -> "только скачать"
                                        }
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (downloadName == file.name) {
                            ExpressiveSpinner(Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Rounded.CloudDownload, null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (downloadName == file.name && downloadProgress > 0f) {
                    WavyProgress(
                        progress = downloadProgress,
                        height = 10.dp,
                        animated = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (link.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(link.note, fontSize = 11.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LocalModelCard(model: LocalModel, onPrint: () -> Unit, on3D: () -> Unit) {
    TonalCard(Modifier.fillMaxWidth()) {
        val thumb = remember(model.file.absolutePath) {
            model.info?.thumbnail?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(78.dp).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null) {
                    Image(thumb, contentDescription = null, modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.Description, null, Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(formatMb(model.size))
                        model.info?.let { info ->
                            append(" · ${info.filamentCount} цвет(а)")
                            if (info.plateCount > 1) append(" · ${info.plateCount} пластины")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!model.printable) {
                    Text("нужен .3mf или .gcode", fontSize = 11.sp, lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
        model.info?.filaments?.takeIf { it.isNotEmpty() }?.let { filaments ->
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                filaments.take(8).forEach { f ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(11.dp).clip(CircleShape).background(
                                    androidx.compose.ui.graphics.Color(
                                        f.color ?: 0xFF3A4A3C.toInt()
                                    )
                                ),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(f.type.ifBlank { "цвет ${f.id + 1}" }, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPrint,
                enabled = model.printable,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Печатать", fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = on3D,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(50.dp),
            ) {
                Icon(Icons.Rounded.ViewInAr, null, Modifier.size(19.dp))
                Spacer(Modifier.width(5.dp))
                Text("3D")
            }
        }
    }
}

@Composable
private fun HistoryRow(
    model: LocalModel,
    selected: Boolean,
    onClick: () -> Unit,
    on3D: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Description, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMb(model.size), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (model.name.endsWith(".3mf", true)) {
                IconButton(onClick = on3D) {
                    Icon(Icons.Rounded.ViewInAr, "3D", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SourceRow(name: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Box(
            Modifier.padding(top = 7.dp).size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatMb(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) String.format(java.util.Locale("ru", "RU"), "%.1f МБ", mb)
    else String.format(java.util.Locale("ru", "RU"), "%.0f КБ", bytes / 1024.0)
}
