package ru.layerpilot.clawd

import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.data.model.PrinterSnapshot
import kotlin.math.roundToInt

enum class ClawdMood { NEUTRAL, HAPPY, LOVE, LAUGH, EXCITED, SURPRISED, SCARED, ANGRY, SAD, SLEEPY, WAVE, DIZZY }
enum class ClawdAction { NONE, WALK_LEFT, WALK_RIGHT, GO_LEFT_EDGE, GO_RIGHT_EDGE, CENTER, HOP, PEEK_LEFT, PEEK_RIGHT, LOOK_LEFT, LOOK_RIGHT, SLEEP }
enum class ClawdPriority { P0, P1, P2, P3 }

data class ClawdReaction(
    val text: String,
    val mood: ClawdMood = ClawdMood.NEUTRAL,
    val action: ClawdAction = ClawdAction.NONE,
    val link: String? = null,
    val question: Boolean = false,
    val priority: ClawdPriority = ClawdPriority.P2,
    val durationMs: Long = 5200,
)

/**
 * Локальный мозг Clawd для 3D-принтера. Без ИИ, без сети:
 * только правила, память и офлайн-факты о печати.
 */
class ClawdBrain(
    private val memory: ClawdMemoryStore,
    private val userName: String,
    private val clawdName: String = "Clawd",
) {
    private val lastByKind = mutableMapOf<String, Long>()
    private var lastBubbleAt = 0L
    private var lastIdleAt = 0L

    private companion object {
        val directInteractions = setOf(
            "tap", "pet", "jump", "throw", "drag", "drop",
            "clean", "wake", "eat", "spit", "peek", "found",
        )
    }

    private fun can(kind: String, priority: ClawdPriority): Boolean {
        val t = now()
        if (kind in directInteractions) return true
        val global = when (priority) {
            ClawdPriority.P0 -> 8
            ClawdPriority.P1 -> 90
            ClawdPriority.P2 -> 180
            ClawdPriority.P3 -> 300
        }
        if (t - lastBubbleAt < global && priority != ClawdPriority.P0) return false
        val cooldown = when (kind) {
            "offline" -> 120
            "online" -> 180
            "hot" -> 420
            "humidity" -> 900
            "low_spool" -> 900
            "pause" -> 240
            "error" -> 300
            "idle" -> 90
            else -> 180
        }
        if (t - (lastByKind[kind] ?: 0) < cooldown) return false
        return true
    }

    private fun emit(kind: String, reaction: ClawdReaction, deltaBond: Float = 0f): ClawdReaction {
        lastByKind[kind] = now()
        lastBubbleAt = now()
        memory.record(kind, deltaBond = deltaBond)
        memory.addSaid(reaction.text)
        return reaction
    }

    fun onSession(away: Long?): ClawdReaction? {
        if (away != null && away > 7200) {
            if (!can("back", ClawdPriority.P2)) return null
            val days = away / 86400
            val text = if (days >= 1) "Тебя не было $days дн. Принтер скучал?" else "Ты вернулся!"
            return emit("back", ClawdReaction(text, ClawdMood.HAPPY, ClawdAction.NONE, null, false, ClawdPriority.P2))
        }
        if (!can("hello", ClawdPriority.P3)) return null
        val name = memory.displayName.ifBlank { userName }.take(24)
        val generic = name.isBlank() || name in setOf("Хозяин", "Гость", "owner", "guest")
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hello = when {
            hour in 5..11 -> "Доброе утро"
            hour in 12..17 -> "Добрый день"
            hour in 18..22 -> "Добрый вечер"
            else -> "Не спится"
        }
        return emit(
            "hello",
            ClawdReaction(
                if (!generic) "$hello, $name!" else "$hello! Я $clawdName",
                ClawdMood.WAVE, ClawdAction.NONE, null, false, ClawdPriority.P3,
            ),
        )
    }

    fun onSnapshot(prev: PrinterSnapshot?, now: PrinterSnapshot): ClawdReaction? {
        if (prev == null) return null
        val phase = now.phase
        val old = prev.phase
        if (phase == PrintPhase.PRINTING && old != PrintPhase.PRINTING && can("print_start", ClawdPriority.P1)) {
            return emit("print_start", ClawdReaction("Поехали! Первый слой — самый важный.", ClawdMood.EXCITED, ClawdAction.HOP, null, false, ClawdPriority.P1))
        }
        if (phase == PrintPhase.FINISHED && old != PrintPhase.FINISHED && can("print_done", ClawdPriority.P0)) {
            return emit("print_done", ClawdReaction("Готово! Забери модель со стола.", ClawdMood.LOVE, ClawdAction.HOP, null, false, ClawdPriority.P0, 7000))
        }
        if (phase == PrintPhase.FAILED && old != PrintPhase.FAILED && can("failed", ClawdPriority.P0)) {
            return emit("failed", ClawdReaction("Печать сорвалась. Проверь принтер.", ClawdMood.SCARED, ClawdAction.NONE, null, false, ClawdPriority.P0, 7000))
        }
        if (phase == PrintPhase.PAUSED && old != PrintPhase.PAUSED && can("pause", ClawdPriority.P2)) {
            return emit("pause", ClawdReaction("Пауза. Всё нормально?", ClawdMood.SURPRISED, ClawdAction.LOOK_RIGHT, null, false, ClawdPriority.P2))
        }
        val active = phase == PrintPhase.PRINTING || phase == PrintPhase.PAUSED
        val freshError = now.errors.firstOrNull { e ->
            val filamentSensor = e.code.startsWith("07FF-2000-0002") ||
                e.code.startsWith("07FF-7000-0002")
            prev.errors.none { it.code == e.code } && (active || !filamentSensor)
        }
        if (freshError != null && can("error", ClawdPriority.P0)) {
            return emit("error", ClawdReaction("Ошибка: ${freshError.text}", ClawdMood.SAD, ClawdAction.NONE, null, false, ClawdPriority.P0, 7000))
        }
        val hot = now.nozzle >= 260f || now.bed >= 100f
        if (hot && can("hot", ClawdPriority.P1)) {
            return emit("hot", ClawdReaction("Горячо! Сопло ${now.nozzle.roundToInt()}°, стол ${now.bed.roundToInt()}°.", ClawdMood.SURPRISED, ClawdAction.LOOK_RIGHT, null, false, ClawdPriority.P1))
        }
        val wet = now.amsUnits.firstOrNull { it.humidityWarn }
        if (wet != null && can("humidity", ClawdPriority.P2)) {
            return emit("humidity", ClawdReaction("В AMS ${wet.id + 1} влажно (${wet.humidityLabel}). Просуши катушки?", ClawdMood.SURPRISED, ClawdAction.NONE, null, true, ClawdPriority.P2, 8000))
        }
        val low = now.usedFilaments.firstOrNull { it.remain in 0..15 }
        if (low != null && can("low_spool", ClawdPriority.P2)) {
            return emit("low_spool", ClawdReaction("«${low.type}» осталось ${low.remain}%. Хватит на модель?", ClawdMood.SURPRISED, ClawdAction.NONE, null, true, ClawdPriority.P2, 8000))
        }
        return null
    }

    fun onConnection(connected: Boolean, wasConnected: Boolean): ClawdReaction? {
        if (!connected && wasConnected && can("offline", ClawdPriority.P0)) {
            return emit("offline", ClawdReaction("Принтер пропал. Проверь Wi-Fi и питание.", ClawdMood.SCARED, ClawdAction.PEEK_LEFT, null, false, ClawdPriority.P0, 7000))
        }
        if (connected && !wasConnected && can("online", ClawdPriority.P1)) {
            return emit("online", ClawdReaction("Снова на связи!", ClawdMood.HAPPY, ClawdAction.HOP, null, false, ClawdPriority.P1))
        }
        return null
    }

    fun onDaily(context: android.content.Context): ClawdReaction? {
        val day = ClawdDaily.forDate()
        if (memory.lastDailyKey == day.key) return null
        memory.markDaily(day.key)
        return emit(
            "daily",
            ClawdReaction(
                "Сегодня ${day.dateText}, ${day.weekday}. ${day.fact}",
                ClawdMood.WAVE, ClawdAction.NONE, null, false, ClawdPriority.P2, 11000,
            ),
        )
    }

    fun onLifehack(context: android.content.Context): ClawdReaction? {
        val day = ClawdDaily.forDate()
        if (memory.lastTipKey == day.key) return null
        memory.markTip(day.key)
        return emit(
            "lifehack",
            ClawdReaction(
                if (day.date.dayOfYear % 2 == 0) "Совет дня: ${day.tip}"
                else "А вы знали, что ${day.curiosity}?",
                ClawdMood.HAPPY, ClawdAction.NONE, null, false, ClawdPriority.P3, 9000,
            ),
        )
    }

    fun onInteraction(kind: String): ClawdReaction? {
        val delta = when (kind) {
            "pet" -> 0.8f
            "tap" -> 0.1f
            "jump" -> 0.2f
            "throw" -> 0.15f
            "clean" -> 0.5f
            else -> 0f
        }
        if (kind == "tap") {
            return emit(
                "fact",
                ClawdReaction(
                    ClawdFactDeck.next(memory),
                    ClawdMood.HAPPY,
                    ClawdAction.NONE,
                    null,
                    false,
                    ClawdPriority.P2,
                    16_000,
                ),
                deltaBond = 0.1f,
            )
        }
        if (!can(kind, ClawdPriority.P3)) return null
        val (text, mood, action) = when (kind) {
            "pet" -> Triple("Мрр…", ClawdMood.LOVE, ClawdAction.NONE)
            "tap" -> Triple("Ой!", ClawdMood.SURPRISED, ClawdAction.HOP)
            "jump" -> Triple("Хоп!", ClawdMood.EXCITED, ClawdAction.HOP)
            "drag" -> Triple("Куда мы?", ClawdMood.SURPRISED, ClawdAction.NONE)
            "throw" -> Triple("Уиии!", ClawdMood.DIZZY, ClawdAction.NONE)
            "dizzy" -> Triple("Голова кружится…", ClawdMood.DIZZY, ClawdAction.NONE)
            "wake" -> Triple("А? Что?", ClawdMood.SURPRISED, ClawdAction.NONE)
            "eat" -> Triple("Ням! Филамент вкусный.", ClawdMood.HAPPY, ClawdAction.NONE)
            "spit" -> Triple("Ладно, отдаю!", ClawdMood.SURPRISED, ClawdAction.NONE)
            "clean" -> Triple("Спасибо!", ClawdMood.HAPPY, ClawdAction.NONE)
            "peek" -> Triple("Ку-ку!", ClawdMood.WAVE, ClawdAction.NONE)
            "found" -> Triple("Нашёл меня!", ClawdMood.LAUGH, ClawdAction.HOP)
            "drop" -> Triple("Я тут постою", ClawdMood.HAPPY, ClawdAction.NONE)
            else -> Triple("Хи!", ClawdMood.HAPPY, ClawdAction.NONE)
        }
        return emit(kind, ClawdReaction(text, mood, action, null, false, ClawdPriority.P3), deltaBond = delta)
    }

    fun onTick(now: PrinterSnapshot, connected: Boolean): ClawdReaction? {
        if (!can("tick", ClawdPriority.P3)) return null
        val question = question(now, connected)
        if (question != null) return question
        if (now() - lastIdleAt < 60) return null
        lastIdleAt = now()
        val idle = listOf(
            "Я тут, слежу за слоями", "Всё спокойно", "Хм…",
            "Проверим первый слой?", "Не забудь проветрить комнату",
        )
        return emit("idle", ClawdReaction(idle.random(), ClawdMood.NEUTRAL, ClawdAction.NONE, null, false, ClawdPriority.P3))
    }

    private fun question(now: PrinterSnapshot, connected: Boolean): ClawdReaction? {
        val t = now()
        fun ask(id: String, text: String, cooldown: Long = 2 * 86400L): ClawdReaction? {
            if (memory.askedRecently(id, cooldown)) return null
            memory.markAsked(id)
            lastBubbleAt = t
            memory.record("question")
            return ClawdReaction(text, ClawdMood.SURPRISED, ClawdAction.LOOK_RIGHT, null, true, ClawdPriority.P2, 8000)
        }
        if (!connected) return ask("q_offline", "Принтер не отвечает. Проверить Wi-Fi?", 1800)
        if (now.phase == PrintPhase.PRINTING && now.progress in 80..98) {
            ask("q_finish", "Печать почти закончена. Приготовить стол?", 86400)?.let { return it }
        }
        if (now.nozzleTarget >= 270f) {
            ask("q_temp", "Сопло ${now.nozzleTarget.roundToInt()}°. Это точно для этого пластика?", 3 * 86400)?.let { return it }
        }
        val wet = now.amsUnits.firstOrNull { it.humidityWarn }
        if (wet != null) {
            ask("q_humidity", "В AMS влажно: ${wet.humidityLabel}. Просушить катушки?", 3 * 86400)?.let { return it }
        }
        val low = now.usedFilaments.firstOrNull { it.remain in 0..15 }
        if (low != null) {
            ask("q_spool", "«${low.type}» почти кончился. Заменить заранее?", 3 * 86400)?.let { return it }
        }
        if (now.remainingMin >= 360 && now.phase == PrintPhase.PRINTING) {
            ask("q_long", "Печать дольше 6 часов. Проверить камеру?", 7 * 86400)?.let { return it }
        }
        return null
    }
}

private fun now(): Long = System.currentTimeMillis() / 1000
