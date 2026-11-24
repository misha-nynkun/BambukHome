package ru.layerpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.TonalCard

private data class Calibration(
    val title: String,
    val subtitle: String,
    val steps: List<String>,
    val gcode: String,
)

private val calibrations = listOf(
    Calibration(
        "Первый слой",
        "База качества и адгезии",
        listOf(
            "Вымойте пластину и высушите её.",
            "Запустите автоуровень стола.",
            "Напечатайте тест первого слоя.",
            "Если слой блестит — сопло слишком близко, если рвётся — слишком далеко.",
        ),
        "M104 S210\nM140 S60\nG28\nG29\n; затем запустите тестовый первый слой из файлов",
    ),
    Calibration(
        "Поток (flow)",
        "Толщина стенок и точность размеров",
        listOf(
            "Напечатайте кубик со стенкой 0.4–0.8 мм.",
            "Измерьте стенки штангенциркулем.",
            "Скорректируйте flow в слайсере на процент отклонения.",
            "Повторите тест и запишите результат под катушку.",
        ),
        "; Тестовый кубик: 2 периметра, 0% заполнения, без обдува первого слоя",
    ),
    Calibration(
        "Pressure advance",
        "Углы, швы и скорость",
        listOf(
            "Напечатайте тест PA с разными значениями.",
            "Выберите участок с самыми ровными углами.",
            "Запишите значение для этого типа пластика.",
            "Проверьте результат на небольшой модели.",
        ),
        "; PA-тест зависит от слайсера. Начните с 0.02 для PLA и 0.04 для PETG",
    ),
    Calibration(
        "Температура",
        "Прочность и внешний вид",
        listOf(
            "Напечатайте температурную башню.",
            "Смотрите на мосты, сопли и расслоение.",
            "Выберите диапазон без дефектов.",
            "Сохраните профиль под конкретную катушку.",
        ),
        "M104 S220\n; для башни меняйте температуру каждые 5–10 мм по инструкции слайсера",
    ),
)

@Composable
fun CalibrationScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var open by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        ScreenHeader("Калибровка", "пошаговые мастера", onBack = onBack)
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            calibrations.forEachIndexed { index, item ->
                TonalCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Science, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { open = if (open == index) -1 else index }) {
                            Text(if (open == index) "Скрыть" else "Открыть")
                        }
                    }
                    if (open == index) {
                        Spacer(Modifier.height(8.dp))
                        item.steps.forEachIndexed { step, text ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("${step + 1}.", color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                                Text(text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()) {
                            SelectionContainer {
                                Text(item.gcode, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                    modifier = Modifier.padding(10.dp))
                            }
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(item.gcode))
                        }) {
                            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Скопировать G-code")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
