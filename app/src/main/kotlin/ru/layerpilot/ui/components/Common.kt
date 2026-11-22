package ru.layerpilot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import ru.layerpilot.ui.locale.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.data.model.Spool
import ru.layerpilot.data.printer.ConnectionStatus

@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = color,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun StatusChip(phase: PrintPhase, label: String? = null, demo: Boolean = false) {
    val (text, color, container) = when (phase) {
        PrintPhase.PRINTING -> Triple(label ?: if (demo) "ДЕМО · ПЕЧАТЬ" else "ПЕЧАТЬ",
            MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        PrintPhase.PAUSED -> Triple(label ?: "ПАУЗА",
            MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer)
        PrintPhase.PREPARE -> Triple(label ?: "ПОДГОТОВКА",
            MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer)
        PrintPhase.FINISHED -> Triple(label ?: "ГОТОВО",
            MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        PrintPhase.FAILED -> Triple(label ?: "ОШИБКА",
            MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
        PrintPhase.IDLE -> Triple(label ?: "СВОБОДЕН",
            MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainerHigh)
        PrintPhase.OFFLINE -> Triple(label ?: "НЕТ СВЯЗИ",
            MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(
            text.uppercase(),
            color = color,
            fontSize = 11.sp, lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
fun ConnectionBadge(status: ConnectionStatus, onClick: () -> Unit = {}) {
    val connectedGreen = if (isSystemInDarkTheme()) Color(0xFF7BD88F) else Color(0xFF1B7F3A)
    val (text, color) = when (status) {
        is ConnectionStatus.Connected -> if (status.demo) "ДЕМО" to MaterialTheme.colorScheme.tertiary
        else "ПОДКЛЮЧЕН" to connectedGreen
        is ConnectionStatus.Connecting -> "ПОДКЛЮЧЕНИЕ" to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatus.Reconnecting -> "ПЕРЕПОДКЛЮЧЕНИЕ" to MaterialTheme.colorScheme.tertiary
        is ConnectionStatus.Error -> "НЕТ СВЯЗИ" to MaterialTheme.colorScheme.error
        ConnectionStatus.Idle -> "НЕ НАСТРОЕНО" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (status is ConnectionStatus.Error || status is ConnectionStatus.Reconnecting) {
                Icon(Icons.Rounded.CloudOff, null, Modifier.size(13.dp), tint = color)
            } else {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            }
            Text(text, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (onBack != null) 8.dp else 22.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions?.invoke()
    }
}

@Composable
fun StatBlock(
    label: String,
    value: String,
    suffix: String? = null,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label.uppercase(), fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
            if (suffix != null) {
                Text(suffix, fontSize = 14.sp, lineHeight = 19.sp, color = accent.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 3.dp, start = 2.dp))
            }
        }
    }
}

@Composable
fun SpoolDot(color: Color, size: Dp = 18.dp, active: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            ),
    )
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp))
    }
}

fun slotLabel(slot: Int): String = when (slot) {
    254 -> "Внешняя катушка"
    255 -> "Не назначен"
    in 0..15 -> "AMS ${slot / 4 + 1} · слот ${slot % 4 + 1}"
    else -> "Слот $slot"
}

fun spoolSummary(spool: Spool?): String {
    spool ?: return "—"
    val remain = if (spool.remain in 0..100) "${spool.remain}%" else "—"
    return "${spool.type} · $remain"
}

@Composable
fun ProgressOverlay(transfer: ru.layerpilot.ui.TransferState?) {
    if (transfer == null) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(transfer.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                if (!transfer.indeterminate) {
                    Text("${(transfer.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                }
            }
            WavyProgress(
                progress = if (transfer.indeterminate) 0.15f else transfer.progress,
                height = 10.dp,
                animated = true,
            )
        }
    }
}
