package ru.layerpilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.data.printer.ConnectionStatus
import ru.layerpilot.data.printer.FarmPrinter
import ru.layerpilot.data.settings.PrinterProfile
import ru.layerpilot.data.maintenance.HmsCatalog
import ru.layerpilot.data.settings.profileList
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.CalmLoadingIndicator
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.StatusChip
import ru.layerpilot.ui.components.TonalCard
import ru.layerpilot.ui.components.pressClickable

@Composable
fun FarmScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenPrinter: (String) -> Unit,
    onPrinters: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val states by vm.farmPrinters.collectAsStateWithLifecycle()
    val monitored by vm.farmMonitored.collectAsStateWithLifecycle()
    val profiles = remember(settings.printersJson, settings.host, settings.activePrinterId) {
        settings.profileList()
    }
    DisposableEffect(Unit) {
        vm.startFarm()
        onDispose { vm.stopFarm() }
    }
    val online = states.values.count { it.connection is ConnectionStatus.Connected }
    val printing = states.values.count { it.snapshot.phase == PrintPhase.PRINTING }
    val errors = states.values.count { it.snapshot.errors.isNotEmpty() || it.snapshot.phase == PrintPhase.FAILED }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        ScreenHeader(
            "Ферма",
            if (errors > 0) "$online онлайн · $printing печать · $errors ошибки"
            else "$online онлайн · $printing печать",
            onBack = onBack,
            onRefresh = {
                states.keys.forEach { vm.farmCommand(it, "refresh") }
            },
            actions = {
                TextButton(onClick = onPrinters) { Text("Все принтеры") }
            },
        )
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TonalCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (profiles.size > monitored) "Мониторим $monitored из ${profiles.size}"
                            else "Принтеров: ${profiles.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (profiles.size > monitored) {
                                "Одновременно держим до 8 MQTT-подключений, чтобы не перегружать сеть."
                            } else {
                                "Статусы обновляются напрямую по локальной сети."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (states.isEmpty()) CalmLoadingIndicator(size = 30.dp)
                }
            }
            LazyVerticalGrid(
                columns = if (states.size <= 1) GridCells.Fixed(1)
                else GridCells.Adaptive(168.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(states.values.toList(), key = { it.profile.id }) { item ->
                    FarmCard(
                        item = item,
                        onOpen = { onOpenPrinter(item.profile.id) },
                        onPause = { vm.farmCommand(item.profile.id, "pause") },
                        onResume = { vm.farmCommand(item.profile.id, "resume") },
                        onStop = { vm.farmCommand(item.profile.id, "stop") },
                        onLight = {
                            vm.farmCommand(
                                item.profile.id,
                                if (item.snapshot.lightOn == true) "light_off" else "light_on",
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FarmCard(
    item: FarmPrinter,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onLight: () -> Unit,
) {
    val profile = item.profile
    val state = item.snapshot
    val connected = item.connection is ConnectionStatus.Connected
    val error = state.errors.isNotEmpty() || state.phase == PrintPhase.FAILED
    TonalCard(
        Modifier.fillMaxWidth().pressClickable(onClick = onOpen),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (error) MaterialTheme.colorScheme.errorContainer
                else if (connected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (connected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                        null,
                        Modifier.size(17.dp),
                        tint = if (error) MaterialTheme.colorScheme.onErrorContainer
                        else if (connected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    profile.host.ifBlank { profile.model.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        StatusChip(state.phase)
        Spacer(Modifier.height(10.dp))
        if (state.phase == PrintPhase.PRINTING || state.phase == PrintPhase.PAUSED) {
            Text(
                state.jobName.ifBlank { "Печать" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.progress}% · ${state.remainingLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                when {
                    state.phase == PrintPhase.FINISHED -> "Готово"
                    state.phase == PrintPhase.IDLE -> "Свободен"
                    connected -> "Ожидание задания"
                    else -> "Нет связи"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (error) {
            Spacer(Modifier.height(6.dp))
            Text(
                state.errors.firstOrNull()?.let { HmsCatalog.explain(it.code).title } ?: "Ошибка принтера",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append("Сопло ${state.nozzle.toInt()}° · стол ${state.bed.toInt()}°")
                if (profile.model.hasChamber) append(" · камера ${state.chamber?.toInt() ?: 0}°")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (state.phase == PrintPhase.PRINTING) {
                IconButton(onClick = onPause, enabled = connected) {
                    Icon(Icons.Rounded.Pause, "Пауза", Modifier.size(20.dp))
                }
            } else if (state.phase == PrintPhase.PAUSED) {
                IconButton(onClick = onResume, enabled = connected) {
                    Icon(Icons.Rounded.PlayArrow, "Продолжить", Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onStop, enabled = connected && state.phase in listOf(PrintPhase.PRINTING, PrintPhase.PAUSED)) {
                Icon(Icons.Rounded.Stop, "Стоп", Modifier.size(20.dp))
            }
            IconButton(onClick = onLight, enabled = connected) {
                Icon(Icons.Rounded.Lightbulb, "Свет", Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onOpen() }) {
                Icon(Icons.Rounded.ChevronRight, "Открыть принтер", Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
