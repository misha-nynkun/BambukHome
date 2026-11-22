package ru.layerpilot.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/** Настройки волны из «Внешнего вида»: толщина, амплитуда, анимация. */
data class WaveStyle(
    val thickness: Float = 1f,
    val amplitude: Float = 1f,
    val animated: Boolean = true,
)

val LocalWaveStyle = staticCompositionLocalOf { WaveStyle() }
