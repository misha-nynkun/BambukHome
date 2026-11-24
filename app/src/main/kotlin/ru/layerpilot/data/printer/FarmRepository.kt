package ru.layerpilot.data.printer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.layerpilot.data.model.PrinterSnapshot
import ru.layerpilot.data.settings.PrinterProfile

data class FarmPrinter(
    val profile: PrinterProfile,
    val snapshot: PrinterSnapshot = PrinterSnapshot(),
    val connection: ConnectionStatus = ConnectionStatus.Idle,
)

/**
 * Наблюдение за фермой. Держим не больше 8 MQTT-подключений одновременно,
 * чтобы не убить батарею и роутер при 200 профилях.
 */
class FarmRepository(private val limit: Int = 8) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repositories = mutableMapOf<String, PrinterRepository>()
    private val jobs = mutableListOf<Job>()
    private val _states = MutableStateFlow<Map<String, FarmPrinter>>(emptyMap())
    val states: StateFlow<Map<String, FarmPrinter>> = _states.asStateFlow()
    private val _monitored = MutableStateFlow(0)
    val monitored: StateFlow<Int> = _monitored.asStateFlow()

    fun start(profiles: List<PrinterProfile>) {
        stop()
        val chosen = profiles.filter { it.isConfigured }.take(limit)
        _monitored.value = chosen.size
        _states.value = chosen.associate { it.id to FarmPrinter(it) }
        chosen.forEach { profile ->
            val repo = PrinterRepository()
            repositories[profile.id] = repo
            val config = PrinterConfig(
                profile.host, profile.accessCode, profile.serial, profile.model, profile.name,
            )
            repo.connect(config, profile.demo, profile.demoAms)
            jobs += scope.launch {
                repo.snapshot.collect { snap ->
                    _states.value = _states.value.toMutableMap().apply {
                        val old = this[profile.id] ?: FarmPrinter(profile)
                        this[profile.id] = old.copy(snapshot = snap)
                    }
                }
            }
            jobs += scope.launch {
                repo.connection.collect { connection ->
                    _states.value = _states.value.toMutableMap().apply {
                        val old = this[profile.id] ?: FarmPrinter(profile)
                        this[profile.id] = old.copy(connection = connection)
                    }
                }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        repositories.values.forEach { it.disconnect() }
        repositories.clear()
        _monitored.value = 0
        _states.value = emptyMap()
    }

    fun command(id: String, action: String) {
        val repo = repositories[id] ?: return
        when (action) {
            "pause" -> repo.pause()
            "resume" -> repo.resume()
            "stop" -> repo.stop()
            "refresh" -> repo.requestPushAll()
            "light_on" -> repo.setLight(true)
            "light_off" -> repo.setLight(false)
        }
    }
}
