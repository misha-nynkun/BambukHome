package ru.layerpilot.notifications

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.layerpilot.LayerPilotApp
import ru.layerpilot.R
import ru.layerpilot.ui.locale.Lang

/**
 * Держит процесс живым, пока идёт печать, чтобы MQTT получал обновления
 * и прогресс в шторке оставался реальным. Само уведомление обновляет ViewModel.
 */
class PrintMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground(null)
        watcher = scope.launch {
            var lastKey = ""
            var lastAt = 0L
            PrintState.state.collect { data ->
                val now = android.os.SystemClock.elapsedRealtime()
                if (data == null) {
                    startAsForeground(null)
                    lastKey = ""
                    return@collect
                }
                val s = data.snapshot
                val key = "${s.phase}|${s.progress}|${s.layer}|${s.remainingMin}|${s.errors.size}"
                if (key != lastKey || now - lastAt > 30_000) {
                    startAsForeground(data)
                    lastKey = key
                    lastAt = now
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground(PrintState.state.value)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground(data: PrintData?) {
        runCatching {
            val notification = if (data == null) baseNotification()
            else PrintNotifications.build(this, data.snapshot, data.printerName)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    PrintNotifications.ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(PrintNotifications.ID, notification)
            }
        }
    }

    private fun baseNotification(): Notification {
        val pending = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, ru.layerpilot.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, LayerPilotApp.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_bambuk)
            .setContentTitle("BambukHome")
            .setContentText(Lang.translate("Печать"))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setProgress(0, 0, true)
            .build()
    }

    companion object {
        fun start(context: Context) {
            if (!PrintNotifications.allowed(context)) return
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PrintMonitorService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PrintMonitorService::class.java)) }
            PrintNotifications.cancel(context)
        }
    }
}
