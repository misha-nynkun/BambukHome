package ru.layerpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.layerpilot.data.printer.ConnectionStatus
import ru.layerpilot.data.settings.AppSettings
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.TonalCard
import ru.layerpilot.ui.components.pressClickable

enum class SettingsCategory(
    val title: String,
    val hint: String,
    val icon: ImageVector,
) {
    PRINTER("Принтер", "Подключение, профили и ферма", Icons.Rounded.Wifi),
    APPEARANCE("Внешний вид", "Тема, шрифт, язык, рабочий стол", Icons.Rounded.Palette),
    PRINTING("Печать", "Температуры, уведомления, камера", Icons.Rounded.Print),
    CLAWD("Clawd", "Питомец, факты и его настройки", Icons.Rounded.Pets),
    ICONS("Иконка", "Своя иконка приложения", Icons.Rounded.Apps),
    ABOUT("О приложении", "Версия, лицензия и поддержка", Icons.Rounded.Info),
}

@Composable
fun SettingsHome(
    settings: AppSettings,
    connection: ConnectionStatus,
    onBack: () -> Unit,
    onOpen: (SettingsCategory) -> Unit,
    onPrinters: () -> Unit,
) {
    val categories = SettingsCategory.entries
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Настройки", "всё по папкам · локально", onBack = onBack)
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TonalCard(Modifier.fillMaxWidth().pressClickable(onPrinters)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(52.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                null,
                                Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            settings.printerName.ifBlank { "Принтер" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                settings.demoMode -> "демо-режим · без железа"
                                settings.host.isBlank() -> "адрес не задан"
                                connection is ConnectionStatus.Connected -> "${settings.host} · на связи"
                                else -> "${settings.host} · нет связи"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            categories.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { category ->
                        TonalCard(
                            Modifier.weight(1f).pressClickable { onOpen(category) },
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(42.dp),
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        category.icon,
                                        null,
                                        Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                category.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                category.hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}
