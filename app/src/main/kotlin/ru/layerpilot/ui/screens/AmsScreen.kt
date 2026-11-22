package ru.layerpilot.ui.screens

import ru.layerpilot.ui.components.ExpressiveSpinner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import ru.layerpilot.ui.locale.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.layerpilot.data.model.AmsUnit
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.data.model.PrinterSnapshot
import ru.layerpilot.data.model.Spool
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.SectionTitle
import ru.layerpilot.ui.components.SpoolDot
import ru.layerpilot.ui.components.StatBlock
import ru.layerpilot.ui.components.TonalCard
import ru.layerpilot.ui.components.slotLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Spool?>(null) }
    var confirmUnload by remember { mutableStateOf(false) }
    val busy = state.phase in listOf(PrintPhase.PRINTING, PrintPhase.PAUSED, PrintPhase.PREPARE)
    val changing = state.targetSlot != 255 && state.targetSlot != state.activeSlot

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Пластик",
            subtitle = when {
                changing -> "Идёт смена прутка…"
                state.hasAms -> "${state.usedFilaments.size} катушек · AMS"
                state.externalSpool != null -> "внешняя катушка"
                else -> "нет данных"
            },
            onBack = onBack,
        )
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            if (changing) {
                TonalCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Смена прутка", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                "Цель: ${slotLabel(state.targetSlot)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        FilledTonalButton(onClick = { vm.amsReply("done") }) {
                            Text("Готово")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ExpressiveSpinner(size = 28.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            if (state.hasAms) {
                val used = state.usedFilaments
                val withRemain = used.mapNotNull { it.remain.takeIf { r -> r in 0..100 } }
                val average = if (withRemain.isEmpty()) -1 else withRemain.average().toInt()
                val low = used.count { it.remain in 0..15 }
                TonalCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("Катушек", "${used.size}", accent = MaterialTheme.colorScheme.primary)
                        StatBlock("Средний остаток", if (average >= 0) "$average%" else "—")
                        StatBlock(
                            "Мало",
                            if (low > 0) "$low" else "0",
                            accent = if (low > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (low > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Есть катушки с остатком ≤15%. Проверьте перед длинной печатью.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                state.amsUnits.forEach { unit ->
                    AmsUnitCard(
                        unit = unit,
                        state = state,
                        onSlot = { selected = it },
                    )
                }
            }

            SectionTitle(if (state.hasAms) "Внешняя катушка" else "Катушка сбоку")
            ExternalSpoolCard(
                spool = state.externalSpool,
                active = state.activeSlot == 254,
                busy = busy,
                onClick = {
                    if (state.externalSpool != null) selected = state.externalSpool
                },
                onLoad = { vm.loadSpool(254) },
                onUnload = { confirmUnload = true },
            )

            TonalCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Как это работает", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Принтер сам нагревает сопло до температуры пластика, подаёт пруток " +
                        "и проверяет датчик. Следите за экраном принтера: иногда нужно подтвердить действие кнопкой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    val spool = selected
    if (spool != null) {
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            SpoolSheet(
                spool = spool,
                active = spool.slot == state.activeSlot,
                onLoad = { vm.loadSpool(spool.slot); selected = null },
                onUnload = { selected = null; confirmUnload = true },
            )
        }
    }

    if (confirmUnload) {
        AlertDialog(
            onDismissRequest = { confirmUnload = false },
            title = { Text("Выгрузить пруток?") },
            text = { Text("Принтер нагреет сопло и вытянет пластик обратно в катушку. Если печать идёт, она встанет на паузу.") },
            confirmButton = {
                TextButton(onClick = { confirmUnload = false; vm.unloadSpool() }) { Text("Выгрузить") }
            },
            dismissButton = { TextButton(onClick = { confirmUnload = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AmsUnitCard(unit: AmsUnit, state: PrinterSnapshot, onSlot: (Spool) -> Unit) {
    TonalCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AMS ${unit.id + 1}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    "${unit.trays.count { it.exists }} из 4 слотов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Rounded.WaterDrop, null, Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.tertiary)
                    Text(unit.humidityLabel, fontSize = 11.sp, lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.AcUnit, null, Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.tertiary)
                    Text("${unit.temp.toInt()}°", fontSize = 11.sp, lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            unit.trays.forEach { tray ->
                BigSlot(
                    spool = tray,
                    active = tray.slot == state.activeSlot,
                    target = tray.slot == state.targetSlot,
                    onClick = { onSlot(tray) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (unit.humidity in 1..2) "Влажность высокая — просушите катушки"
            else "Влажность в норме",
            style = MaterialTheme.typography.bodySmall,
            color = if (unit.humidity in 1..2) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BigSlot(
    spool: Spool,
    active: Boolean,
    target: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val low = spool.remain in 0..15
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val progressColor = if (low) MaterialTheme.colorScheme.error else spool.colorCompose
    Surface(
        onClick = onClick,
        enabled = spool.exists,
        shape = RoundedCornerShape(20.dp),
        color = when {
            !spool.exists -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = trackColor,
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        style = stroke,
                    )
                    if (spool.remain in 0..100) {
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = 360f * spool.remain / 100f,
                            useCenter = false,
                            style = stroke,
                        )
                    }
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (spool.exists) spool.colorCompose
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!spool.exists) {
                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (target) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                if (spool.exists) spool.type else "пусто",
                fontSize = 11.sp, lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (spool.remain in 0..100) "${spool.remain}%" else if (spool.exists) "—" else " ",
                fontSize = 10.sp, lineHeight = 13.sp,
                fontWeight = if (low) FontWeight.Bold else FontWeight.Normal,
                color = if (low) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExternalSpoolCard(
    spool: Spool?,
    active: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
) {
    TonalCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .background(spool?.colorCompose ?: MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(spool?.type ?: "Не заряжена", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        spool?.brand?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                        spool?.name?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                        append(if (spool?.remain in 0..100) "осталось ${spool?.remain}%" else "остаток неизвестен")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                if (active) {
                    Text("ЗАПРАВЛЕНА", fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onLoad, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Заправить")
            }
            FilledTonalButton(onClick = onUnload, enabled = spool != null,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Выгрузить")
            }
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            Text("Во время печати заправка поставит её на паузу",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpoolSheet(spool: Spool, active: Boolean, onLoad: () -> Unit, onUnload: () -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 34.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpoolDot(spool.colorCompose, size = 42.dp, active = active)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(spool.type, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text(slotLabel(spool.slot), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(18.dp))

        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) {
                DetailRow("Бренд", spool.brand.ifBlank { "—" })
                DetailRow("Линия", spool.name.ifBlank { "—" })
                DetailRow("Остаток", if (spool.remain in 0..100) "${spool.remain}%" else "неизвестен")
                DetailRow("Температура сопла", "${spool.minTemp}–${spool.maxTemp}°")
                DetailRow("Рекомендуемая заправка", "${spool.loadTemp}°")
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onLoad, enabled = !active, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (active) "Уже заправлена" else "Заправить")
            }
            FilledTonalButton(onClick = onUnload, enabled = active, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Выгрузить")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
