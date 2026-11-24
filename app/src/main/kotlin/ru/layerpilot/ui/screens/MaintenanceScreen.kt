package ru.layerpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.layerpilot.data.maintenance.HmsCatalog
import ru.layerpilot.data.maintenance.MaintenanceStore
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.TonalCard

private data class CareTask(
    val id: String,
    val title: String,
    val hint: String,
    val intervalHours: Int,
)

private val careTasks = listOf(
    CareTask("nozzle", "Прочистить сопло", "Холодная протяжка или игла", 50),
    CareTask("bed", "Вымыть пластину", "Обезжириватель, без металлической щётки", 20),
    CareTask("belts", "Проверить ремни", "Натяжение и люфт каретки", 200),
    CareTask("grease", "Смазать оси", "Только рекомендованная смазка", 300),
    CareTask("fan", "Продуть вентиляторы", "Радиатор и обдув модели", 100),
    CareTask("ptfe", "Проверить PTFE", "Деформация и замятия трубки", 500),
    CareTask("silica", "Заменить силикагель", "AMS и бокс с филаментом", 720),
)

@Composable
fun MaintenanceScreen(vm: AppViewModel, onBack: () -> Unit, onCalibration: () -> Unit = {}) {
    val state by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val store = remember { MaintenanceStore(context) }
    val key = settings.serial.ifBlank { settings.host.ifBlank { settings.activePrinterId } }
    var query by remember { mutableStateOf("") }
    var logText by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(0) }
    val log = remember(version, key) { store.log(key) }
    val hours = stats.totalMinutes / 60

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        ScreenHeader("Обслуживание", settings.printerName, onBack = onBack,
            actions = { TextButton(onClick = onCalibration) { Text("Калибровка") } })
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TonalCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Build, null, Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${hours} ч печати", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text(
                            "Ошибок сейчас: ${state.errors.size} · модель ${settings.model.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.errors.isNotEmpty()) {
                TonalCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WarningAmber, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Текущие ошибки", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.clearError() }) { Text("Сбросить") }
                    }
                    state.errors.forEach { error ->
                        val info = HmsCatalog.explain(error.code)
                        Text(info.code, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
                        Text(info.title, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(info.cause, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            TonalCard(Modifier.fillMaxWidth()) {
                Text("Регламент", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                careTasks.forEach { task ->
                    val last = store.lastDone(key, task.id)
                    val due = last <= 0L || (System.currentTimeMillis() - last) / 3_600_000L >= task.intervalHours
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp),
                            color = if (due) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(30.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Icon(if (due) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle,
                                    null, Modifier.size(16.dp),
                                    tint = if (due) MaterialTheme.colorScheme.onTertiaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (due) "Пора · ${task.hint}" else "Сделано · ${task.hint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (due) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = {
                            store.markDone(key, task.id); version++
                        }) { Text(if (due) "Готово" else "Ещё") }
                    }
                }
            }

            TonalCard(Modifier.fillMaxWidth()) {
                Text("Журнал ремонтов", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = logText,
                    onValueChange = { logText = it.take(300) },
                    placeholder = { Text("Например: заменил сопло 0.4, промыл PEI") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(onClick = {
                    store.addLog(key, logText); logText = ""; version++
                }) { Text("Добавить запись") }
                if (log.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    log.takeLast(8).reversed().forEach {
                        Text(
                            "${java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("ru")).format(java.util.Date(it.at))} — ${it.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            TonalCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Справочник HMS", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Код или слово: засор, AMS, сопло") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (query.isBlank()) {
                    Text(
                        "Базовый локальный справочник. Неизвестные коды объясняются по семейству модуля — без сети и облака.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HmsCatalog.search(query).take(20).forEach { info ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Text(info.title, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text(info.code, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(info.cause, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
