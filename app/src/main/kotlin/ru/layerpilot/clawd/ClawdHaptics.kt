package ru.layerpilot.clawd

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object ClawdHaptics {
    fun soft(context: Context) = vibrate(context, 12, 40)
    fun click(context: Context) = vibrate(context, 18, 70)
    fun happy(context: Context) = vibrate(context, 26, 90)
    fun long(context: Context) = vibrate(context, 35, 120)

    private fun vibrate(context: Context, ms: Long, amplitude: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)),
            )
        }
    }
}
