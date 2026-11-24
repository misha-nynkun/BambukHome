package ru.layerpilot.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.layerpilot.data.stats.PrintEvent
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.TonalCard
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val events by vm.printEvents.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val success = events.count { it.success }
    val fail = events.count { !it.success }
    val energy = events.sumOf { it.energyKwh.toDouble() }.toFloat()
    val cost = events.sumOf { it.cost.toDouble() }.toFloat()
    val minutes = events.sumOf { it.minutes }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        ScreenHeader("История", "печати, время и энергия", onBack = onBack)
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TonalCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HistoryStat("Всего", "${stats.printsFinished}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    HistoryStat("Успешно", "$success", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    HistoryStat("Ошибки", "$fail", if (fail > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Время: ${formatHours(minutes)} · энергия: ${"%.2f".format(energy)} кВт·ч" +
                        if (cost > 0f) " · ${"%.0f".format(cost)} ₽" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TonalCard(Modifier.fillMaxWidth()) {
                Text("Последние 7 дней", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                WeeklyChart(events)
            }

            if (events.isEmpty()) {
                TonalCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, null, Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("История появится после завершения печати.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                events.asReversed().take(40).forEach { event -> HistoryRow(event) }
                TextButton(onClick = { vm.clearPrintHistory() }) {
                    Text("Очистить историю", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HistoryStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeeklyChart(events: List<PrintEvent>) {
    val starts = remember(events) {
        val base = Calendar.getInstance()
        base.set(Calendar.HOUR_OF_DAY, 0); base.set(Calendar.MINUTE, 0)
        base.set(Calendar.SECOND, 0); base.set(Calendar.MILLISECOND, 0)
        (6 downTo 0).map { back ->
            val c = base.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -back)
            c.timeInMillis
        }
    }
    val days = remember(events, starts) {
        starts.map { start ->
            val end = start + 86_400_000L
            events.filter { it.finishedAt in start until end }.sumOf { it.minutes }.coerceAtLeast(0)
        }
    }
    val labels = remember(starts) {
        starts.map { start ->
            java.text.SimpleDateFormat("EE", Locale("ru")).format(Date(start))
                .replaceFirstChar { it.titlecase(Locale("ru")) }
        }
    }
    val max = (days.maxOrNull() ?: 0).coerceAtLeast(1)
    val hasData = days.any { it > 0 }
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val gap = 8.dp.toPx()
        val w = (size.width - gap * 6) / 7f
        val baseHeight = 12.dp.toPx()
        days.forEachIndexed { i, value ->
            val h = if (value == 0) baseHeight else
                (value.toFloat() / max) * size.height
            val x = i * (w + gap)
            drawRoundRect(
                track,
                Offset(x, size.height - baseHeight),
                Size(w, baseHeight),
                CornerRadius(w / 2f),
            )
            if (value > 0) {
                drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2f))
            }
        }
    }
    if (!hasData) {
        Text(
            "На этой неделе печатей ещё не было",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryRow(event: PrintEvent) {
    Surface(shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp),
                color = if (event.success) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (event.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        null, Modifier.size(20.dp),
                        tint = if (event.success) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(event.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "${SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(event.finishedAt))} · " +
                        formatHours(event.minutes) + if (event.cost > 0f) " · ${"%.0f".format(event.cost)} ₽" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (event.success) "OK" else "Ошибка", fontSize = 11.sp,
                color = if (event.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatHours(minutes: Int): String =
    if (minutes < 60) "$minutes мин" else "${minutes / 60} ч ${minutes % 60} мин"
