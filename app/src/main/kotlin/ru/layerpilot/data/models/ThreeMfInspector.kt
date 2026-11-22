package ru.layerpilot.data.models

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

data class FilamentSpec(val id: Int, val type: String, val color: Int?)

data class ThreeMfInfo(
    val filaments: List<FilamentSpec>,
    val plateCount: Int,
    val thumbnail: ByteArray?,
) {
    val filamentCount: Int get() = filaments.size.coerceAtLeast(1)
}

/** Читает служебные файлы 3MF: сколько цветов в проекте и превью пластины. */
object ThreeMfInspector {

    fun inspect(file: File): ThreeMfInfo? {
        return runCatching {
            ZipFile(file).use { zip ->
                val slice = zip.entries().asSequence()
                    .firstOrNull { it.name.equals("Metadata/slice_info.config", ignoreCase = true) }
                val thumbEntry = zip.entries().asSequence()
                    .firstOrNull { it.name.equals("Metadata/plate_1.png", ignoreCase = true) }
                val thumbnail = thumbEntry?.let { zip.getInputStream(it).use { s -> s.readBytes() } }
                if (slice == null) {
                    return@use ThreeMfInfo(emptyList(), 1, thumbnail)
                }
                val parser = Xml.newPullParser()
                parser.setInput(zip.getInputStream(slice), "UTF-8")
                val filaments = LinkedHashMap<Int, FilamentSpec>()
                var plates = 0
                var event = parser.eventType
                var insidePlate = false
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "plate" -> { plates++; insidePlate = true }
                            "filament" -> if (insidePlate) {
                                val id = parser.getAttributeValue(null, "id")?.toIntOrNull() ?: -1
                                if (id >= 0) {
                                    val type = parser.getAttributeValue(null, "type") ?: ""
                                    val color = parseColor(parser.getAttributeValue(null, "color"))
                                    filaments[id] = FilamentSpec(id, type, color)
                                }
                            }
                        }
                    } else if (event == XmlPullParser.END_TAG && parser.name == "plate") {
                        insidePlate = false
                    }
                    event = parser.next()
                }
                ThreeMfInfo(filaments.values.toList(), plates.coerceAtLeast(1), thumbnail)
            }
        }.getOrNull()
    }

    private fun parseColor(value: String?): Int? {
        val s = value?.trim()?.removePrefix("#") ?: return null
        if (s.length < 6) return null
        return runCatching { 0xFF000000.toInt() or s.take(6).toLong(16).toInt() }.getOrNull()
    }
}
