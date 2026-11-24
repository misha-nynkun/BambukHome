package ru.layerpilot.clawd

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.layerpilot.data.model.PrinterSnapshot
import ru.layerpilot.data.printer.ConnectionStatus
import ru.layerpilot.data.settings.AppSettings
import ru.layerpilot.ui.AppViewModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private enum class PetState { IDLE, WALK, DRAG, HOP, THROW, SLEEP }
private data class Snack(val pos: Offset)
private data class Poop(val pos: Offset)
private data class Particle(
    val type: String,
    val pos: Offset,
    val vel: Offset,
    val born: Long,
    val ttl: Long,
)

private val ClawdQuietStart = 23
private val ClawdQuietEnd = 8

@Composable
fun ClawdOverlay(
    vm: AppViewModel,
    settings: AppSettings,
) {
    if (!settings.clawdEnabled) return
    val context = LocalContext.current
    val printerKey = settings.activePrinterId.ifBlank {
        settings.serial.ifBlank { settings.host.ifBlank { "default" } }
    }
    val displayName = settings.clawdOwnerName.ifBlank { "Хозяин" }
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val memory = remember(printerKey) {
        ClawdMemoryStore.get(context, printerKey, displayName)
    }
    if (memory.displayName != displayName) {
        memory.updateDisplayName(displayName)
    }
    val brain = remember(memory, printerKey, settings.clawdName) {
        ClawdBrain(memory, printerKey, settings.clawdName)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val unit = ClawdUnitSizes[settings.clawdSize.coerceIn(ClawdUnitSizes.indices)]
        val clawdW = with(density) { clawdWidth(unit).toPx() }
        val clawdH = with(density) { clawdHeight(unit).toPx() }
        val maxX = (maxW - clawdW).coerceAtLeast(0f)
        val maxY = (maxH - clawdH).coerceAtLeast(0f)
        val bottomGap = with(density) { 94.dp.toPx() }
        val floorY = (maxY - bottomGap).coerceAtLeast(0f)

        var pos by remember(maxW, maxH, clawdW, clawdH) {
            mutableStateOf(Offset((maxX - with(density) { 8.dp.toPx() }).coerceAtLeast(0f), floorY))
        }
        var vel by remember { mutableStateOf(Offset.Zero) }
        var state by remember { mutableStateOf(PetState.IDLE) }
        var mood by remember { mutableStateOf(ClawdMood.NEUTRAL) }
        var moodUntil by remember { mutableLongStateOf(0L) }
        var bubble by remember { mutableStateOf<ClawdReaction?>(null) }
        var bubbleHeight by remember { mutableIntStateOf(0) }
        var targetX by remember { mutableFloatStateOf(0f) }
        var look by remember { mutableIntStateOf(1) }
        var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var blinkUntil by remember { mutableLongStateOf(0L) }
        var nextBlink by remember { mutableLongStateOf(0L) }
        var snack by remember { mutableStateOf<Snack?>(null) }
        var poops by remember { mutableStateOf<List<Poop>>(emptyList()) }
        var snacksEaten by remember { mutableIntStateOf(0) }
        val particles = remember { mutableStateListOf<Particle>() }
        var prevSnapshot by remember { mutableStateOf<PrinterSnapshot?>(null) }
        var prevConnected by remember { mutableStateOf(false) }
        var lastTapAt by remember { mutableLongStateOf(0L) }
        var squashUntil by remember { mutableLongStateOf(0L) }
        var lastZzz by remember { mutableLongStateOf(0L) }
        val latestSnapshot by rememberUpdatedState(snapshot)

        fun haptic(kind: String = "soft") {
            if (!settings.clawdHaptics) return
            when (kind) {
                "click" -> ClawdHaptics.click(context)
                "happy" -> ClawdHaptics.happy(context)
                "long" -> ClawdHaptics.long(context)
                else -> ClawdHaptics.soft(context)
            }
        }

        fun addParticles(kind: String, count: Int = 1) {
            if (settings.clawdReduceMotion) return
            repeat(count) {
                val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
                val speed = with(density) { (8..22).random().dp.toPx() }
                particles.add(
                    Particle(
                        type = kind,
                        pos = Offset(pos.x + clawdW / 2f, pos.y + clawdH / 2f),
                        vel = Offset(cos(angle) * speed, -abs(sin(angle) * speed) - speed / 2f),
                        born = System.currentTimeMillis(),
                        ttl = 1400,
                    ),
                )
                if (particles.size > 14) particles.removeAt(0)
            }
        }

        fun walkTo(x: Float) {
            targetX = x.coerceIn(0f, maxX)
            look = if (targetX > pos.x) 1 else -1
            state = PetState.WALK
            lastInteraction = System.currentTimeMillis()
        }

        fun wanderTo(x: Float) {
            targetX = x.coerceIn(0f, maxX)
            look = if (targetX > pos.x) 1 else -1
            state = PetState.WALK
        }

        fun hop(strength: Float = 1f) {
            state = PetState.HOP
            vel = Offset(0f, -with(density) { (420.dp.toPx() * strength) })
            lastInteraction = System.currentTimeMillis()
        }

        fun react(reaction: ClawdReaction) {
            lastInteraction = System.currentTimeMillis()
            mood = reaction.mood
            moodUntil = System.currentTimeMillis() + 2600
            if (settings.clawdPhrases) bubble = reaction
            when (reaction.mood) {
                ClawdMood.LOVE -> addParticles("heart", 3)
                ClawdMood.HAPPY, ClawdMood.EXCITED, ClawdMood.LAUGH -> addParticles("spark", 3)
                ClawdMood.SLEEPY -> addParticles("zzz", 1)
                ClawdMood.WAVE -> { }
                else -> { }
            }
            when (reaction.action) {
                ClawdAction.HOP -> hop(if (reaction.mood == ClawdMood.EXCITED) 1.35f else 1f)
                ClawdAction.CENTER -> walkTo(maxX / 2f)
                ClawdAction.WALK_LEFT -> walkTo(pos.x - with(density) { 90.dp.toPx() })
                ClawdAction.WALK_RIGHT -> walkTo(pos.x + with(density) { 90.dp.toPx() })
                ClawdAction.GO_LEFT_EDGE -> walkTo(0f)
                ClawdAction.GO_RIGHT_EDGE -> walkTo(maxX)
                ClawdAction.LOOK_LEFT -> { look = -1; lastInteraction = System.currentTimeMillis() }
                ClawdAction.LOOK_RIGHT -> { look = 1; lastInteraction = System.currentTimeMillis() }
                ClawdAction.SLEEP -> state = PetState.SLEEP
                ClawdAction.PEEK_LEFT -> walkTo(0f)
                ClawdAction.PEEK_RIGHT -> walkTo(maxX)
                ClawdAction.NONE -> { }
            }
            if (settings.clawdDance && reaction.action == ClawdAction.NONE &&
                reaction.mood in setOf(ClawdMood.HAPPY, ClawdMood.LOVE, ClawdMood.EXCITED, ClawdMood.LAUGH)
            ) {
                hop(0.75f)
            }
            if (reaction.priority == ClawdPriority.P0) haptic("long")
        }

        fun petting() {
            lastInteraction = System.currentTimeMillis()
            mood = ClawdMood.LOVE
            moodUntil = System.currentTimeMillis() + 2200
            addParticles("heart", 2)
            haptic("happy")
        }

        fun eatSnack() {
            snack = null
            state = PetState.IDLE
            snacksEaten++
            mood = ClawdMood.HAPPY
            moodUntil = System.currentTimeMillis() + 1800
            addParticles("spark", 2)
            haptic("happy")
            brain.onInteraction("eat")?.let { react(it) }
            if (snacksEaten >= 3 && settings.clawdPoop && poops.size < 3) {
                poops = poops + Poop(pos)
                snacksEaten = 0
                memory.record("poop")
            }
        }

        fun wake() {
            if (state == PetState.SLEEP) {
                state = PetState.IDLE
                mood = ClawdMood.SURPRISED
                moodUntil = System.currentTimeMillis() + 1200
                brain.onInteraction("wake")?.let { react(it) }
            }
        }

        fun inQuietHours(): Boolean {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            return settings.clawdQuiet && (hour >= ClawdQuietStart || hour < ClawdQuietEnd)
        }

        LaunchedEffect(printerKey, maxW, maxH, clawdW) {
            if (pos.x < 0f) {
                pos = Offset(maxX - with(density) { 8.dp.toPx() }, floorY)
                targetX = pos.x
            } else {
                pos = Offset(pos.x.coerceIn(0f, maxX), pos.y.coerceIn(0f, maxY))
            }
            if (settings.clawdAbout.isNotBlank()) memory.addNote(settings.clawdAbout)
            val away = memory.sessionStart()
            val hello = brain.onSession(away)
            hello?.let { if (!inQuietHours()) react(it) }
            if (settings.clawdPhrases && !inQuietHours()) {
                delay(if (hello != null) 6_400L else 1_200L)
                brain.onDaily(context)?.let { react(it) }
                delay(12_800L)
                brain.onLifehack(context)?.let { react(it) }
            }
        }

        LaunchedEffect(settings.clawdEnabled, settings.clawdReduceMotion, maxW, maxH) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    last = now
                    val nowMs = System.currentTimeMillis()
                    if (nowMs > moodUntil) mood = ClawdMood.NEUTRAL
                    if (nowMs > nextBlink) {
                        blinkUntil = nowMs + 140
                        nextBlink = nowMs + 3800 + Random.nextLong(0L, 2400L)
                    }
                    if (state == PetState.WALK) {
                        val dx = targetX - pos.x
                        val step = with(density) { 70.dp.toPx() } * dt
                        if (abs(dx) <= step) {
                            pos = pos.copy(x = targetX)
                            state = PetState.IDLE
                            if (snack != null && abs(pos.x - snack!!.pos.x) < clawdW * 0.2f) eatSnack()
                        } else {
                            pos = pos.copy(x = pos.x + step * if (dx > 0) 1 else -1)
                            look = if (dx > 0) 1 else -1
                        }
                    } else if (state == PetState.HOP || state == PetState.THROW) {
                        pos = Offset(
                            (pos.x + vel.x * dt).coerceIn(0f, maxX),
                            (pos.y + vel.y * dt).coerceIn(0f, floorY),
                        )
                        // Падение всегда естественное: и после броска, и после переноса.
                        vel = Offset(vel.x * 0.995f, vel.y + with(density) { 2200.dp.toPx() } * dt)
                        if (pos.x <= 0f || pos.x >= maxX) {
                            vel = vel.copy(x = -vel.x * 0.55f)
                            pos = pos.copy(x = pos.x.coerceIn(0f, maxX))
                        }
                        if (pos.y >= floorY - 0.5f && vel.y >= 0f) {
                            pos = pos.copy(y = floorY)
                            if (abs(vel.y) > with(density) { 90.dp.toPx() }) {
                                vel = Offset(vel.x * 0.42f, -vel.y * 0.30f)
                                squashUntil = nowMs + 110
                            } else {
                                vel = Offset.Zero
                                state = PetState.IDLE
                            }
                        }
                    }
                    if (state == PetState.IDLE) {
                        val sleepMs = settings.clawdSleepMin * 60_000L
                        if (sleepMs > 0 && nowMs - lastInteraction > sleepMs) {
                            state = PetState.SLEEP
                            mood = ClawdMood.SLEEPY
                            if (settings.clawdPhrases && Random.nextFloat() < 0.35f) {
                                bubble = ClawdReaction("Тсс…", ClawdMood.SLEEPY, ClawdAction.SLEEP, null, false, ClawdPriority.P3, 3200)
                            }
                        }
                    }
                    if (state == PetState.SLEEP && nowMs - lastInteraction > 15_000L &&
                        nowMs - lastZzz > 2_400L && !settings.clawdReduceMotion) {
                        lastZzz = nowMs
                        addParticles("zzz", 1)
                    }
                    val list = particles
                    var i = 0
                    while (i < list.size) {
                        val p = list[i]
                        val age = nowMs - p.born
                        if (age > p.ttl) {
                            list.removeAt(i)
                            continue
                        }
                        list[i] = p.copy(
                            pos = Offset(p.pos.x + p.vel.x * dt, p.pos.y + p.vel.y * dt),
                            vel = Offset(p.vel.x * 0.985f, p.vel.y + with(density) { 70.dp.toPx() } * dt),
                        )
                        i++
                    }
                }
                // В покое не крутим кадры на 60 Гц — экономим батарею.
                if ((state == PetState.IDLE || state == PetState.SLEEP) &&
                    particles.isEmpty()
                ) {
                    delay(120L)
                }
            }
        }

        LaunchedEffect(snapshot) {
            if (settings.clawdBrain) brain.onSnapshot(prevSnapshot, snapshot)?.let { react(it) }
            prevSnapshot = snapshot
        }

        LaunchedEffect(connection) {
            val connected = connection is ConnectionStatus.Connected
            if (settings.clawdBrain) brain.onConnection(connected, prevConnected)?.let { react(it) }
            prevConnected = connected
        }

        LaunchedEffect(printerKey, settings.clawdBrain, settings.clawdPhrases) {
            while (true) {
                delay(25_000L)
                if (settings.clawdBrain && bubble == null && !inQuietHours()) {
                    brain.onTick(latestSnapshot, connection is ConnectionStatus.Connected)?.let { react(it) }
                }
            }
        }

        LaunchedEffect(settings.clawdEat, settings.clawdEnabled, settings.clawdReduceMotion) {
            if (!settings.clawdEat) return@LaunchedEffect
            while (true) {
                delay(45_000L + Random.nextLong(0L, 65_000L))
                if (settings.clawdEat && settings.clawdEnabled && state == PetState.IDLE &&
                    bubble == null && snack == null && !settings.clawdReduceMotion &&
                    Random.nextFloat() < 0.55f
                ) {
                    val x = Random.nextFloat() * maxX
                    snack = Snack(Offset(x, pos.y))
                    walkTo(x)
                }
            }
        }

        LaunchedEffect(settings.clawdWander) {
            if (!settings.clawdWander) return@LaunchedEffect
            while (true) {
                delay(18_000L + Random.nextLong(0L, 24_000L))
                if (settings.clawdWander && state == PetState.IDLE && bubble == null &&
                    snack == null && !settings.clawdReduceMotion && Random.nextFloat() < 0.42f
                ) {
                    wanderTo(Random.nextFloat() * maxX)
                }
            }
        }

        DisposableEffect(settings.clawdShake) {
            if (!settings.clawdShake) return@DisposableEffect onDispose { }
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            var lastShake = 0L
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val values = event?.values ?: return
                    val magnitude = kotlin.math.sqrt(
                        values[0] * values[0] + values[1] * values[1] + values[2] * values[2],
                    )
                    val now = System.currentTimeMillis()
                    if (magnitude > 17f && now - lastShake > 900L) {
                        lastShake = now
                        state = PetState.IDLE
                        mood = ClawdMood.DIZZY
                        moodUntil = now + 2200
                        addParticles("spark", 2)
                        haptic("long")
                        brain.onInteraction("shake")?.let { react(it) }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (manager != null && sensor != null) {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                onDispose { manager.unregisterListener(listener) }
            } else {
                onDispose { }
            }
        }

        LaunchedEffect(bubble?.text) {
            val b = bubble ?: return@LaunchedEffect
            delay(b.durationMs)
            if (bubble?.text == b.text) bubble = null
        }

        // particles
        Canvas(Modifier.fillMaxSize()) {
            val nowMs = System.currentTimeMillis()
            particles.forEach { p ->
                val life = ((nowMs - p.born).toFloat() / p.ttl).coerceIn(0f, 1f)
                val alpha = (1f - life).coerceIn(0f, 1f)
                when (p.type) {
                    "spark" -> drawCircle(
                        Color(0xFFFFC94D).copy(alpha = alpha),
                        radius = with(density) { 2.2.dp.toPx() },
                        center = p.pos,
                    )
                    "heart" -> drawHeart(p.pos, with(density) { 5.dp.toPx() }, Color(0xFFFF5C7A).copy(alpha = alpha))
                    "zzz" -> drawZzz(p.pos, with(density) { 7.dp.toPx() }, Color(0xFF8FA3BF).copy(alpha = alpha))
                    "note" -> {
                        drawCircle(Color(0xFF6FB8FF).copy(alpha = alpha), with(density) { 2.4.dp.toPx() }, p.pos)
                        drawLine(
                            Color(0xFF6FB8FF).copy(alpha = alpha),
                            p.pos,
                            Offset(p.pos.x + with(density) { 2.dp.toPx() }, p.pos.y - with(density) { 8.dp.toPx() }),
                            strokeWidth = with(density) { 1.5.dp.toPx() },
                        )
                    }
                }
            }
        }

        // snack
        snack?.let { s ->
            Canvas(
                Modifier.offset { IntOffset(s.pos.x.roundToInt(), s.pos.y.roundToInt()) }
                    .size(unit * 3),
            ) {
                val u = size.width / 3f
                drawRect(ClawdPalettes[settings.clawdColor.coerceIn(ClawdPalettes.indices)].body, Offset(u, u), Size(u, u))
            }
        }

        // poop
        poops.forEach { p ->
            Box(
                Modifier
                    .offset { IntOffset(p.pos.x.roundToInt(), (p.pos.y + clawdH * 0.7f).roundToInt()) }
                    .size(unit * 9, unit * 7)
                    .pointerInput(p) {
                        detectTapGestures {
                            poops = poops - p
                            brain.onInteraction("clean")?.let { react(it) }
                            haptic("soft")
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawPoop(size.width / 9f, ClawdPalettes[settings.clawdColor.coerceIn(ClawdPalettes.indices)].body)
                }
            }
        }

        // pet
        Box(
            Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .size(clawdWidth(unit), clawdHeight(unit))
                .zIndex(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        var lastPos = start
                        var lastTime = down.uptimeMillis
                        var dragging = false
                        var petted = false
                        var velocity = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val now = change.uptimeMillis
                            val dt = (now - lastTime).coerceAtLeast(1L)
                            val delta = change.position - lastPos
                            if (!dragging && (change.position - start).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                state = PetState.DRAG
                                wake()
                            }
                            if (dragging) {
                                pos = Offset(
                                    (pos.x + delta.x).coerceIn(0f, maxX),
                                    (pos.y + delta.y).coerceIn(0f, floorY),
                                )
                                velocity = Offset(delta.x / dt * 1000f, delta.y / dt * 1000f)
                                change.consume()
                            } else if (!petted && now - down.uptimeMillis > 420) {
                                petted = true
                                petting()
                                brain.onInteraction("pet")?.let { react(it) }
                            }
                            lastPos = change.position
                            lastTime = now
                            if (change.changedToUpIgnoreConsumed()) {
                                if (dragging) {
                                    val speed = velocity.getDistance()
                                    if (speed > 1300f) {
                                        state = PetState.THROW
                                        vel = velocity.coerceSpeed(2200f)
                                        brain.onInteraction("throw")?.let { react(it) }
                                    } else if (pos.y < floorY - 1f) {
                                        state = PetState.THROW
                                        vel = Offset(velocity.x * 0.25f, 0f)
                                        brain.onInteraction("drop")?.let { react(it) }
                                    } else {
                                        state = PetState.IDLE
                                        brain.onInteraction("drop")?.let { react(it) }
                                    }
                                } else if (!petted) {
                                    wake()
                                    val t = System.currentTimeMillis()
                                    if (t - lastTapAt < 320) {
                                        hop(1.1f)
                                        brain.onInteraction("jump")?.let { react(it) }
                                        lastTapAt = 0
                                    } else {
                                        lastTapAt = t
                                        brain.onInteraction("tap")?.let { react(it) }
                                        haptic("click")
                                    }
                                }
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val frame = buildFrame(
                state = state,
                mood = mood,
                blink = System.currentTimeMillis() < blinkUntil,
                look = look,
                walkingPhase = ((System.currentTimeMillis() / 130L) % 2L).toInt(),
                squash = System.currentTimeMillis() < squashUntil,
            )
            ClawdSprite(frame, settings.clawdColor, unit)
        }

        // bubble
        bubble?.let { b ->
            val bubbleWidth = with(density) { 285.dp.toPx() }
            val bx = (pos.x + clawdW / 2f - bubbleWidth / 2f).coerceIn(8f, (maxW - bubbleWidth - 8f).coerceAtLeast(8f))
            val measuredHeight = if (bubbleHeight > 0) bubbleHeight.toFloat()
            else with(density) { 120.dp.toPx() }
            val by = (pos.y - measuredHeight - with(density) { 6.dp.toPx() }).coerceAtLeast(8f)
            Surface(
                modifier = Modifier
                    .offset { IntOffset(bx.roundToInt(), by.roundToInt()) }
                    .widthIn(max = 285.dp)
                    .onSizeChanged { bubbleHeight = it.height }
                    .zIndex(2f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 10.dp,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(b.text, style = MaterialTheme.typography.bodyMedium)
                    if (b.question && b.link != null) {
                        TextButton(
                            onClick = { bubble = null; haptic("click") },
                        ) { Text("Проверить") }
                    }
                }
            }
        }
    }
}

private fun buildFrame(
    state: PetState,
    mood: ClawdMood,
    blink: Boolean,
    look: Int,
    walkingPhase: Int,
    squash: Boolean,
): ClawdFrame {
    val eyes = when {
        state == PetState.SLEEP || mood == ClawdMood.SLEEPY -> ClawdEyes.SLEEP
        mood == ClawdMood.HAPPY || mood == ClawdMood.LOVE || mood == ClawdMood.EXCITED ||
            mood == ClawdMood.LAUGH || mood == ClawdMood.WAVE -> ClawdEyes.HAPPY
        mood == ClawdMood.SURPRISED || mood == ClawdMood.SCARED -> ClawdEyes.SURPRISED
        mood == ClawdMood.ANGRY -> ClawdEyes.ANGRY
        mood == ClawdMood.SAD -> ClawdEyes.SAD
        mood == ClawdMood.DIZZY -> ClawdEyes.DIZZY
        blink -> ClawdEyes.BLINK
        else -> ClawdEyes.NORMAL
    }
    val arms = when {
        mood == ClawdMood.WAVE -> if (walkingPhase == 0) ClawdArms.WAVE1 else ClawdArms.WAVE2
        mood == ClawdMood.EXCITED -> ClawdArms.UP
        state == PetState.SLEEP || mood == ClawdMood.SAD -> ClawdArms.TUCK
        else -> ClawdArms.NORMAL
    }
    val legs = when {
        state == PetState.SLEEP -> ClawdLegs.TUCK
        state == PetState.THROW || state == PetState.HOP -> ClawdLegs.DANGLE
        state == PetState.WALK -> if (walkingPhase == 0) ClawdLegs.WALK1 else ClawdLegs.WALK2
        else -> ClawdLegs.STAND
    }
    val mouth = mood in setOf(ClawdMood.HAPPY, ClawdMood.EXCITED, ClawdMood.SURPRISED, ClawdMood.LAUGH)
    val blush = mood == ClawdMood.LOVE || mood == ClawdMood.HAPPY || mood == ClawdMood.WAVE
    return ClawdFrame(eyes, look, arms, legs, if (squash) 1 else 0, blush, mouth)
}

private fun Offset.coerceSpeed(max: Float): Offset {
    val d = getDistance()
    return if (d <= max || d <= 0f) this else this * (max / d)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(center: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + r * 0.35f)
        cubicTo(center.x - r, center.y - r * 0.5f, center.x - r * 0.5f, center.y - r, center.x, center.y - r * 0.35f)
        cubicTo(center.x + r * 0.5f, center.y - r, center.x + r, center.y - r * 0.5f, center.x, center.y + r * 0.35f)
        close()
    }
    drawPath(path, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawZzz(center: Offset, r: Float, color: Color) {
    drawLine(color, Offset(center.x - r, center.y), Offset(center.x, center.y), strokeWidth = 1.6f)
    drawLine(color, Offset(center.x, center.y), Offset(center.x - r, center.y + r), strokeWidth = 1.6f)
    drawLine(color, Offset(center.x - r, center.y + r), Offset(center.x, center.y + r), strokeWidth = 1.6f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPoop(s: Float, body: Color) {
    val rows = listOf(
        "....X....",
        "...XXo...",
        "..XXXXX..",
        ".XXXXXXo.",
        ".XekXekX.",
        "XXXXXXXXo",
        "XXXXXXXXX",
    )
    val bodyColor = Color(0xFF8A5A33)
    val light = Color(0xFFB27D4E)
    val white = Color.White
    val black = Color(0xFF1A1A1A)
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch ->
            val c = when (ch) {
                'X' -> bodyColor
                'o' -> light
                'w' -> white
                'k' -> black
                else -> null
            }
            if (c != null) drawRect(c, Offset(x * s, y * s), Size(s, s))
        }
    }
}
