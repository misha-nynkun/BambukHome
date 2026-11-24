package ru.layerpilot.data.printer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.layerpilot.data.model.AmsUnit
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.data.model.PrinterSnapshot
import ru.layerpilot.data.model.Spool
import kotlin.math.abs

enum class FanKind(val index: Int, val label: String) {
    PART(1, "Обдув модели"),
    AUX(2, "Доп. вентилятор"),
    CHAMBER(3, "Вентилятор камеры"),
}

data class PrintOptions(
    val profileId: String = "standard",
    val speedLevel: Int? = 2,
    val timelapse: Boolean = true,
    val bedLeveling: Boolean = true,
    val flowCali: Boolean = false,
    val vibrationCali: Boolean = false,
    val useAms: Boolean = true,
    val amsMapping: List<Int> = emptyList(),
)

class PrinterRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(PrinterSnapshot(phase = PrintPhase.OFFLINE, online = false))
    val snapshot: StateFlow<PrinterSnapshot> = _snapshot.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val connection: StateFlow<ConnectionStatus> = _connection.asStateFlow()

    private var config: PrinterConfig? = null
    private var mqtt: MqttPrinterClient? = null
    private var rawPrint = JSONObject()
    private var demo = false
    private var demoAms = true
    private var demoState = demoInitial(true)
    private var demoTicker: Job? = null
    private var retryJob: Job? = null
    private var generation = 0

    fun connect(config: PrinterConfig, demoMode: Boolean = false, demoWithAms: Boolean = true) {
        generation++
        stopEverything()
        this.config = config
        this.demo = demoMode
        this.demoAms = demoWithAms
        if (demoMode) {
            startDemo(demoWithAms)
            return
        }
        rawPrint = JSONObject()
        _snapshot.value = PrinterSnapshot(phase = PrintPhase.OFFLINE, online = false)
        _connection.value = ConnectionStatus.Connecting
        val token = generation
        val client = MqttPrinterClient(config, ::onMessage) { status ->
            _connection.value = status
            if (status is ConnectionStatus.Connected) {
                _connection.value = ConnectionStatus.Connected()
            } else if (status is ConnectionStatus.Error) {
                scheduleRetry(token)
            }
        }
        mqtt = client
        scope.launch { client.connect() }
    }

    fun disconnect() {
        generation++
        stopEverything()
        _connection.value = ConnectionStatus.Idle
        _snapshot.value = PrinterSnapshot(phase = PrintPhase.OFFLINE, online = false)
    }

    private fun stopEverything() {
        retryJob?.cancel()
        retryJob = null
        demoTicker?.cancel()
        demoTicker = null
        mqtt?.disconnect()
        mqtt = null
        demo = false
    }

    private fun scheduleRetry(token: Int) {
        if (demo) return
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(8_000)
            val cfg = config ?: return@launch
            if (token != generation || _connection.value is ConnectionStatus.Connected) return@launch
            mqtt?.disconnect()
            val client = MqttPrinterClient(cfg, ::onMessage) { status ->
                _connection.value = status
                if (status is ConnectionStatus.Error) scheduleRetry(token)
            }
            mqtt = client
            client.connect()
        }
    }

    // ---------------------------------------------------------------- MQTT

    private fun onMessage(root: JSONObject) {
        val print = root.optJSONObject("print") ?: return
        merge(rawPrint, print)
        val parsed = PrinterSnapshot.parse(JSONObject().put("print", rawPrint))
        _snapshot.value = parsed
        if (_connection.value !is ConnectionStatus.Connected) {
            _connection.value = ConnectionStatus.Connected()
        }
    }

    private fun merge(target: JSONObject, source: JSONObject) {
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = source.opt(key)
            if (value is JSONObject) {
                val existing = target.optJSONObject(key)
                if (existing != null) {
                    merge(existing, value)
                } else {
                    target.put(key, JSONObject(value.toString()))
                }
            } else {
                target.put(key, value)
            }
        }
    }

    private fun send(payload: JSONObject): Boolean = mqtt?.publish(payload) ?: false

    private fun printCommand(command: String, extra: JSONObject.() -> Unit = {}): Boolean =
        if (demo) true else send(JSONObject().put("print", JSONObject().put("command", command).apply(extra)))

    // ---------------------------------------------------------------- команды

    fun requestPushAll() {
        if (demo) return
        mqtt?.requestPushAll()
    }

    fun pause(): Boolean =
        if (demo) demoCommand { if (demoState.phase == PrintPhase.PRINTING) demoState = demoState.copy(phase = PrintPhase.PAUSED) }
        else printCommand("pause")

    fun resume(): Boolean =
        if (demo) demoCommand { if (demoState.phase == PrintPhase.PAUSED) demoState = demoState.copy(phase = PrintPhase.PRINTING) }
        else printCommand("resume")

    fun stop(): Boolean =
        if (demo) demoCommand {
            demoState = demoState.copy(phase = PrintPhase.IDLE, progress = 0, jobName = "", layer = 0, remainingMin = 0)
        } else printCommand("stop")

    fun setLight(on: Boolean): Boolean {
        if (demo) return demoCommand { demoState = demoState.copy(lightOn = on) }
        return send(JSONObject().put("system", JSONObject()
            .put("command", "ledctrl")
            .put("led_node", "chamber_light")
            .put("led_mode", if (on) "on" else "off")
            .put("led_on_time", 500)
            .put("led_off_time", 500)
            .put("loop_times", 0)
            .put("interval_time", 0)))
    }

    fun setSpeed(level: Int): Boolean {
        val lvl = level.coerceIn(1, 4)
        if (demo) return demoCommand {
            demoState = demoState.copy(
                speedLevel = lvl,
                speedPercent = PrinterSnapshot.SPEED_PERCENT[lvl] ?: 100,
            )
        }
        return printCommand("print_speed") { put("param", lvl.toString()) }
    }

    fun setNozzleTemp(temp: Int): Boolean {
        val t = temp.coerceIn(0, 300)
        if (demo) return demoCommand { demoState = demoState.copy(nozzleTarget = t.toFloat()) }
        return gcode("M104 S$t")
    }

    fun setBedTemp(temp: Int): Boolean {
        val t = temp.coerceIn(0, 100)
        if (demo) return demoCommand { demoState = demoState.copy(bedTarget = t.toFloat()) }
        return gcode("M140 S$t")
    }

    fun setFan(kind: FanKind, percent: Int): Boolean {
        val p = percent.coerceIn(0, 100)
        if (demo) return demoCommand {
            demoState = when (kind) {
                FanKind.PART -> demoState.copy(partFan = p)
                FanKind.AUX -> demoState.copy(auxFan = p)
                FanKind.CHAMBER -> demoState.copy(chamberFan = p)
            }
        }
        return gcode("M106 P${kind.index} S${p * 255 / 100}")
    }

    fun gcode(vararg lines: String): Boolean {
        if (demo) return true
        val text = lines.joinToString("\n", postfix = "\n")
        return printCommand("gcode_line") { put("param", text) }
    }

    fun home(): Boolean {
        if (demo) return true
        return gcode("G28")
    }

    fun move(axis: String, mm: Int, feed: Int = if (axis == "Z") 600 else 3000): Boolean {
        if (demo) return true
        return gcode("G91", "G1 $axis$mm F$feed", "G90")
    }

    fun extrude(mm: Int): Boolean {
        if (demo) return true
        return gcode("M83", "G1 E$mm F300")
    }

    fun loadSpool(target: Int, currTemp: Int, targetTemp: Int): Boolean {
        if (demo) return demoCommand {
            demoState = demoState.copy(activeSlot = target, targetSlot = target)
        }
        return printCommand("ams_change_filament") {
            put("target", target)
            put("curr_temp", currTemp)
            put("tar_temp", targetTemp)
        }
    }

    fun unloadSpool(currTemp: Int = 210): Boolean {
        if (demo) return demoCommand { demoState = demoState.copy(activeSlot = 255, targetSlot = 255) }
        return printCommand("ams_change_filament") {
            put("target", 255)
            put("curr_temp", currTemp)
            put("tar_temp", currTemp)
        }
    }

    fun amsControl(param: String): Boolean = printCommand("ams_control") { put("param", param) }

    fun skipObjects(ids: List<Int>): Boolean {
        if (ids.isEmpty()) return false
        if (demo) return demoCommand {
            demoState = demoState.copy(skippedObjects = (demoState.skippedObjects + ids).distinct())
        }
        return printCommand("skip_objects") {
            put("obj_list", org.json.JSONArray(ids))
        }
    }

    fun clearPrintError(): Boolean {
        val state = _snapshot.value
        if (demo) return demoCommand { demoState = demoState.copy(errors = emptyList(), printError = 0) }
        return printCommand("clean_print_error") {
            put("subtask_id", state.subtaskId)
            put("print_error", 0)
        }
    }

    fun startPrint(path: String, options: PrintOptions): Boolean {
        val name = path.substringAfterLast('/')
        val stem = name.replace(Regex("\\.(gcode\\.3mf|3mf|gcode)$", RegexOption.IGNORE_CASE), "")
        if (demo) return demoCommand {
            demoState = demoState.copy(
                phase = PrintPhase.PRINTING,
                jobName = stem.ifBlank { "Новая модель" },
                progress = 1,
                layer = 0,
                remainingMin = 185,
                timelapseEnabled = options.timelapse,
                speedLevel = options.speedLevel ?: demoState.speedLevel,
                speedPercent = PrinterSnapshot.SPEED_PERCENT[options.speedLevel ?: demoState.speedLevel] ?: 100,
            )
        }
        val sent = send(JSONObject().put("print", JSONObject()
            .put("command", "project_file")
            .put("param", "Metadata/plate_1.gcode")
            .put("url", "file:///sdcard$path")
            .put("subtask_name", stem)
            .put("project_id", "0")
            .put("profile_id", "0")
            .put("task_id", "0")
            .put("subtask_id", "0")
            .put("md5", "")
            .put("bed_type", "auto")
            .put("timelapse", options.timelapse)
            .put("bed_leveling", options.bedLeveling)
            .put("flow_cali", options.flowCali)
            .put("vibration_cali", options.vibrationCali)
            .put("layer_inspect", false)
            .put("use_ams", options.useAms)
            .put("ams_mapping", org.json.JSONArray(options.amsMapping))))
        if (sent) {
            options.speedLevel?.let { level ->
                scope.launch {
                    delay(3_500)
                    setSpeed(level)
                }
            }
        }
        return sent
    }

    // ---------------------------------------------------------------- демо

    fun startDemo(withAms: Boolean = true) {
        generation++
        stopEverything()
        demo = true
        demoAms = withAms
        demoState = demoInitial(withAms)
        config = config ?: PrinterConfig("demo", "", "demo", ru.layerpilot.data.model.PrinterModel.P1S, "Bambu P1S")
        _connection.value = ConnectionStatus.Connected(demo = true)
        _snapshot.value = demoState
        demoTicker?.cancel()
        demoTicker = scope.launch {
            while (true) {
                delay(1000)
                demoState = tickDemo(demoState)
                _snapshot.value = demoState
            }
        }
    }

    fun isDemo(): Boolean = demo

    private fun demoCommand(block: () -> Unit): Boolean {
        block()
        _snapshot.value = demoState
        return true
    }

    private fun tickDemo(s: PrinterSnapshot): PrinterSnapshot {
        if (s.phase == PrintPhase.PRINTING) {
            val progress = (s.progress + 0.06).coerceAtMost(100.0).toInt()
            val finished = progress >= 100
            val layer = if (s.totalLayers > 0) (s.totalLayers * progress / 100).coerceAtLeast(1) else 0
            return s.copy(
                progress = progress,
                layer = layer,
                phase = if (finished) PrintPhase.FINISHED else PrintPhase.PRINTING,
                remainingMin = if (finished) 0 else ((100 - progress) * 1.8).toInt() + 1,
                nozzle = approach(s.nozzle, s.nozzleTarget, 1.5f),
                bed = approach(s.bed, s.bedTarget, 0.8f),
                chamber = 32f + ((System.currentTimeMillis() / 5000) % 3).toFloat(),
                partFan = if (s.nozzle > 200) 100 else s.partFan,
            )
        }
        return s.copy(
            nozzle = approach(s.nozzle, 28f, 1.2f),
            bed = approach(s.bed, 24f, 0.6f),
        )
    }

    private fun approach(value: Float, target: Float, step: Float): Float {
        if (abs(target - value) < step) return target
        return value + if (target > value) step else -step
    }

    private fun demoInitial(withAms: Boolean): PrinterSnapshot {
        val ams = if (withAms) listOf(
            AmsUnit(0, 2, 26f, listOf(
                Spool(0, "PLA", 0xFF78DE84.toInt(), 82, 190, 230, "Bambu", "Matte", active = true),
                Spool(1, "PETG", 0xFF2B2B2B.toInt(), 45, 220, 260, "Bambu", "Basic"),
                Spool(2, "PLA", 0xFFF1F1F1.toInt(), 12, 190, 230, "Bambu", "Basic"),
                Spool(3, "ABS", 0xFFFF7A45.toInt(), -1, 240, 280, "Bambu", "HF"),
            ), humidityRaw = 24),
        ) else emptyList()
        return PrinterSnapshot(
            phase = PrintPhase.PRINTING,
            gcodeState = "RUNNING",
            stage = 0,
            stageLabel = "Печать",
            progress = 63,
            remainingMin = 74,
            layer = 148,
            totalLayers = 235,
            jobName = "Ваза hex",
            nozzle = 218f,
            nozzleTarget = 220f,
            bed = 55f,
            bedTarget = 55f,
            chamber = 33f,
            partFan = 90,
            auxFan = 40,
            chamberFan = 0,
            speedLevel = 2,
            speedPercent = 100,
            lightOn = true,
            errors = emptyList(),
            wifiDbm = -52,
            firmware = "01.08.00.00",
            nozzleDiameter = 0.4f,
            machine = "P1S",
            amsUnits = ams,
            externalSpool = Spool(254, "PLA", 0xFF7CB8FF.toInt(), 30, 190, 230, "Bambu", "Basic"),
            activeSlot = if (withAms) 0 else 254,
            targetSlot = if (withAms) 0 else 254,
            timelapseEnabled = true,
            online = true,
        )
    }
}
