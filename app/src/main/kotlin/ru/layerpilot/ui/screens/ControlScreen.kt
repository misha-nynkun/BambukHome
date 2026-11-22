package ru.layerpilot.ui.screens

import ru.layerpilot.data.models.SkipPlate
import ru.layerpilot.data.models.PlateInspector
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import ru.layerpilot.ui.components.ExpressiveLoadingIndicator
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import ru.layerpilot.ui.locale.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.data.model.PrinterSnapshot
import ru.layerpilot.data.printer.FanKind
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.SectionTitle
import ru.layerpilot.ui.components.TonalCard
import ru.layerpilot.ui.components.WavySlider
import ru.layerpilot.ui.components.slotLabel

@Composable
fun ControlScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy = state.phase in listOf(PrintPhase.PRINTING, PrintPhase.PAUSED, PrintPhase.PREPARE)
    var showSkip by remember { mutableStateOf(false) }
    var gcode by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        ScreenHeader(
            title = "Управление",
            subtitle = if (busy) "${state.jobName} · ${state.progress}%" else "принтер свободен",
            onBack = onBack,
        )
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Temperatures(state, settings, onNozzle = vm::setNozzleTemp, onBed = vm::setBedTemp)

            Fans(state, settings, onFan = vm::setFan)

            TonalCard(Modifier.fillMaxWidth()) {
                Text("Скорость", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { level ->
                        FilterChip(
                            selected = state.speedLevel == level,
                            onClick = { vm.setSpeed(level) },
                            label = { Text(PrinterSnapshot.SPEED_SHORT[level] ?: "") },
                        )
                    }
                }
            }

            Movement(state = state, busy = busy, onMove = vm::move, onHome = { vm.home() }, onExtrude = vm::extrude)

            if (busy) {
                TonalCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Пропуск деталей", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text("Отключить выбранные объекты со следующего слоя",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = { showSkip = true }) {
                            Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Пропуск")
                        }
                    }
                }
            }

            TonalCard(Modifier.fillMaxWidth()) {
                Text("G-code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Для опытных: команды уходят как есть",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = gcode,
                    onValueChange = { gcode = it },
                    placeholder = { Text("M104 S220\nM106 P1 S255") },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.sendGcode(gcode); gcode = "" },
                    enabled = gcode.isNotBlank() && !busy,
                    shape = RoundedCornerShape(14.dp)) {
                    Text("Отправить")
                }
            }

            Text(
                "Перемещение и экструзия доступны, когда принтер не печатает. " +
                    "Температуру и вентиляторы можно менять во время печати.",
                fontSize = 12.sp, lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showSkip) SkipDialog(vm = vm, onDismiss = { showSkip = false })
}

@Composable
private fun Temperatures(
    state: PrinterSnapshot,
    settings: ru.layerpilot.data.settings.AppSettings,
    onNozzle: (Int) -> Unit,
    onBed: (Int) -> Unit,
) {
    TonalCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Температуры", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("сейчас ${state.nozzle.toInt()}° / ${state.bed.toInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        TempSlider(
            label = "Сопло",
            value = state.nozzleTarget,
            current = state.nozzle,
            range = 0f..300f,
            presets = listOf(0, 200, 220, 250, 270),
            accent = MaterialTheme.colorScheme.tertiary,
            thickness = settings.waveThickness,
            amplitude = settings.waveAmplitude,
            animated = settings.waveAnimation,
            onCommit = onNozzle,
        )
        Spacer(Modifier.height(16.dp))
        TempSlider(
            label = "Стол",
            value = state.bedTarget,
            current = state.bed,
            range = 0f..100f,
            presets = listOf(0, 55, 65, 80, 90),
            accent = MaterialTheme.colorScheme.primary,
            thickness = settings.waveThickness,
            amplitude = settings.waveAmplitude,
            animated = settings.waveAnimation,
            onCommit = onBed,
        )
    }
}

@Composable
private fun TempSlider(
    label: String,
    value: Float,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    presets: List<Int>,
    accent: androidx.compose.ui.graphics.Color,
    thickness: Float,
    amplitude: Float,
    animated: Boolean,
    onCommit: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("${local.toInt()}°", fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.width(8.dp))
        Text("сейчас ${current.toInt()}°", fontSize = 11.sp, lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    WavySlider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local.toInt()) },
        valueRange = range,
        accent = accent,
        thickness = thickness,
        amplitude = amplitude,
        animated = animated,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEach { preset ->
            FilterChip(
                selected = local.toInt() == preset,
                onClick = { local = preset.toFloat(); onCommit(preset) },
                label = { Text(if (preset == 0) "выкл" else "$preset°") },
            )
        }
    }
}

@Composable
private fun Fans(
    state: PrinterSnapshot,
    settings: ru.layerpilot.data.settings.AppSettings,
    onFan: (FanKind, Int) -> Unit,
) {
    val available = when (state.phase) {
        PrintPhase.OFFLINE -> listOf(FanKind.PART)
        else -> listOf(FanKind.PART, FanKind.AUX, FanKind.CHAMBER)
    }
    TonalCard(Modifier.fillMaxWidth()) {
        Text("Вентиляторы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        available.forEach { kind ->
            val value = when (kind) {
                FanKind.PART -> state.partFan
                FanKind.AUX -> state.auxFan
                FanKind.CHAMBER -> state.chamberFan
            }
            var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kind.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("${local.toInt()}%", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
            WavySlider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { onFan(kind, local.toInt()) },
                valueRange = 0f..100f,
                thickness = settings.waveThickness,
                amplitude = settings.waveAmplitude,
                animated = settings.waveAnimation,
            )
        }
    }
}

@Composable
private fun Movement(
    state: PrinterSnapshot,
    busy: Boolean,
    onMove: (String, Int) -> Unit,
    onHome: () -> Unit,
    onExtrude: (Int) -> Unit,
) {
    var step by remember { mutableStateOf(10) }
    TonalCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Перемещение", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (busy) {
                Text("во время печати недоступно", fontSize = 11.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Шаг", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(1, 10, 50).forEach { value ->
                FilterChip(selected = step == value, onClick = { step = value },
                    label = { Text("$value мм") })
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(56.dp))
                MoveButton(Icons.Rounded.KeyboardArrowUp, "Y−", enabled = !busy) { onMove("Y", -step) }
                Spacer(Modifier.size(56.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MoveButton(Icons.Rounded.ChevronLeft, "X−", enabled = !busy) { onMove("X", -step) }
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(12.dp).background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape))
                }
                MoveButton(Icons.Rounded.ChevronRight, "X+", enabled = !busy) { onMove("X", step) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(56.dp))
                MoveButton(Icons.Rounded.KeyboardArrowDown, "Y+", enabled = !busy) { onMove("Y", step) }
                Spacer(Modifier.size(56.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MoveButton(Icons.Rounded.KeyboardArrowDown, "Z−", enabled = !busy) { onMove("Z", -step) }
                MoveButton(Icons.Rounded.KeyboardArrowUp, "Z+", enabled = !busy) { onMove("Z", step) }
                MoveButton(Icons.Rounded.Home, "Домой", enabled = !busy) { onHome() }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = { onExtrude(10) }, enabled = !busy,
                shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("10 мм")
            }
            FilledTonalButton(onClick = { onExtrude(-10) }, enabled = !busy,
                shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Remove, null, Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("10 мм")
            }
            Spacer(Modifier.weight(1f))
            Text("сопло ${state.nozzle.toInt()}°", fontSize = 11.sp, lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}

@Composable
private fun MoveButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(56.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, label, Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Text(label, fontSize = 10.sp, lineHeight = 13.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun SkipDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val plate by vm.skipPlate.collectAsStateWithLifecycle()
    val loading by vm.skipLoading.collectAsStateWithLifecycle()
    val selected = remember { mutableStateListOf<Int>() }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.loadSkipForCurrentJob()
            .onSuccess { loaded = true }
            .onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пропуск деталей") },
        text = {
            when {
                loading -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Читаем пластину и детали из 3MF…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    ExpressiveLoadingIndicator(size = 42.dp)
                }
                error != null -> Text("Не удалось: $error")
                plate == null || plate!!.parts.isEmpty() -> Text("На этой пластине нет деталей для пропуска")
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Нажмите на деталь на пластине или отметьте её в списке.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    SkipPlateView(
                        plate = plate!!,
                        selected = selected,
                        onToggle = { id ->
                            if (id in selected) selected.remove(id) else selected.add(id)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    plate!!.parts.forEach { obj ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = obj.id in selected,
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(obj.id) else selected.remove(obj.id)
                                },
                            )
                            Text(obj.name, modifier = Modifier.weight(1f))
                            Text("#${obj.id}", fontSize = 11.sp, lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Печать выбранных деталей прекратится со следующего слоя. Действие необратимо.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.requestSkipObjects(selected.toList()); onDismiss() },
                enabled = selected.isNotEmpty() && !loading,
            ) { Text("Пропустить (${selected.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SkipPlateView(
    plate: SkipPlate,
    selected: List<Int>,
    onToggle: (Int) -> Unit,
) {
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val bed = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val normalFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val normalLine = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val skipFill = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
    val skipLine = MaterialTheme.colorScheme.error

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bed,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(plate.bedWidth / plate.bedHeight)
                .pointerInput(plate) {
                    detectTapGestures { offset ->
                        val x = (offset.x / size.width) * plate.bedWidth
                        val y = (1f - offset.y / size.height) * plate.bedHeight
                        val hit = plate.parts.lastOrNull { part ->
                            part.hasShape && PlateInspector.contains(part.polygon, x, y)
                        }
                        if (hit != null) onToggle(hit.id)
                    }
                },
        ) {
            val sx = size.width / plate.bedWidth
            val sy = size.height / plate.bedHeight
            // сетка стола
            val step = if (plate.bedWidth <= 200f) 20f else 25f
            var g = 0f
            while (g <= plate.bedWidth) {
                drawLine(grid, Offset(g * sx, 0f), Offset(g * sx, size.height), 1f)
                drawLine(grid, Offset(0f, g * sy), Offset(size.width, g * sy), 1f)
                g += step
            }
            drawRect(outline, style = Stroke(width = 2.dp.toPx()))
            plate.parts.forEach { part ->
                if (!part.hasShape) return@forEach
                val isSelected = part.id in selected
                val path = Path()
                part.polygon.forEachIndexed { index, point ->
                    val px = point.x * sx
                    val py = size.height - point.y * sy
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path, color = if (isSelected) skipFill else normalFill)
                drawPath(
                    path,
                    color = if (isSelected) skipLine else normalLine,
                    style = Stroke(width = (if (isSelected) 2.5f else 1.5f).dp.toPx()),
                )
            }
        }
    }
}
