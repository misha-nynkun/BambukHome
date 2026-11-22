package ru.layerpilot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class LayerPilotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val events = NotificationChannel(
            CHANNEL_PRINTER,
            getString(R.string.app_name) + " · события",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Завершение печати и ошибки (со звуком)"
            setShowBadge(true)
        }
        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            getString(R.string.app_name) + " · прогресс",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Тихий прогресс печати в шторке"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        manager.createNotificationChannels(listOf(events, progress))
    }

    companion object {
        const val CHANNEL_PRINTER = "printer_events"
        const val CHANNEL_PROGRESS = "print_progress"
    }
}
