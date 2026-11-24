package ru.layerpilot.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.layerpilot.MainActivity
import ru.layerpilot.R
import ru.layerpilot.data.model.PrinterSnapshot
import ru.layerpilot.data.settings.AppSettings

private fun prefs(context: Context) = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

object WidgetUpdater {
    fun save(context: Context, snapshot: PrinterSnapshot, settings: AppSettings) {
        prefs(context).edit()
            .putString("name", settings.printerName)
            .putString("job", snapshot.jobName)
            .putInt("progress", snapshot.progress)
            .putString("phase", snapshot.phase.name)
            .putFloat("nozzle", snapshot.nozzle)
            .putFloat("bed", snapshot.bed)
            .putString("remaining", snapshot.remainingLabel)
            .apply()
        updateAll(context)
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(BambuSmallWidget::class.java, BambuMediumWidget::class.java).forEach { cls ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            ids.forEach { id -> manager.updateAppWidget(id, build(context, cls == BambuMediumWidget::class.java, id)) }
        }
    }

    fun build(context: Context, medium: Boolean, id: Int): RemoteViews {
        val p = prefs(context)
        val views = RemoteViews(context.packageName, if (medium) R.layout.widget_medium else R.layout.widget_small)
        views.setTextViewText(R.id.widget_title, p.getString("name", "BambuHome") ?: "BambuHome")
        val phase = p.getString("phase", "IDLE") ?: "IDLE"
        val progress = p.getInt("progress", 0)
        val job = p.getString("job", "").orEmpty()
        views.setTextViewText(
            R.id.widget_status,
            when (phase) {
                "PRINTING" -> "${progress}% · ${p.getString("remaining", "")}"
                "PAUSED" -> "Пауза · ${progress}%"
                "FINISHED" -> "Готово"
                "FAILED" -> "Ошибка"
                else -> "Свободен"
            },
        )
        views.setProgressBar(R.id.widget_progress, 100, progress, phase == "PREPARE")
        if (medium) {
            views.setTextViewText(
                R.id.widget_temps,
                "Сопло ${p.getFloat("nozzle", 0f).toInt()}° · стол ${p.getFloat("bed", 0f).toInt()}°",
            )
            views.setTextViewText(R.id.widget_job, job)
            val pause = PendingIntent.getActivity(
                context, 101,
                Intent(context, MainActivity::class.java).putExtra("printer_action", "pause"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val resume = PendingIntent.getActivity(
                context, 102,
                Intent(context, MainActivity::class.java).putExtra("printer_action", "resume"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_pause, pause)
            views.setOnClickPendingIntent(R.id.widget_resume, resume)
        }
        val open = PendingIntent.getActivity(
            context, 100,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_title, open)
        return views
    }
}

class BambuSmallWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, WidgetUpdater.build(context, false, it)) }
    }
}

class BambuMediumWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, WidgetUpdater.build(context, true, it)) }
    }
}
