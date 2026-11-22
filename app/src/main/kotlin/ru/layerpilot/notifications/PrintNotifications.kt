package ru.layerpilot.notifications

import android.annotation.SuppressLint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.layerpilot.LayerPilotApp
import ru.layerpilot.MainActivity
import ru.layerpilot.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.data.model.PrinterSnapshot
import ru.layerpilot.ui.locale.Lang

/**
 * Тихий прогресс печати в шторке. Канал LOW: без звука и вибрации,
 * обновляется без повторных оповещений. Звук остаётся только у финиша и ошибок.
 */
data class PrintData(val snapshot: PrinterSnapshot, val printerName: String)

/** Единый источник для foreground-уведомления: ViewModel пишет, сервис рисует. */
object PrintState {
    private val _state = MutableStateFlow<PrintData?>(null)
    val state = _state.asStateFlow()

    fun set(snapshot: PrinterSnapshot, printerName: String) {
        _state.value = PrintData(snapshot, printerName)
    }

    fun clear() {
        _state.value = null
    }
}

object PrintNotifications {

    const val ID = 4101

    fun allowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun build(context: Context, snapshot: PrinterSnapshot, printerName: String): Notification {
        val title = snapshot.jobName.ifBlank {
            when (snapshot.phase) {
                PrintPhase.PREPARE -> Lang.translate("Подготовка")
                PrintPhase.PAUSED -> Lang.translate("Пауза")
                else -> Lang.translate("Печать")
            }
        }
        val layerWord = when (Lang.code) {
            "ru" -> "слой"
            "de" -> "Schicht"
            "fr" -> "couche"
            "es" -> "capa"
            "it" -> "strato"
            "pt" -> "camada"
            "pl" -> "warstwa"
            "tr" -> "katman"
            "zh" -> "层"
            else -> "layer"
        }
        val line = when (snapshot.phase) {
            PrintPhase.PREPARE -> Lang.translate(snapshot.stageLabel.ifBlank { "Нагрев и калибровка" })
            PrintPhase.PAUSED -> "${Lang.translate("Пауза")} · ${snapshot.progress}%"
            else -> buildList {
                add("${snapshot.progress}%")
                if (snapshot.totalLayers > 0) {
                    add("$layerWord ${snapshot.layer}/${snapshot.totalLayers}")
                }
                if (snapshot.remainingMin > 0) add(Lang.translate(snapshot.remainingLabel))
            }.joinToString(" · ")
        }
        val pending = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, LayerPilotApp.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_bambuk)
            .setContentTitle(title)
            .setContentText(line)
            .setSubText(printerName)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF8FE39A.toInt())
        if (snapshot.phase == PrintPhase.PREPARE) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, snapshot.progress.coerceIn(0, 100), false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context, snapshot: PrinterSnapshot, printerName: String) {
        if (!allowed(context)) return
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(ID, build(context, snapshot, printerName))
        }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID) }
    }

    @Suppress("unused")
    fun addProgressChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            LayerPilotApp.CHANNEL_PROGRESS,
            "Прогресс печати",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Тихий прогресс в шторке: без звука и вибрации"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        manager.createNotificationChannel(channel)
    }

}
