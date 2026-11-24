package ru.layerpilot.data.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Context.statsStore: DataStore<Preferences> by preferencesDataStore(name = "print_stats")

data class PrintStats(
    val totalMinutes: Int = 0,
    val printsFinished: Int = 0,
    val lastJob: String = "",
    val lastAt: Long = 0,
) {
    val totalHours: Float get() = totalMinutes / 60f
}

data class PrintEvent(
    val id: Long,
    val name: String,
    val printer: String,
    val startedAt: Long,
    val finishedAt: Long,
    val minutes: Int,
    val success: Boolean,
    val energyKwh: Float,
    val cost: Float,
    val filamentGrams: Float,
)

/**
 * Локальная статистика: сколько печатали и сколько раз завершили.
 * Принтер не отдаёт общий счётчик часов по MQTT, поэтому считаем сами.
 */
class StatsRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _stats = MutableStateFlow(PrintStats())
    val stats: StateFlow<PrintStats> = _stats
    private val _events = MutableStateFlow<List<PrintEvent>>(emptyList())
    val events: StateFlow<List<PrintEvent>> = _events

    init {
        scope.launch {
            runCatching { context.statsStore.data.first() }.getOrNull()?.let { p ->
                _stats.value = PrintStats(
                    totalMinutes = p[Keys.MINUTES] ?: 0,
                    printsFinished = p[Keys.FINISHED] ?: 0,
                    lastJob = p[Keys.LAST_JOB] ?: "",
                    lastAt = p[Keys.LAST_AT] ?: 0,
                )
            }
            runCatching { context.statsStore.data.first() }.getOrNull()?.get(Keys.EVENTS)?.let { raw ->
                _events.value = parseEvents(raw)
            }
        }
    }

    fun addEvent(event: PrintEvent) {
        _events.value = (_events.value + event).takeLast(200)
        persistEvents()
    }

    fun clearEvents() {
        _events.value = emptyList()
        persistEvents()
    }

    private fun parseEvents(raw: String): List<PrintEvent> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    PrintEvent(
                        id = o.optLong("id"), name = o.optString("name"),
                        printer = o.optString("printer"), startedAt = o.optLong("started"),
                        finishedAt = o.optLong("finished"), minutes = o.optInt("minutes"),
                        success = o.optBoolean("success"), energyKwh = o.optDouble("energy", 0.0).toFloat(),
                        cost = o.optDouble("cost", 0.0).toFloat(),
                        filamentGrams = o.optDouble("grams", 0.0).toFloat(),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun persistEvents() {
        val arr = JSONArray()
        _events.value.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("printer", it.printer)
                    .put("started", it.startedAt).put("finished", it.finishedAt)
                    .put("minutes", it.minutes).put("success", it.success)
                    .put("energy", it.energyKwh.toDouble()).put("cost", it.cost.toDouble())
                    .put("grams", it.filamentGrams.toDouble()),
            )
        }
        scope.launch {
            runCatching { context.statsStore.edit { it[Keys.EVENTS] = arr.toString() } }
        }
    }

    fun addMinute() {
        _stats.value = _stats.value.copy(totalMinutes = _stats.value.totalMinutes + 1)
        persist()
    }

    fun finish(jobName: String) {
        _stats.value = _stats.value.copy(
            printsFinished = _stats.value.printsFinished + 1,
            lastJob = jobName,
            lastAt = System.currentTimeMillis(),
        )
        persist()
    }

    /** Ручная правка: часы и число завершённых печатей. */
    fun updateManual(totalMinutes: Int, printsFinished: Int) {
        _stats.value = _stats.value.copy(
            totalMinutes = totalMinutes.coerceAtLeast(0),
            printsFinished = printsFinished.coerceAtLeast(0),
        )
        persist()
    }

    fun reset() {
        _stats.value = PrintStats()
        persist()
    }

    private fun persist() {
        val snapshot = _stats.value
        scope.launch {
            runCatching {
                context.statsStore.edit { p ->
                    p[Keys.MINUTES] = snapshot.totalMinutes
                    p[Keys.FINISHED] = snapshot.printsFinished
                    p[Keys.LAST_JOB] = snapshot.lastJob
                    p[Keys.LAST_AT] = snapshot.lastAt
                }
            }
        }
    }

    private object Keys {
        val MINUTES = intPreferencesKey("total_minutes")
        val FINISHED = intPreferencesKey("prints_finished")
        val LAST_JOB = stringPreferencesKey("last_job")
        val LAST_AT = longPreferencesKey("last_at")
        val EVENTS = stringPreferencesKey("events")
    }
}
