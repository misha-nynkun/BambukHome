package ru.layerpilot.clawd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ClawdEyes { NORMAL, BLINK, HAPPY, SURPRISED, DIZZY, SLEEP, ANGRY, SAD }
enum class ClawdArms { NORMAL, UP, WAVE1, WAVE2, TUCK }
enum class ClawdLegs { STAND, WALK1, WALK2, DANGLE, TUCK }

data class ClawdFrame(
    val eyes: ClawdEyes = ClawdEyes.NORMAL,
    val look: Int = 0,
    val arms: ClawdArms = ClawdArms.NORMAL,
    val legs: ClawdLegs = ClawdLegs.STAND,
    val squash: Int = 0,
    val blush: Boolean = false,
    val mouth: Boolean = false,
)

data class ClawdPalette(val name: String, val body: Color, val eye: Color)

val ClawdPalettes = listOf(
    ClawdPalette("Claude", Color(0xFFD97757), Color(0xFF1A1A1A)),
    ClawdPalette("Мятный", Color(0xFF4FBF98), Color(0xFF12241D)),
    ClawdPalette("Лавандовый", Color(0xFF9B8AEE), Color(0xFF1B1631)),
    ClawdPalette("Небесный", Color(0xFF4FA8E8), Color(0xFF0E1C29)),
    ClawdPalette("Розовый", Color(0xFFEE85AE), Color(0xFF2A1220)),
    ClawdPalette("Графит", Color(0xFF5E6670), Color(0xFFF2F2F2)),
)

const val PET_GRID_W = 18
const val PET_GRID_H = 13
// Индексы остаются прежними ради совместимости со старыми настройками.
// В интерфейсе размеры показываются в порядке ClawdSizeOrder: Мини, Маленький, Средний, Большой.
val ClawdUnitSizes = listOf(3.dp, 4.dp, 5.dp, 2.5.dp)
val ClawdSizes = listOf("Маленький", "Средний", "Большой", "Мини")
val ClawdSizeOrder = listOf(3, 0, 1, 2)

fun clawdWidth(unit: Dp): Dp = unit * PET_GRID_W
fun clawdHeight(unit: Dp): Dp = unit * PET_GRID_H

@Composable
fun ClawdSprite(
    frame: ClawdFrame,
    paletteIndex: Int,
    unit: Dp,
    modifier: Modifier = Modifier,
) {
    val palette = ClawdPalettes[paletteIndex.coerceIn(ClawdPalettes.indices)]
    val blush = mixColor(palette.body, Color(0xFFFF4F79), 0.5f)
    Canvas(modifier.size(clawdWidth(unit), clawdHeight(unit))) {
        val s = size.width / PET_GRID_W
        fun px(x0: Int, y0: Int, x1: Int, y1: Int, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(x0 * s, y0 * s),
                size = Size((x1 - x0) * s, (y1 - y0) * s),
            )
        }

        var left = 3
        var right = 15
        var top = 3
        var bottom = 10
        if (frame.squash > 0) {
            left = 2; right = 16; top = 5
        } else if (frame.squash < 0) {
            left = 4; right = 14; top = 2
        }
        if (frame.legs == ClawdLegs.TUCK) {
            top += 1; bottom += 1
        }
        val heights = when (frame.legs) {
            ClawdLegs.STAND -> listOf(2, 2, 2, 2)
            ClawdLegs.WALK1 -> listOf(1, 2, 1, 2)
            ClawdLegs.WALK2 -> listOf(2, 1, 2, 1)
            ClawdLegs.DANGLE -> listOf(3, 3, 3, 3)
            ClawdLegs.TUCK -> listOf(1, 1, 1, 1)
        }
        listOf(left + 1, left + 3, right - 4, right - 2).forEachIndexed { i, col ->
            px(col, bottom, col + 1, bottom + heights[i], palette.body)
        }

        fun arm(side: Int, style: ClawdArms) {
            when (style) {
                ClawdArms.UP -> if (side < 0) {
                    px(left - 2, top - 1, left - 1, top + 3, palette.body)
                    px(left - 1, top + 2, left, top + 3, palette.body)
                } else {
                    px(right + 1, top - 1, right + 2, top + 3, palette.body)
                    px(right, top + 2, right + 1, top + 3, palette.body)
                }
                ClawdArms.TUCK -> if (side < 0) {
                    px(left - 1, top + 3, left, top + 5, palette.body)
                } else {
                    px(right, top + 3, right + 1, top + 5, palette.body)
                }
                else -> if (side < 0) {
                    px(left - 2, top + 3, left, top + 5, palette.body)
                } else {
                    px(right, top + 3, right + 2, top + 5, palette.body)
                }
            }
        }
        var leftArm = frame.arms
        var rightArm = frame.arms
        if (frame.arms == ClawdArms.WAVE1) {
            leftArm = ClawdArms.UP; rightArm = ClawdArms.NORMAL
        } else if (frame.arms == ClawdArms.WAVE2) {
            leftArm = ClawdArms.NORMAL; rightArm = ClawdArms.UP
        }
        arm(-1, leftArm)
        arm(1, rightArm)

        px(left, top, right, bottom, palette.body)

        val ey = top + 2
        listOf(-1 to left + 3 + frame.look, 1 to right - 4 + frame.look).forEach { (side, cx) ->
            when (frame.eyes) {
                ClawdEyes.BLINK -> px(cx, ey + 1, cx + 1, ey + 2, palette.eye)
                ClawdEyes.HAPPY -> {
                    px(cx - 1, ey + 1, cx, ey + 2, palette.eye)
                    px(cx, ey, cx + 1, ey + 1, palette.eye)
                    px(cx + 1, ey + 1, cx + 2, ey + 2, palette.eye)
                }
                ClawdEyes.SURPRISED -> px(cx, ey - 1, cx + 1, ey + 2, palette.eye)
                ClawdEyes.DIZZY -> listOf(
                    -1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1,
                ).forEach { (dx, dy) -> px(cx + dx, ey + dy, cx + dx + 1, ey + dy + 1, palette.eye) }
                ClawdEyes.SLEEP -> px(cx - 1, ey + 1, cx + 2, ey + 2, palette.eye)
                ClawdEyes.ANGRY -> {
                    px(cx, ey, cx + 1, ey + 2, palette.eye)
                    val brow = if (side < 0) cx - 1 else cx + 1
                    px(brow, ey - 1, brow + 1, ey, palette.eye)
                }
                ClawdEyes.SAD -> {
                    px(cx, ey, cx + 1, ey + 2, palette.eye)
                    val brow = if (side < 0) cx + 1 else cx - 1
                    px(brow, ey - 1, brow + 1, ey, palette.eye)
                }
                ClawdEyes.NORMAL -> px(cx, ey, cx + 1, ey + 2, palette.eye)
            }
            if (frame.blush) {
                if (side < 0) px(cx - 2, ey + 2, cx, ey + 3, blush)
                else px(cx + 1, ey + 2, cx + 3, ey + 3, blush)
            }
        }
        if (frame.mouth) {
            val mx = (left + right) / 2
            val my = ey + 2
            px(mx - 1, my, mx + 1, minOf(my + 2, bottom), palette.eye)
        }
    }
}

private fun mixColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
