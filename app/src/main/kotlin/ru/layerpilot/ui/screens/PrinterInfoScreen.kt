package ru.layerpilot.ui.screens

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.layerpilot.BuildConfig
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.ConnectionBadge
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.SectionTitle
import ru.layerpilot.ui.components.StatBlock
import ru.layerpilot.ui.components.TonalCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrinterInfoScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.snapshot.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    var editStats by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        ScreenHeader(
            title = "О принтере",
            subtitle = settings.printerName,
            onBack = onBack,
            actions = {
                androidx.compose.material3.IconButton(onClick = { showQr = true }) {
                    Icon(Icons.Rounded.QrCode, "QR-код принтера")
                }
            },
        )
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Surface(
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(58.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Print, null, Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(settings.printerName, style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(
                                    settings.model.label.takeIf { it.isNotBlank() },
                                    state.machine.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ConnectionBadge(connection)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        StatBlock("Слой", if (state.totalLayers > 0) "${state.layer}" else "—")
                        StatBlock("Сопло", "${state.nozzleDiameter} мм")
                        StatBlock("Прогресс", "${state.progress}%",
                            accent = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.reconnect() }, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Переподключиться")
                        }
                        if (connection is ru.layerpilot.data.printer.ConnectionStatus.Error ||
                            connection is ru.layerpilot.data.printer.ConnectionStatus.Reconnecting
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                                Text(
                                    (connection as? ru.layerpilot.data.printer.ConnectionStatus.Error)?.message
                                        ?: "переподключение…",
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle("Устройство")
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    InfoRow("Модель", settings.model.label)
                    InfoRow("Серийный номер", settings.serial.ifBlank { "—" })
                    InfoRow("Прошивка", state.firmware.ifBlank { "—" })
                    InfoRow("Код устройства", state.machine.ifBlank { "—" })
                    InfoRow("Сопло", "${state.nozzleDiameter} мм")
                    InfoRow("Область печати", settings.model.buildVolume)
                    InfoRow("IP-адрес", settings.host.ifBlank { "—" })
                    InfoRow("Порт FTPS", settings.ftpPort.toString())
                    InfoRow("Wi-Fi", wifiLabel(state.wifiDbm))
                    InfoRow("Камера", if (settings.model.hasCamera) "есть, порт 6000" else "нет")
                    InfoRow("AMS", if (state.hasAms) "${state.usedFilaments.size} катушек" else "внешняя катушка")
                    InfoRow("Камера нагрева", if (settings.model.hasChamber) "есть датчик" else "нет")
                }
            }

            SectionTitle("Что принтер сообщает сейчас")
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    InfoRow("Состояние", phaseLabel(state.phase, state.stageLabel))
                    InfoRow("Задание", state.jobName.ifBlank { "—" })
                    InfoRow("Слой", if (state.totalLayers > 0) "${state.layer} / ${state.totalLayers}" else "—")
                    InfoRow("Осталось", state.remainingLabel)
                    InfoRow("Температуры", "сопло ${state.nozzle.toInt()}° · стол ${state.bed.toInt()}°")
                    InfoRow("Ошибки HMS", if (state.errors.isEmpty()) "нет" else "${state.errors.size}")
                }
            }

            SectionTitle(
                "Статистика BambukHome",
                action = {
                    TextButton(onClick = { editStats = true }) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Вписать")
                    }
                },
            )
            TonalCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatBlock(
                        "Напечатано", formatHours(stats.totalMinutes),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatBlock(
                        "Завершено", "${stats.printsFinished}",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (stats.lastAt > 0) {
                    Text(
                        "Последняя печать: ${stats.lastJob.ifBlank { "модель" }} · ${dateText(stats.lastAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (stats.printsFinished > 0) {
                    Text(
                        "Статистика вписана вручную. Дальше приложение продолжит считать само.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Пока нет завершённых печатей. Время считается, пока приложение открыто и печать идёт.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Принтер не отдаёт общий счётчик часов по локальному MQTT, поэтому здесь — " +
                        "время, которое BambukHome наблюдал сам. Версия ${BuildConfig.VERSION_NAME}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { confirmReset = true }) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сбросить статистику", color = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                "Данные принтера никуда не отправляются: всё читается напрямую по Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(30.dp))
        }
    }

    if (editStats) {
        StatsEditDialog(
            initialMinutes = stats.totalMinutes,
            initialPrints = stats.printsFinished,
            onDismiss = { editStats = false },
            onSave = { minutes, prints ->
                editStats = false
                vm.setStats(minutes, prints)
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(Icons.Rounded.Info, null) },
            title = { Text("Сбросить статистику?") },
            text = { Text("Часы и число завершённых печатей обнулятся. Настройки принтера не изменятся.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resetStats() }) {
                    Text("Сбросить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
        )
    }
    if (showQr) {
        val payload = "bambuhome://printer?host=${settings.host}&serial=${settings.serial}&model=${settings.model.name}&name=${settings.printerName}"
        val bitmap = remember(payload) { qrBitmap(payload) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("QR-код принтера") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    bitmap?.let {
                        Image(
                            it.asImageBitmap(),
                            contentDescription = "QR-код",
                            modifier = Modifier.size(240.dp),
                        )
                    }
                    Text("${settings.host}:8883 · ${settings.serial}",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun wifiLabel(dbm: Int?): String = when {
    dbm == null -> "—"
    dbm >= -55 -> "$dbm dBm · отлично"
    dbm >= -67 -> "$dbm dBm · хорошо"
    dbm >= -75 -> "$dbm dBm · слабо"
    else -> "$dbm dBm · очень слабо"
}

private fun formatHours(minutes: Int): String {
    if (minutes <= 0) return "0 ч"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "$h ч $m мин" else "$m мин"
}

private fun dateText(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale("ru", "RU")).format(Date(millis))

private fun phaseLabel(phase: PrintPhase, stage: String): String = when (phase) {
    PrintPhase.PRINTING -> stage.ifBlank { "печать" }
    PrintPhase.PAUSED -> "пауза"
    PrintPhase.PREPARE -> "подготовка"
    PrintPhase.FINISHED -> "готово"
    PrintPhase.FAILED -> "ошибка"
    PrintPhase.IDLE -> "свободен"
    PrintPhase.OFFLINE -> "нет связи"
}


@Composable
private fun StatsEditDialog(
    initialMinutes: Int,
    initialPrints: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    var hours by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", initialMinutes / 60f))
    }
    var prints by remember { mutableStateOf(initialPrints.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, null) },
        title = { Text("Статистика вручную") },
        text = {
            Column {
                Text(
                    "Впишите общее время печати и число завершённых работ. Дальше приложение продолжит считать само.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it; error = null },
                    label = { Text("Всего часов") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prints,
                    onValueChange = { prints = it.filter(Char::isDigit); error = null },
                    label = { Text("Завершено печатей") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hours.replace(',', '.').toFloatOrNull()
                val p = prints.toIntOrNull()
                if (h == null || h < 0f || p == null || p < 0) {
                    error = "Введите неотрицательные числа"
                } else {
                    onSave((h * 60f).toInt(), p)
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )

}

private fun qrBitmap(text: String, size: Int = 640): android.graphics.Bitmap? = runCatching {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) {
        pixels[y * size + x] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
}.getOrNull()
