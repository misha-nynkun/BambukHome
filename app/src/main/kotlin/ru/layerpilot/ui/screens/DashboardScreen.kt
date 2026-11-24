package ru.layerpilot.ui.screens

import ru.layerpilot.data.settings.DashboardSection
import ru.layerpilot.data.settings.dashboardHiddenSections
import ru.layerpilot.data.settings.dashboardSections
import ru.layerpilot.data.settings.encodeDashboardHidden
import ru.layerpilot.data.settings.encodeDashboardOrder
import ru.layerpilot.data.settings.profileList
import ru.layerpilot.data.settings.currentProfileId
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.horizontalScroll
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.data.model.PrinterSnapshot
import ru.layerpilot.data.model.Spool
import ru.layerpilot.data.printer.ConnectionStatus
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.ConnectionBadge
import ru.layerpilot.ui.components.SpoolDot
import ru.layerpilot.ui.components.StatBlock
import ru.layerpilot.ui.components.StatusChip
import ru.layerpilot.ui.components.TonalCard
import ru.layerpilot.ui.components.WaveGauge
import ru.layerpilot.ui.components.WavyProgress
import ru.layerpilot.ui.components.pressScale
import ru.layerpilot.ui.components.slotLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onSettings: () -> Unit,
    onControl: () -> Unit,
    onAms: () -> Unit,
    onCamera: () -> Unit,
    onFiles: () -> Unit,
    onPrinters: () -> Unit,
    onFarm: () -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.snapshot.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val online = connection is ConnectionStatus.Connected
    val demo = settings.demoMode
    val profiles = remember(settings.printersJson, settings.host, settings.activePrinterId) {
        settings.profileList()
    }
    val activeProfileId = settings.currentProfileId()

    var showStop by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showLayout by remember { mutableStateOf(false) }
    val hiddenSections = remember(settings.dashboardHidden) { settings.dashboardHiddenSections() }
    val sections = remember(settings.dashboardOrder) { settings.dashboardSections() }
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var live by remember(settings.dashboardLive) { mutableStateOf(settings.dashboardLive) }
    var pullRefreshing by remember { mutableStateOf(false) }
    val pullScope = rememberCoroutineScope()

    val cameraAvailable = (settings.demoMode || settings.model.hasCamera) &&
        "camera" !in hiddenSections

    LaunchedEffect(online, cameraAvailable, settings.host, live) {
        if (!cameraAvailable || !online || !live) return@LaunchedEffect
        while (isActive) {
            val shot = vm.cameraSnapshot()
            if (shot != null) jpeg = shot
            delay(6_000)
        }
    }

    PullToRefreshBox(
        isRefreshing = pullRefreshing,
        onRefresh = {
            pullRefreshing = true
            vm.requestRefresh()
            pullScope.launch {
                delay(1200)
                pullRefreshing = false
            }
        },
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    settings.printerName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (demo) "демонстрация · без принтера" else settings.host.ifBlank { "адрес не задан" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConnectionBadge(connection, onClick = onSettings)
            Spacer(Modifier.width(4.dp))
            if (settings.showFarmQuickAction && profiles.size >= 3) {
                IconButton(onClick = onFarm) {
                    Icon(Icons.Rounded.Storage, "Ферма принтеров",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { showLayout = true }) {
                Icon(Icons.Rounded.Tune, "Настроить экран",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, "Настройки", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (profiles.size > 12) {
            Surface(
                onClick = onPrinters,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Print, null, Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${settings.printerName} · ${profiles.size} принтеров",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Выбрать", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        } else if (profiles.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                profiles.forEach { profile ->
                    val selected = profile.id == activeProfileId
                    Surface(
                        onClick = {
                            if (selected) onPrinters() else vm.switchPrinter(profile.id)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.pressScale(),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(7.dp)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                profile.name,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Surface(
                    onClick = onPrinters,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(Icons.Rounded.Add, "Принтеры", Modifier.padding(7.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!online) {
            OfflineCard(
                message = when (val c = connection) {
                    is ConnectionStatus.Error -> c.message
                    is ConnectionStatus.Reconnecting -> "Переподключение: ${c.reason}"
                    is ConnectionStatus.Connecting -> "Подключаемся к ${settings.host}…"
                    else -> "Принтер не подключён"
                },
                onRetry = { vm.reconnect() },
                onSettings = onSettings,
            )
            Spacer(Modifier.height(14.dp))
        }

        var rendered = 0
        sections.forEach { section ->
            if (section.id in hiddenSections) return@forEach
            if (section == DashboardSection.CAMERA && !cameraAvailable) return@forEach
            if (section == DashboardSection.ERRORS &&
                state.errors.isEmpty() && state.printError == 0
            ) return@forEach
            if (rendered > 0) Spacer(Modifier.height(14.dp))
            when (section) {
                DashboardSection.HERO -> HeroCard(
                    state = state,
                    online = online,
                    demo = demo,
                    animated = settings.waveAnimation,
                    onOpenControl = onControl,
                )
                DashboardSection.QUICK -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickAction(
                        icon = if (state.phase == PrintPhase.PRINTING) Icons.Rounded.Pause
                        else Icons.Rounded.PlayArrow,
                        label = if (state.phase == PrintPhase.PRINTING) "Пауза" else "Продолжить",
                        enabled = state.phase == PrintPhase.PRINTING || state.phase == PrintPhase.PAUSED,
                        onClick = { if (state.phase == PrintPhase.PRINTING) vm.pause() else vm.resume() },
                        modifier = Modifier.weight(1f),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        accent = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    QuickAction(
                        icon = Icons.Rounded.Stop,
                        label = "Стоп",
                        enabled = state.phase in listOf(PrintPhase.PRINTING, PrintPhase.PAUSED, PrintPhase.PREPARE),
                        accent = MaterialTheme.colorScheme.onErrorContainer,
                        container = MaterialTheme.colorScheme.errorContainer,
                        onClick = { showStop = true },
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        icon = if (state.lightOn == true) Icons.Rounded.Lightbulb
                        else Icons.Outlined.Lightbulb,
                        label = if (state.lightOn == true) "Свет вкл" else "Свет",
                        enabled = state.lightOn != null || state.phase != PrintPhase.OFFLINE,
                        accent = MaterialTheme.colorScheme.onTertiaryContainer,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = { vm.setLight(state.lightOn != true) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        icon = Icons.Rounded.Speed,
                        label = state.speedName,
                        enabled = online,
                        accent = MaterialTheme.colorScheme.onSecondaryContainer,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = { showSpeed = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                DashboardSection.TEMPS -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TempCard(
                        label = "Сопло",
                        current = state.nozzle,
                        target = state.nozzleTarget,
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    TempCard(
                        label = "Стол",
                        current = state.bed,
                        target = state.bedTarget,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (settings.model.hasChamber && (state.chamber ?: 0f) > 0f) {
                        val chamber = state.chamber
                        Box(Modifier.weight(1f)) {
                            TempCard(
                                label = "Камера",
                                current = chamber ?: 0f,
                                target = null,
                                accent = MaterialTheme.colorScheme.secondary,
                                unavailable = chamber == null,
                            )
                        }
                    }
                }
                DashboardSection.AMS -> AmsStrip(state = state, onOpen = onAms)
                DashboardSection.ERRORS -> ErrorCard(state = state, onClear = { vm.clearError() })
                DashboardSection.CAMERA -> CameraCard(
                    jpeg = jpeg,
                    state = state,
                    live = live,
                    onOpen = onCamera,
                    onToggleLive = {
                        live = !live
                        vm.updateSettings { it.copy(dashboardLive = live) }
                    },
                )
                DashboardSection.ACTIONS -> OfflineActionsRow(
                    onFiles = onFiles,
                    onControl = onControl,
                )
            }
            rendered++
        }

        transfer?.let {
            Spacer(Modifier.height(14.dp))
            ru.layerpilot.ui.components.ProgressOverlay(it)
        }

        Spacer(Modifier.height(100.dp))
    }
    }

    if (showStop) {
        AlertDialog(
            onDismissRequest = { showStop = false },
            icon = { Icon(Icons.Rounded.ErrorOutline, null) },
            title = { Text("Остановить печать?") },
            text = { Text("Продолжить будет нельзя. Модель останется на столе, принтер освободится.") },
            confirmButton = {
                TextButton(onClick = { showStop = false; vm.stop() }) {
                    Text("Остановить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showStop = false }) { Text("Отмена") } },
        )
    }

    if (showSpeed) {
        ModalBottomSheet(
            onDismissRequest = { showSpeed = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("Скорость печати", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("Меняется на лету, не влияет на уже напечатанные слои",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                (1..4).forEach { level ->
                    val name = PrinterSnapshot.SPEED_NAMES[level] ?: ""
                    val pct = PrinterSnapshot.SPEED_PERCENT[level] ?: 100
                    Surface(
                        onClick = { vm.setSpeed(level); showSpeed = false },
                        color = if (level == state.speedLevel) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text("$pct%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showLayout) {
        DashboardLayoutSheet(
            settings = settings,
            onDismiss = { showLayout = false },
            onSave = { order, hidden ->
                showLayout = false
                vm.updateSettings {
                    it.copy(
                        dashboardOrder = encodeDashboardOrder(order),
                        dashboardHidden = encodeDashboardHidden(hidden),
                    )
                }
            },
        )
    }
}

@Composable
private fun HeroCard(
    state: PrinterSnapshot,
    online: Boolean,
    demo: Boolean,
    animated: Boolean,
    onOpenControl: () -> Unit,
) {
    val busy = state.phase in listOf(PrintPhase.PRINTING, PrintPhase.PAUSED, PrintPhase.PREPARE)
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(state.phase, demo = demo)
                Spacer(Modifier.weight(1f))
                if (busy) {
                    Text(
                        state.stageLabel.ifBlank { state.speedName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            !online -> "Принтер выключен"
                            busy -> state.jobName.ifBlank { "Печать" }
                            state.phase == PrintPhase.FINISHED -> state.jobName.ifBlank { "Готово" }
                            else -> "Принтер свободен"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        when {
                            !online -> "Проверьте питание и Wi-Fi"
                            busy -> "Слой ${state.layer} из ${state.totalLayers.takeIf { it > 0 } ?: "—"}"
                            state.phase == PrintPhase.FINISHED -> "Можно забирать со стола"
                            else -> "Отправьте готовый 3MF из «Файлов»"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    if (busy) {
                        StatBlock("Осталось", state.remainingLabel)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "финиш ≈ ${PrinterSnapshot.etaText(state.remainingMin)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                WaveGauge(
                    progress = state.progress / 100f,
                    size = 126.dp,
                    animated = animated && busy,
                    caption = if (busy) "напечатано" else if (state.phase == PrintPhase.FINISHED) "готово" else "прогресс",
                )
            }
            Spacer(Modifier.height(16.dp))
            WavyProgress(
                progress = state.progress / 100f,
                animated = animated && busy,
                height = 16.dp,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenControl),
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (busy) "Слой ${state.layer} / ${state.totalLayers} · ${state.speedName} ${state.speedPercent}%"
                    else "Управление принтером",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" Настроить", fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(21.dp),
            color = if (enabled) container else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (enabled) accent.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
            modifier = Modifier.size(62.dp).pressScale(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, null, Modifier.size(26.dp),
                    tint = if (enabled) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun TempCard(
    label: String,
    current: Float,
    target: Float?,
    accent: Color,
    modifier: Modifier = Modifier,
    unavailable: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label.uppercase(), fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (unavailable) "—" else "${current.toInt()}°",
                fontSize = 28.sp, lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = if (unavailable) MaterialTheme.colorScheme.onSurfaceVariant else accent,
            )
            Text(
                if (target != null && target > 0) "цель ${target.toInt()}°" else "",
                fontSize = 11.sp, lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AmsStrip(state: PrinterSnapshot, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AMS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                if (state.hasAms) {
                    val unit = state.amsUnits.firstOrNull()
                    Text(
                        "влажность ${unit?.humidityLabel ?: "—"} · ${unit?.temp?.toInt() ?: "—"}°",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("внешняя катушка", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            if (state.hasAms) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.usedFilaments.take(4).forEach { spool ->
                        SpoolTile(spool, active = spool.slot == state.activeSlot, modifier = Modifier.weight(1f))
                    }
                    repeat(4 - state.usedFilaments.take(4).size) {
                        EmptySpoolTile(Modifier.weight(1f))
                    }
                }
            } else {
                val spool = state.externalSpool
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpoolDot(
                        color = spool?.colorCompose ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                        size = 34.dp,
                        active = state.activeSlot == 254,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(spool?.type ?: "Не заряжена",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (spool?.remain in 0..100) "осталось ${spool?.remain}%" else "остаток неизвестен",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.activeSlot == 254) {
                        Text("АКТИВНА", fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpoolTile(spool: Spool, active: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .padding(9.dp),
    ) {
        SpoolDot(spool.colorCompose, size = 20.dp, active = active)
        Spacer(Modifier.height(8.dp))
        Text(spool.type, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            if (spool.remain in 0..100) "${spool.remain}%" else "—",
            fontSize = 10.sp, lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptySpoolTile(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(9.dp),
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Spacer(Modifier.height(8.dp))
        Text("пусто", fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("—", fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorCard(state: PrinterSnapshot, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Ошибка принтера", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    state.errors.firstOrNull()?.text ?: "Код ${state.printError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, "Сбросить", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun CameraCard(
    jpeg: ByteArray?,
    state: PrinterSnapshot,
    live: Boolean,
    onOpen: () -> Unit,
    onToggleLive: () -> Unit,
) {
    val bitmap = remember(jpeg) {
        jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(28.dp))) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = "Камера принтера", modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CameraAlt, null, Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        Spacer(Modifier.height(6.dp))
                        Text("Камера · подключение…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    )
                )
            )
            Surface(
                onClick = onToggleLive,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (live) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF6E66)))
                        Text("LIVE", fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, letterSpacing = 1.sp)
                    } else {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(12.dp), tint = Color.White)
                        Text("ВКЛЮЧИТЬ LIVE", fontSize = 10.sp, lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f),
                            letterSpacing = 0.6.sp)
                    }
                }
            }
            if (!live) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Камера", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (state.phase == PrintPhase.PRINTING) "${state.progress}% · ${state.remainingLabel}"
                        else "нажмите, чтобы открыть",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.16f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CameraAlt, null, Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Смотреть", fontSize = 12.sp, lineHeight = 17.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineCard(message: String, onRetry: () -> Unit, onSettings: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Нет связи с принтером", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 2)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text("Подключиться снова") }
                TextButton(onClick = onSettings) { Text("Настройки") }
            }
        }
    }
}

@Composable
private fun OfflineActionsRow(onFiles: () -> Unit, onControl: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onControl,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.weight(1f),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Управление", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(
            onClick = onFiles,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.weight(1f),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Файлы", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}


@Composable
private fun DashboardLayoutSheet(
    settings: ru.layerpilot.data.settings.AppSettings,
    onDismiss: () -> Unit,
    onSave: (List<DashboardSection>, Set<String>) -> Unit,
) {
    var order by remember(settings.dashboardOrder) { mutableStateOf(settings.dashboardSections()) }
    var hidden by remember(settings.dashboardHidden) {
        mutableStateOf(settings.dashboardHiddenSections())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Рабочий стол") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Двигайте карточки стрелками и скрывайте то, что не нужно. Порядок сохранится.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                order.forEachIndexed { index, section ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(section.title, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    order = order.toMutableList().also {
                                        val item = it.removeAt(index)
                                        it.add(index - 1, item)
                                    }
                                }
                            },
                            enabled = index > 0,
                        ) { Icon(Icons.Rounded.KeyboardArrowUp, "Выше", Modifier.size(19.dp)) }
                        IconButton(
                            onClick = {
                                if (index < order.lastIndex) {
                                    order = order.toMutableList().also {
                                        val item = it.removeAt(index)
                                        it.add(index + 1, item)
                                    }
                                }
                            },
                            enabled = index < order.lastIndex,
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, "Ниже", Modifier.size(19.dp)) }
                        IconButton(onClick = {
                            hidden = if (section.id in hidden) hidden - section.id
                            else hidden + section.id
                        }) {
                            Icon(
                                if (section.id in hidden) Icons.Rounded.VisibilityOff
                                else Icons.Rounded.Visibility,
                                if (section.id in hidden) "Показать" else "Скрыть",
                                Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(order, hidden) }) { Text("Сохранить") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    order = DashboardSection.defaultOrder
                    hidden = emptySet()
                }) { Text("Сбросить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
