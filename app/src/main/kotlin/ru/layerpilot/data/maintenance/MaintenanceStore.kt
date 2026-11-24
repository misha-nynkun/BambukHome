package ru.layerpilot.data.maintenance

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RepairEntry(val at: Long, val text: String)

class MaintenanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("maintenance", Context.MODE_PRIVATE)

    fun lastDone(printerKey: String, taskId: String): Long =
        prefs.getLong("done_${printerKey}_$taskId", 0L)

    fun markDone(printerKey: String, taskId: String) {
        prefs.edit().putLong("done_${printerKey}_$taskId", System.currentTimeMillis()).apply()
    }

    fun log(printerKey: String): List<RepairEntry> {
        val raw = prefs.getString("log_$printerKey", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(RepairEntry(o.optLong("at"), o.optString("text")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addLog(printerKey: String, text: String) {
        val clean = text.trim().take(300)
        if (clean.length < 3) return
        val items = log(printerKey).takeLast(40) + RepairEntry(System.currentTimeMillis(), clean)
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("at", it.at).put("text", it.text)) }
        prefs.edit().putString("log_$printerKey", arr.toString()).apply()
    }
}
