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

private val Context.statsStore: DataStore<Preferences> by preferencesDataStore(name = "print_stats")

data class PrintStats(
    val totalMinutes: Int = 0,
    val printsFinished: Int = 0,
    val lastJob: String = "",
    val lastAt: Long = 0,
) {
    val totalHours: Float get() = totalMinutes / 60f
}

/**
 * Локальная статистика: сколько печатали и сколько раз завершили.
 * Принтер не отдаёт общий счётчик часов по MQTT, поэтому считаем сами.
 */
class StatsRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _stats = MutableStateFlow(PrintStats())
    val stats: StateFlow<PrintStats> = _stats

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
    }
}
