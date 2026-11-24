package ru.layerpilot.clawd

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ClawdEpisode(val ts: Long, val kind: String, val detail: String)
data class ClawdNote(val text: String, val weight: Int, val ts: Long)

/**
 * Локальная память Пико. Отдельная на каждого пользователя (owner/guest).
 * Никаких секретов: только имя, события, экраны, привычки и короткие факты.
 */
class ClawdMemoryStore private constructor(
    context: Context,
    private val userName: String,
    displayName: String,
) {
    private val prefs = context.getSharedPreferences("pet_$userName", Context.MODE_PRIVATE)
    private val defaultDisplayName = displayName
    var displayName: String = displayName
    var created: Long = now()
    var sessions: Int = 0
    var lastSeen: Long = 0
    var prevSeen: Long = 0
    var bond: Float = 10f
    var hours: IntArray = IntArray(24)
    var screens: MutableMap<String, Int> = mutableMapOf()
    var stats: MutableMap<String, Int> = mutableMapOf()
    var days: MutableList<String> = mutableListOf()
    var notes: MutableList<ClawdNote> = mutableListOf()
    var episodes: MutableList<ClawdEpisode> = mutableListOf()
    var said: MutableList<String> = mutableListOf()
    var asked: MutableMap<String, Long> = mutableMapOf()
    var lastDailyKey: String = ""
    var lastTipKey: String = ""
    var factCounter: Long = 0L

    init {
        load()
    }

    companion object {
        private val cache = mutableMapOf<String, ClawdMemoryStore>()

        /** Один экземпляр на профиль: настройки и оверлей видят одну память. */
        fun get(context: Context, userName: String, displayName: String): ClawdMemoryStore =
            synchronized(cache) {
                cache.getOrPut(userName) {
                    ClawdMemoryStore(context.applicationContext, userName, displayName)
                }
            }
    }

    fun load() {
        val raw = prefs.getString("json", null) ?: return
        runCatching {
            val o = JSONObject(raw)
            displayName = o.optString("display", displayName)
            created = o.optLong("created", created)
            sessions = o.optInt("sessions", 0)
            lastSeen = o.optLong("last_seen", 0)
            prevSeen = o.optLong("prev_seen", 0)
            bond = o.optDouble("bond", 10.0).toFloat()
            val h = o.optJSONArray("hours")
            if (h != null) for (i in 0 until minOf(24, h.length())) hours[i] = h.optInt(i, 0)
            o.optJSONObject("screens")?.let { s ->
                s.keys().forEach { k -> screens[k] = s.optInt(k, 0) }
            }
            o.optJSONObject("stats")?.let { s ->
                s.keys().forEach { k -> stats[k] = s.optInt(k, 0) }
            }
            o.optJSONArray("days")?.let { a -> for (i in 0 until a.length()) days.add(a.optString(i)) }
            o.optJSONArray("notes")?.let { a ->
                for (i in 0 until a.length()) {
                    val n = a.optJSONObject(i) ?: continue
                    notes.add(ClawdNote(n.optString("text"), n.optInt("weight", 1), n.optLong("ts", 0)))
                }
            }
            o.optJSONArray("episodes")?.let { a ->
                for (i in 0 until a.length()) {
                    val n = a.optJSONObject(i) ?: continue
                    episodes.add(ClawdEpisode(n.optLong("ts", 0), n.optString("kind"), n.optString("detail")))
                }
            }
            o.optJSONArray("said")?.let { a -> for (i in 0 until a.length()) said.add(a.optString(i)) }
            o.optJSONObject("asked")?.let { a ->
                a.keys().forEach { k -> asked[k] = a.optLong(k, 0) }
            }
            lastDailyKey = o.optString("last_daily", "")
            lastTipKey = o.optString("last_tip", "")
            factCounter = o.optLong("fact_counter", 0L)
        }
    }

    fun save() {
        runCatching {
            val o = JSONObject()
                .put("display", displayName)
                .put("created", created)
                .put("sessions", sessions)
                .put("last_seen", lastSeen)
                .put("prev_seen", prevSeen)
                .put("bond", bond.toDouble())
                .put("hours", JSONArray(hours.toList()))
                .put("screens", JSONObject(screens as Map<*, *>))
                .put("stats", JSONObject(stats as Map<*, *>))
                .put("days", JSONArray(days))
                .put("notes", JSONArray().apply {
                    notes.takeLast(30).forEach {
                        put(JSONObject().put("text", it.text).put("weight", it.weight).put("ts", it.ts))
                    }
                })
                .put("episodes", JSONArray().apply {
                    episodes.takeLast(80).forEach {
                        put(JSONObject().put("ts", it.ts).put("kind", it.kind).put("detail", it.detail))
                    }
                })
                .put("said", JSONArray(said.takeLast(12)))
                .put("asked", JSONObject(asked as Map<*, *>))
                .put("last_daily", lastDailyKey)
                .put("last_tip", lastTipKey)
                .put("fact_counter", factCounter)
            prefs.edit().putString("json", o.toString()).apply()
        }
    }

    fun sessionStart(): Long? {
        val t = now()
        val away = if (lastSeen > 0) t - lastSeen else null
        if (created == 0L) created = t
        if (away == null || away > 600) {
            sessions++
            prevSeen = lastSeen
            if (away != null && away > 2 * 86400) {
                bond = (bond - minOf(8f, (away / 86400f - 1f) * 1.5f)).coerceAtLeast(5f)
            }
        }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t * 1000))
        if (day !in days) days.add(day)
        lastSeen = t
        save()
        return if (away != null && away > 600) away else null
    }

    fun record(kind: String, detail: String = "", deltaBond: Float = 0f) {
        stats[kind] = (stats[kind] ?: 0) + 1
        if (kind in setOf("pet", "tap", "jump", "annoyed", "drag", "throw", "shake", "clean", "spit", "torrent_done", "offline", "updates")) {
            hours[(System.currentTimeMillis() / 3_600_000 % 24).toInt()]++
        }
        if (deltaBond != 0f) {
            bond = if (deltaBond > 0) bond + deltaBond * maxOf(0.05f, 1f - bond / 110f)
            else bond + deltaBond
            bond = bond.coerceIn(0f, 100f)
        }
        if (detail.length <= 40) {
            val last = episodes.lastOrNull()
            if (last != null && last.kind == kind && last.detail == detail &&
                now() - last.ts < 90) {
                episodes[episodes.lastIndex] = last.copy(ts = now())
            } else {
                episodes.add(ClawdEpisode(now(), kind, detail))
                while (episodes.size > 80) episodes.removeAt(0)
            }
        }
        save()
    }

    fun screenVisit(route: String) {
        screens[route] = (screens[route] ?: 0) + 1
        save()
    }

    fun updateDisplayName(value: String) {
        val clean = value.trim().take(24)
        if (clean == displayName) return
        displayName = clean
        save()
    }

    fun addNote(text: String) {
        val clean = text.trim().take(90)
        if (clean.length < 4) return
        if (notes.any { it.text.equals(clean, true) }) return
        notes.add(ClawdNote(clean, 1, now()))
        while (notes.size > 30) notes.removeAt(0)
        save()
    }

    fun addSaid(text: String) {
        said.add(text.take(80))
        while (said.size > 12) said.removeAt(0)
        save()
    }

    fun markDaily(key: String) {
        lastDailyKey = key
        save()
    }

    fun markTip(key: String) {
        lastTipKey = key
        save()
    }

    /** Счётчик колоды фактов: каждая нажатие даёт новую комбинацию. */
    fun nextFactCounter(): Long {
        val value = factCounter
        factCounter = if (factCounter == Long.MAX_VALUE) 0L else factCounter + 1
        save()
        return value
    }

    fun markAsked(id: String) {
        asked[id] = now()
        save()
    }

    fun askedRecently(id: String, seconds: Long): Boolean =
        now() - (asked[id] ?: 0) < seconds

    fun bondLabel(): String = when {
        bond < 15 -> "знакомый"
        bond < 35 -> "приятель"
        bond < 60 -> "друг"
        bond < 85 -> "близкий друг"
        else -> "лучший друг"
    }

    fun habits(): List<String> {
        val out = mutableListOf<String>()
        val total = hours.sum()
        if (total >= 25) {
            val buckets = listOf("ночью" to 0..5, "утром" to 6..11, "днём" to 12..17, "вечером" to 18..23)
            val best = buckets.maxByOrNull { b -> b.second.sumOf { hours[it] } }
            if (best != null && best.second.sumOf { hours[it] } / total.toFloat() >= 0.45f) {
                out.add("чаще заходит ${best.first}")
            }
        }
        val topScreen = screens.maxByOrNull { it.value }
        if (topScreen != null && topScreen.value >= 5) out.add("чаще всего открывает ${screenTitle(topScreen.key)}")
        if ((stats["pet"] ?: 0) >= 10) out.add("любит гладить Пико")
        if ((stats["throw"] ?: 0) >= 10) out.add("любит бросать Пико")
        if ((stats["offline"] ?: 0) >= 3) out.add("часто ловит обрывы связи")
        return out
    }

    fun daysTogether(): Int = days.size.coerceAtLeast(1)

    fun recentLines(limit: Int = 6): List<String> =
        episodes.takeLast(limit).asReversed().map { "${relTime(now() - it.ts)} — ${episodeText(it)}" }

    fun episodeText(e: ClawdEpisode): String = when (e.kind) {
        "pet" -> "Пико погладили"
        "tap" -> "Пико потыкали"
        "jump" -> "Пико подпрыгнул"
        "throw" -> "Пико бросили"
        "offline" -> "пропадала связь с Pi"
        "online" -> "связь с Pi вернулась"
        "torrent_done" -> "торрент скачался"
        "hot" -> "температура поднималась"
        "disk" -> "место на диске кончалось"
        "updates" -> "появились обновления"
        "news" -> "приходило важное сообщение"
        "clean" -> "убрали за Пико"
        "eat" -> "Пико что-то съел"
        else -> e.detail.ifBlank { e.kind }
    }

    fun forget() {
        prefs.edit().clear().apply()
        displayName = defaultDisplayName
        created = now()
        sessions = 0
        lastSeen = 0
        prevSeen = 0
        bond = 10f
        hours = IntArray(24)
        screens.clear()
        stats.clear()
        days.clear()
        notes.clear()
        episodes.clear()
        said.clear()
        asked.clear()
        lastDailyKey = ""
        lastTipKey = ""
        save()
    }
}

private fun now(): Long = System.currentTimeMillis() / 1000

private fun relTime(sec: Long): String = when {
    sec < 60 -> "только что"
    sec < 3600 -> "${sec / 60} мин назад"
    sec < 86400 -> "${sec / 3600} ч назад"
    sec < 172800 -> "вчера"
    else -> "${sec / 86400} дн назад"
}

private fun screenTitle(route: String): String = when (route) {
    "dashboard" -> "рабочий стол"
    "status" -> "статус"
    "disks" -> "диски"
    "services" -> "службы"
    "network" -> "сеть"
    "updates" -> "обновления"
    "settings" -> "настройки"
    "news" -> "сообщения"
    else -> route
}
