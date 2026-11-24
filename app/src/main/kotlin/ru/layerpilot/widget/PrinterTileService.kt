package ru.layerpilot.widget

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ru.layerpilot.MainActivity
import ru.layerpilot.R

/** Плитка в быстрых настройках: статус и быстрый вход в BambukHome. */
class PrinterTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val p = getSharedPreferences("widget", MODE_PRIVATE)
        val phase = p.getString("phase", "IDLE") ?: "IDLE"
        val progress = p.getInt("progress", 0)
        tile.state = when (phase) {
            "PRINTING", "PAUSED", "PREPARE" -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = when (phase) {
            "PRINTING" -> "Печать $progress%"
            "PAUSED" -> "Пауза $progress%"
            "FINISHED" -> "Готово"
            "FAILED" -> "Ошибка"
            else -> "BambuHome"
        }
        tile.icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_bambuk)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
    }
}
