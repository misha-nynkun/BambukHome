package ru.layerpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import ru.layerpilot.ui.locale.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.layerpilot.data.model.Spool
import ru.layerpilot.data.printer.PrintOptions
import ru.layerpilot.data.printer.PrintProfiles
import ru.layerpilot.ui.components.SpoolDot

/**
 * Единый лист параметров запуска: одинаково работает для файла на SD, импортированного 3MF
 * и модели из ссылки. AMS-маппинг показываем только когда есть AMS и больше одного цвета.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintOptionsSheet(
    title: String,
    subtitle: String,
    filamentCount: Int,
    amsSlots: List<Spool>,
    defaults: PrintOptions,
    initialProfileId: String = "standard",
    onDismiss: () -> Unit,
    onStart: (PrintOptions) -> Unit,
) {
    var profileId by remember { mutableStateOf(initialProfileId) }
    var timelapse by remember { mutableStateOf(defaults.timelapse) }
    var bedLeveling by remember { mutableStateOf(defaults.bedLeveling) }
    var flowCali by remember { mutableStateOf(defaults.flowCali) }
    var vibrationCali by remember { mutableStateOf(defaults.vibrationCali) }
    var useAms by remember { mutableStateOf(defaults.useAms && amsSlots.isNotEmpty()) }
    val mapping = remember {
        mutableStateListOf<Int>().apply {
            val preset = defaults.amsMapping
            repeat(filamentCount.coerceAtLeast(1)) { i ->
                add(preset.getOrNull(i) ?: amsSlots.getOrNull(i)?.slot ?: amsSlots.firstOrNull()?.slot ?: 0)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 34.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))

            Text("Профиль печати", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrintProfiles.all.forEach { profile ->
                    FilterChip(
                        selected = profileId == profile.id,
                        onClick = {
                            profileId = profile.id
                            timelapse = profile.timelapse
                            bedLeveling = profile.bedLeveling
                            flowCali = profile.flowCali
                            vibrationCali = profile.vibrationCali
                        },
                        label = { Text(profile.name) },
                    )
                }
            }
            Text(
                PrintProfiles.byId(profileId).subtitle +
                    " · толщина слоя задаётся в слайсере",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(10.dp))

            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    ToggleRow("Таймлапс", "Видео печати в папке timelapse", timelapse) { timelapse = it }
                    ToggleRow("Выравнивание стола", "Автоуровень перед печатью", bedLeveling) { bedLeveling = it }
                    ToggleRow("Калибровка потока", "Точнее стенки, дольше подготовка", flowCali) { flowCali = it }
                    ToggleRow("Калибровка вибраций", "Меньше «звона» на углах", vibrationCali) { vibrationCali = it }
                }
            }

            if (amsSlots.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        ToggleRow("Печать из AMS", "Автоподача выбранных катушек", useAms) { useAms = it }
                    }
                }
                if (useAms) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (filamentCount > 1) "Сопоставление цветов" else "Катушка для печати",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                    repeat(filamentCount.coerceAtLeast(1)) { index ->
                        MappingRow(
                            index = index,
                            slots = amsSlots,
                            selected = mapping.getOrNull(index) ?: amsSlots.first().slot,
                            onSelect = { slot -> if (index < mapping.size) mapping[index] = slot },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (filamentCount > 1) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        "В модели $filamentCount цвет(а). Проверьте, что нужные катушки стоят в AMS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    onStart(
                        PrintOptions(
                            profileId = profileId,
                            speedLevel = PrintProfiles.byId(profileId).speedLevel,
                            timelapse = timelapse,
                            bedLeveling = bedLeveling,
                            flowCali = flowCali,
                            vibrationCali = vibrationCali,
                            useAms = useAms && amsSlots.isNotEmpty(),
                            amsMapping = if (useAms) mapping.toList() else emptyList(),
                        )
                    )
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text("Начать печать", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Отмена")
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MappingRow(index: Int, slots: List<Spool>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val spool = slots.firstOrNull { it.slot == selected }

    Surface(
        onClick = { open = true },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxNumber(index + 1)
            Spacer(Modifier.width(12.dp))
            SpoolDot(spool?.colorCompose ?: MaterialTheme.colorScheme.surfaceContainerHighest, size = 26.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(spool?.type ?: "Слот $selected", style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium)
                Text(
                    spool?.let { "AMS ${it.slot / 4 + 1} · ${it.remain.takeIf { r -> r in 0..100 } ?: "—"}%" }
                        ?: "выберите катушку",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                slots.forEach { s ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SpoolDot(s.colorCompose, size = 16.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("${s.type} · AMS ${s.slot / 4 + 1}/${s.slot % 4 + 1}")
                            }
                        },
                        onClick = { onSelect(s.slot); open = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxNumber(n: Int) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            "$n",
            fontSize = 12.sp, lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
