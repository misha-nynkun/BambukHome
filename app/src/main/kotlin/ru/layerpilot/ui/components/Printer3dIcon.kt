package ru.layerpilot.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Аккуратная иконка 3D-принтера для нижней навигации. */
val Printer3dIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Printer3d",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Верхняя рама
            moveTo(4.6f, 2.2f)
            lineTo(19.4f, 2.2f)
            quadTo(20.4f, 2.2f, 20.4f, 3.2f)
            lineTo(20.4f, 4.4f)
            lineTo(3.6f, 4.4f)
            lineTo(3.6f, 3.2f)
            quadTo(3.6f, 2.2f, 4.6f, 2.2f)
            close()
            // Левая стойка
            moveTo(3.6f, 3.8f)
            lineTo(5.8f, 3.8f)
            lineTo(5.8f, 21.2f)
            lineTo(3.6f, 21.2f)
            close()
            // Правая стойка
            moveTo(18.2f, 3.8f)
            lineTo(20.4f, 3.8f)
            lineTo(20.4f, 21.2f)
            lineTo(18.2f, 21.2f)
            close()
            // Основание
            moveTo(3.6f, 19.4f)
            lineTo(20.4f, 19.4f)
            lineTo(20.4f, 21.8f)
            lineTo(3.6f, 21.8f)
            close()
            // Поперечная балка
            moveTo(5.8f, 7.0f)
            lineTo(18.2f, 7.0f)
            lineTo(18.2f, 8.5f)
            lineTo(5.8f, 8.5f)
            close()
            // Печатающая головка
            moveTo(10.7f, 8.5f)
            lineTo(13.3f, 8.5f)
            lineTo(13.3f, 12.0f)
            lineTo(10.7f, 12.0f)
            close()
            // Сопло
            moveTo(11.1f, 12.0f)
            lineTo(12.9f, 12.0f)
            lineTo(12.0f, 14.2f)
            close()
            // Стол
            moveTo(5.8f, 16.0f)
            lineTo(18.2f, 16.0f)
            lineTo(18.2f, 17.4f)
            lineTo(5.8f, 17.4f)
            close()
            // Напечатанная деталь
            moveTo(9.9f, 16.0f)
            lineTo(14.1f, 16.0f)
            lineTo(13.3f, 13.5f)
            lineTo(10.7f, 13.5f)
            close()
        }
    }.build()
}
