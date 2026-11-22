package ru.layerpilot.data.model

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** Модели, которые понимает локальный протокол. Набор команд у них общий. */
enum class PrinterModel(
    val label: String,
    val hasCamera: Boolean,
    val hasChamber: Boolean,
    val hasAmsOption: Boolean,
    val buildVolume: String,
) {
    X1("Bambu X1", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "256 × 256 × 256"),
    X1C("Bambu X1 Carbon", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "256 × 256 × 256"),
    X1E("Bambu X1E", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "256 × 256 × 256"),
    P1P("Bambu P1P", hasCamera = true, hasChamber = false, hasAmsOption = true, buildVolume = "256 × 256 × 256"),
    P1S("Bambu P1S", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "256 × 256 × 256"),
    P2S("Bambu P2S", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "256 × 256 × 256"),
    A1("Bambu A1", hasCamera = false, hasChamber = false, hasAmsOption = true, buildVolume = "256 × 256 × 256"),
    A1MINI("Bambu A1 mini", hasCamera = false, hasChamber = false, hasAmsOption = true, buildVolume = "180 × 180 × 180"),
    A2L("Bambu A2L", hasCamera = false, hasChamber = false, hasAmsOption = true, buildVolume = "—"),
    H2D("Bambu H2D", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "325 × 320 × 325"),
    H2DPRO("Bambu H2D Pro", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "325 × 320 × 325"),
    H2S("Bambu H2S", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "—"),
    H2C("Bambu H2C", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "—"),
    X2D("Bambu X2D", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "—"),
    OTHER("Другой принтер", hasCamera = true, hasChamber = true, hasAmsOption = true, buildVolume = "—"),
    ;

    companion object {
        fun fromName(name: String?): PrinterModel {
            val migrated = when (name) {
                "X1CARBON" -> "X1C"
                "H2D_PRO" -> "H2DPRO"
                "A1MINI" -> "A1MINI"
                else -> name
            }
            return entries.firstOrNull { it.name == migrated } ?: P1S
        }

        val withCamera = entries.filter { it.hasCamera }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Состояние печати по gcode_state. */
enum class PrintPhase { OFFLINE, IDLE, PREPARE, PRINTING, PAUSED, FINISHED, FAILED }

data class HmsError(val code: String, val text: String)

data class Spool(
    val slot: Int,
    val type: String,
    val color: Int?,
    val remain: Int,
    val minTemp: Int,
    val maxTemp: Int,
    val brand: String = "",
    val name: String = "",
    val active: Boolean = false,
    val exists: Boolean = true,
) {
    val loadTemp: Int
        get() = when {
            minTemp in 150..300 && maxTemp in 150..300 && maxTemp >= minTemp -> (minTemp + maxTemp) / 2
            type.contains("PETG", true) -> 250
            type.contains("ABS", true) || type.contains("ASA", true) -> 270
            else -> 220
        }

    val colorCompose: androidx.compose.ui.graphics.Color
        get() = androidx.compose.ui.graphics.Color(color ?: 0xFF3A4A3C.toInt())
}

data class AmsUnit(
    val id: Int,
    val humidity: Int,
    val temp: Float,
    val trays: List<Spool>,
) {
    val humidityLabel: String
        get() = if (humidity in 1..5) "$humidity/5" else "—"
}

data class PrinterSnapshot(
    val phase: PrintPhase = PrintPhase.OFFLINE,
    val gcodeState: String = "IDLE",
    val stage: Int = -1,
    val stageLabel: String = "",
    val progress: Int = 0,
    val remainingMin: Int = 0,
    val layer: Int = 0,
    val totalLayers: Int = 0,
    val jobName: String = "",
    val nozzle: Float = 0f,
    val nozzleTarget: Float = 0f,
    val bed: Float = 0f,
    val bedTarget: Float = 0f,
    val chamber: Float? = null,
    val partFan: Int = 0,
    val auxFan: Int = 0,
    val chamberFan: Int = 0,
    val speedLevel: Int = 2,
    val speedPercent: Int = 100,
    val lightOn: Boolean? = null,
    val errors: List<HmsError> = emptyList(),
    val wifiDbm: Int? = null,
    val firmware: String = "",
    val nozzleDiameter: Float = 0.4f,
    val machine: String = "",
    val amsUnits: List<AmsUnit> = emptyList(),
    val externalSpool: Spool? = null,
    val activeSlot: Int = 255,
    val targetSlot: Int = 255,
    val skippedObjects: List<Int> = emptyList(),
    val timelapseEnabled: Boolean = false,
    val printError: Int = 0,
    val subtaskId: String = "0",
    val online: Boolean = true,
    /** Номер пластины из gcode_file: Metadata/plate_2.gcode → 2. */
    val plate: Int = 1,
) {
    val hasAms: Boolean get() = amsUnits.any { it.trays.any { t -> t.exists } }

    val activeSpool: Spool?
        get() {
            if (activeSlot == 254) return externalSpool
            return amsUnits.flatMap { it.trays }.firstOrNull { it.slot == activeSlot && it.exists }
        }

    val allTrays: List<Spool>
        get() = amsUnits.flatMap { it.trays }

    val usedFilaments: List<Spool>
        get() = allTrays.filter { it.exists }

    val remainingLabel: String
        get() = formatMinutes(remainingMin)

    val layerLabel: String
        get() = if (totalLayers > 0) "$layer / $totalLayers" else "—"

    val speedName: String
        get() = SPEED_NAMES[speedLevel] ?: "Стандарт"

    companion object {
        val SPEED_NAMES = mapOf(1 to "Тихий", 2 to "Стандарт", 3 to "Спорт", 4 to "Безумный")
        val SPEED_SHORT = mapOf(1 to "Тихий", 2 to "Стандарт", 3 to "Спорт", 4 to "Безум")
        val SPEED_PERCENT = mapOf(1 to 50, 2 to 100, 3 to 124, 4 to 166)

        val STAGES = mapOf(
            0 to "Печать", 1 to "Выравнивание стола", 2 to "Нагрев стола", 3 to "Проверка механики XY",
            4 to "Смена прутка", 5 to "Пауза M400", 6 to "Пауза: кончился пруток", 7 to "Нагрев сопла",
            8 to "Калибровка экструзии", 9 to "Сканирование стола", 10 to "Проверка первого слоя",
            11 to "Определение пластины", 12 to "Калибровка лидара", 13 to "Парковка головы",
            14 to "Очистка сопла", 15 to "Проверка температуры", 16 to "Пауза пользователем",
            17 to "Пауза: открыта крышка", 18 to "Калибровка лидара", 19 to "Калибровка потока",
            20 to "Сбой температуры сопла", 21 to "Сбой температуры стола", 22 to "Выгрузка прутка",
            23 to "Пропуск шагов", 24 to "Загрузка прутка", 25 to "Калибровка шума моторов",
            26 to "Пауза: потерян AMS", 27 to "Слабый вентилятор радиатора", 28 to "Ошибка температуры камеры",
            29 to "Охлаждение камеры", 30 to "Пауза из G-кода", 31 to "Проверка шума моторов",
            32 to "Сопло облеплено пластиком", 33 to "Ошибка резака", 34 to "Ошибка первого слоя",
            35 to "Засор сопла",
        )

        val HMS_TEXT = mapOf(
            "07FF-2000-0002-0001" to "Кончился пруток с внешней катушки",
            "07FF-2000-0002-0002" to "Внешний пруток не вставлен",
            "07FF-2000-0002-0004" to "Вытащи внешний пруток из экструдера",
            "07FF-7000-0002-0003" to "Датчик экструдера: кончился пруток",
            "0300-4000-0002-0001" to "Датчик сопла: пластик не идёт (возможен засор)",
        )

        fun parse(raw: JSONObject): PrinterSnapshot {
            val p = raw.optJSONObject("print") ?: raw
            val state = p.optString("gcode_state", "IDLE").uppercase()
            val stage = p.optIntSafe("stg_cur", -1)
            val ams = parseAms(p.optJSONObject("ams"))
            val active = p.optJSONObject("ams")?.optString("tray_now", "255")?.toIntOrNull() ?: 255
            val target = p.optJSONObject("ams")?.optString("tray_tar", "255")?.toIntOrNull() ?: 255
            val errors = parseHms(p.optJSONArray("hms"))
            val external = parseExternal(p.optJSONObject("vt_tray"))
            val light = parseLight(p.optJSONArray("lights_report"))
            val skipped = mutableListOf<Int>()
            p.optJSONArray("s_obj")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optInt(i, -1).takeIf { it >= 0 }?.let(skipped::add)
                }
            }
            val phase = when (state) {
                "RUNNING" -> PrintPhase.PRINTING
                "PAUSE" -> PrintPhase.PAUSED
                "PREPARE", "SLICING", "INIT" -> PrintPhase.PREPARE
                "FINISH" -> PrintPhase.FINISHED
                "FAILED" -> PrintPhase.FAILED
                else -> PrintPhase.IDLE
            }
            val timelapse = p.optJSONObject("ipcam")?.optString("timelapse", "")
                ?.equals("enable", true) == true
            val gcodeFile = p.optString("gcode_file")
            val name = (p.optString("subtask_name").ifBlank { gcodeFile })
                .substringAfterLast('/')
                .replace(Regex("\\.(gcode\\.3mf|3mf|gcode)$", RegexOption.IGNORE_CASE), "")
            val plate = Regex("plate[_-]?(\\d+)\\.gcode", RegexOption.IGNORE_CASE)
                .find(gcodeFile)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val wifi = p.optString("wifi_signal", "").filter { it.isDigit() || it == '-' }.toIntOrNull()
            return PrinterSnapshot(
                phase = phase,
                gcodeState = state,
                stage = stage,
                stageLabel = STAGES[stage].orEmpty(),
                progress = p.optIntSafe("mc_percent", 0).coerceIn(0, 100),
                remainingMin = p.optIntSafe("mc_remaining_time", 0).coerceAtLeast(0),
                layer = p.optIntSafe("layer_num", 0),
                totalLayers = p.optIntSafe("total_layer_num", 0),
                jobName = name,
                nozzle = p.optFloat("nozzle_temper"),
                nozzleTarget = p.optFloat("nozzle_target_temper"),
                bed = p.optFloat("bed_temper"),
                bedTarget = p.optFloat("bed_target_temper"),
                chamber = p.optString("chamber_temper").toFloatOrNull(),
                partFan = fanPercent(p.optIntSafe("cooling_fan_speed", 0)),
                auxFan = fanPercent(p.optIntSafe("big_fan1_speed", 0)),
                chamberFan = fanPercent(p.optIntSafe("big_fan2_speed", 0)),
                speedLevel = p.optIntSafe("spd_lvl", 2).coerceIn(1, 4),
                speedPercent = p.optIntSafe("spd_mag", SPEED_PERCENT[p.optIntSafe("spd_lvl", 2)] ?: 100),
                lightOn = light,
                errors = errors,
                wifiDbm = wifi,
                firmware = p.optJSONObject("upgrade_state")?.optString("cur_ver").orEmpty()
                    .ifBlank { p.optString("firmware") },
                nozzleDiameter = p.optFloat("nozzle_diameter", 0.4f),
                machine = p.optString("machine"),
                amsUnits = ams,
                externalSpool = external,
                activeSlot = active,
                targetSlot = target,
                skippedObjects = skipped,
                timelapseEnabled = timelapse,
                printError = p.optIntSafe("print_error", 0),
                subtaskId = p.optString("subtask_id", "0"),
                online = true,
                plate = plate,
            )
        }

        private fun parseAms(ams: JSONObject?): List<AmsUnit> {
            val units = ams?.optJSONArray("ams") ?: return emptyList()
            val out = ArrayList<AmsUnit>()
            for (i in 0 until units.length()) {
                val u = units.optJSONObject(i) ?: continue
                val unitId = u.optIntSafe("id", i)
                val trays = ArrayList<Spool>()
                val list = u.optJSONArray("tray")
                if (list != null) {
                    for (j in 0 until list.length()) {
                        val t = list.optJSONObject(j) ?: continue
                        val slot = unitId * 4 + j
                        trays += parseSpool(t, slot, t.optIntSafe("id", j))
                    }
                }
                out += AmsUnit(
                    id = unitId,
                    humidity = u.optString("humidity").toIntOrNull() ?: -1,
                    temp = u.optString("temp").toFloatOrNull() ?: 0f,
                    trays = trays,
                )
            }
            return out
        }

        private fun parseSpool(t: JSONObject, slot: Int, trayId: Int): Spool {
            val type = t.optString("tray_type").trim()
            val exists = type.isNotBlank() && t.optString("tray_color").isNotBlank()
            return Spool(
                slot = if (slot >= 0) slot else trayId,
                type = type.ifBlank { "—" },
                color = parseColor(t.optString("tray_color")),
                remain = t.optString("remain").toIntOrNull() ?: -1,
                minTemp = t.optString("nozzle_temp_min").toIntOrNull() ?: 0,
                maxTemp = t.optString("nozzle_temp_max").toIntOrNull() ?: 0,
                brand = t.optString("tray_sub_brands").substringBefore(';'),
                name = t.optString("tray_id_name"),
                exists = exists,
            )
        }

        private fun parseExternal(t: JSONObject?): Spool? {
            t ?: return null
            val type = t.optString("tray_type").trim()
            if (type.isBlank()) return null
            return Spool(
                slot = 254,
                type = type,
                color = parseColor(t.optString("tray_color")),
                remain = t.optString("remain").toIntOrNull() ?: -1,
                minTemp = t.optString("nozzle_temp_min").toIntOrNull() ?: 0,
                maxTemp = t.optString("nozzle_temp_max").toIntOrNull() ?: 0,
                brand = t.optString("tray_sub_brands").substringBefore(';'),
                name = t.optString("tray_id_name"),
            )
        }

        private fun parseHms(list: JSONArray?): List<HmsError> {
            if (list == null) return emptyList()
            val out = ArrayList<HmsError>()
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val attr = item.optLong("attr", 0)
                val code = item.optLong("code", 0)
                val text = item.optString("msg")
                val key = "%04X-%04X-%04X-%04X".format(
                    (attr shr 16) and 0xFFFF, attr and 0xFFFF,
                    (code shr 16) and 0xFFFF, code and 0xFFFF,
                )
                out += HmsError(key, HMS_TEXT[key] ?: text.ifBlank { "Код $key" })
            }
            return out
        }

        private fun parseLight(list: JSONArray?): Boolean? {
            if (list == null) return null
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                if (item.optString("node") == "chamber_light") {
                    return item.optString("mode") == "on"
                }
            }
            return null
        }

        fun parseColor(raw: String?): Int? {
            val s = raw?.trim()?.removePrefix("#") ?: return null
            if (s.length < 6) return null
            if (s == "00000000" || s == "000000") return null
            return try {
                val rgb = s.take(6).toLong(16).toInt()
                0xFF000000.toInt() or rgb
            } catch (_: NumberFormatException) {
                null
            }
        }

        private fun fanPercent(level: Int): Int = ((level.coerceIn(0, 15) / 15f) * 100).toInt()

        fun formatMinutes(min: Int): String {
            if (min <= 0) return "—"
            val h = min / 60
            val m = min % 60
            return when {
                h == 0 -> "$m мин"
                m == 0 -> "$h ч"
                else -> "$h ч $m мин"
            }
        }

        fun etaText(min: Int): String {
            if (min <= 0) return "—"
            val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            return fmt.format(java.util.Date(System.currentTimeMillis() + min * 60_000L))
        }
    }
}

fun JSONObject.optIntSafe(key: String, fallback: Int = 0): Int {
    val v = opt(key) ?: return fallback
    return when (v) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: fallback
        else -> fallback
    }
}

fun JSONObject.optFloat(key: String, fallback: Float = 0f): Float {
    val v = opt(key) ?: return fallback
    return when (v) {
        is Number -> v.toFloat()
        is String -> v.toFloatOrNull() ?: fallback
        else -> fallback
    }
}

fun Int.toHexColor(): String = String.format("#%06X", 0xFFFFFF and this)

fun ColorLuminance(color: Int): Float {
    val r = Color.red(color) / 255f
    val g = Color.green(color) / 255f
    val b = Color.blue(color) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
