package ru.layerpilot.data.models

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs

/** Точка на столе принтера в миллиметрах. */
data class PlatePoint(val x: Float, val y: Float)

data class SkipPart(
    val id: Int,
    val name: String,
    val polygon: List<PlatePoint> = emptyList(),
) {
    val hasShape: Boolean get() = polygon.size >= 3
}

data class SkipPlate(
    val parts: List<SkipPart>,
    val bedWidth: Float = 256f,
    val bedHeight: Float = 256f,
)

/**
 * Разбор 3MF для пропуска деталей: пластина, объекты и их настоящие контуры
 * из геометрии модели. Если геометрии нет (например, старый 3MF), остаются
 * имена и ID — список всё равно работает.
 *
 * Bambu Studio хранит положение объектов на столе в build-элементах
 * 3D/3dmodel.model, а состав пластины — в Metadata/model_settings.config.
 * Поэтому для контуров берём build-трансформ, а assemble — только запасной
 * вариант (в некоторых файлах assemble описывает раскладку вне стола).
 */
object PlateInspector {

    private val IDENTITY = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f,
        0f, 0f, 0f,
    )

    fun inspect(
        file: File,
        plateIndex: Int = 1,
        bedWidth: Float = 256f,
        bedHeight: Float = 256f,
    ): SkipPlate = runCatching {
        inspectOrThrow(file, plateIndex, bedWidth, bedHeight)
    }.getOrDefault(SkipPlate(emptyList(), bedWidth, bedHeight))

    internal fun inspectOrThrow(
        file: File,
        plateIndex: Int = 1,
        bedWidth: Float = 256f,
        bedHeight: Float = 256f,
    ): SkipPlate {
        ZipFile(file).use { zip ->
            val settings = parseSettings(zip)
            val meshes = loadMeshes(zip)
            val sliceNames = parseSliceInfo(zip)
            val plate = settings.plates.firstOrNull { it.index == plateIndex }
                ?: settings.plates.firstOrNull()
            val parts = mutableListOf<SkipPart>()
            plate?.instances.orEmpty().forEach { inst ->
                val name = settings.objectNames[inst.objectId]
                    ?: sliceNames[inst.identifyId]
                    ?: "Деталь ${parts.size + 1}"
                val builds = settings.buildItems[inst.objectId].orEmpty()
                val transform = builds.getOrNull(inst.instanceId)
                    ?: builds.firstOrNull()
                    ?: settings.assemble[inst.objectId to inst.instanceId]
                    ?: settings.assemble[inst.objectId to 0]
                    ?: IDENTITY
                val vertices = meshes.verticesFor(settings.rootFile, inst.objectId)
                val polygon = if (vertices.isNotEmpty()) {
                    val raw = hullOf(vertices, transform)
                    normalizeToBed(raw, plateIndex, bedWidth, bedHeight)
                } else emptyList()
                parts.add(SkipPart(inst.identifyId, name, polygon))
            }
            if (parts.isEmpty()) {
                sliceNames.forEach { (id, name) -> parts.add(SkipPart(id, name)) }
            }
            return SkipPlate(parts, bedWidth, bedHeight)
        }
    }

    // ------------------------------------------------------------------ 3MF config

    private data class PlateInstance(
        val identifyId: Int,
        val objectId: String,
        val instanceId: Int,
    )

    private data class PlateInfo(var index: Int) {
        val instances = mutableListOf<PlateInstance>()
    }

    private data class SettingsData(
        val objectNames: Map<String, String>,
        val plates: List<PlateInfo>,
        val assemble: Map<Pair<String, Int>, FloatArray>,
        val buildItems: Map<String, List<FloatArray>>,
        val rootFile: String,
    )

    private fun parseSettings(zip: ZipFile): SettingsData {
        val names = mutableMapOf<String, String>()
        val plates = mutableListOf<PlateInfo>()
        val assemble = mutableMapOf<Pair<String, Int>, FloatArray>()
        var currentObject: String? = null
        var currentPlate: PlateInfo? = null
        var inPart = false
        var inInstance = false
        var instanceObject: String? = null
        var instanceId = 0
        var instanceIdentify: Int? = null

        val entry = zip.entries().asSequence()
            .firstOrNull { it.name.equals("Metadata/model_settings.config", true) }
        if (entry != null) {
            val parser = Xml.newPullParser()
            parser.setInput(zip.getInputStream(entry), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "object" -> {
                            currentObject = parser.getAttributeValue(null, "id")
                            inPart = false
                        }
                        "part" -> inPart = true
                        "plate" -> currentPlate = PlateInfo(0)
                        "model_instance" -> {
                            inInstance = true
                            instanceObject = null
                            instanceId = 0
                            instanceIdentify = null
                        }
                        "assemble_item" -> {
                            val objectId = parser.getAttributeValue(null, "object_id")
                            val id = parser.getAttributeValue(null, "instance_id")
                                ?.toIntOrNull() ?: 0
                            val transform = parseTransform(parser.getAttributeValue(null, "transform"))
                            if (objectId != null && transform != null) {
                                assemble[objectId to id] = transform
                            }
                        }
                        "metadata" -> {
                            val key = attr(parser, "key")
                            val value = attr(parser, "value").orEmpty()
                            if (inInstance) {
                                when (key) {
                                    "object_id" -> instanceObject = value
                                    "instance_id" -> instanceId = value.toIntOrNull() ?: 0
                                    "identify_id" -> instanceIdentify = value.toIntOrNull()
                                }
                            } else if (currentPlate != null) {
                                if (key == "plater_id") {
                                    value.toIntOrNull()?.let { currentPlate?.index = it }
                                }
                            } else if (currentObject != null && !inPart &&
                                key == "name" && value.isNotBlank()
                            ) {
                                names[currentObject!!] = value
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (parser.name) {
                        "part" -> inPart = false
                        "model_instance" -> {
                            val obj = instanceObject
                            val id = instanceIdentify
                            if (obj != null && id != null) {
                                currentPlate?.instances?.add(
                                    PlateInstance(id, obj, instanceId),
                                )
                            }
                            inInstance = false
                        }
                        "plate" -> {
                            currentPlate?.let { plate ->
                                if (plate.index <= 0) plate.index = plates.size + 1
                                plates.add(plate)
                            }
                            currentPlate = null
                        }
                        "object" -> {
                            currentObject = null
                            inPart = false
                        }
                    }
                }
                event = parser.next()
            }
        }

        val rootFile = zip.entries().asSequence()
            .firstOrNull { it.name.equals("3D/3dmodel.model", true) }?.name ?: "3D/3dmodel.model"
        val buildItems = mutableMapOf<String, MutableList<FloatArray>>()
        zip.getEntry(rootFile)?.let { root ->
            val parser = Xml.newPullParser()
            parser.setInput(zip.getInputStream(root), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val objectId = parser.getAttributeValue(null, "objectid")
                    if (objectId != null) {
                        val transform = parseTransform(parser.getAttributeValue(null, "transform"))
                            ?: IDENTITY
                        buildItems.getOrPut(objectId) { mutableListOf() }.add(transform)
                    }
                }
                event = parser.next()
            }
        }

        return SettingsData(names, plates, assemble, buildItems, rootFile)
    }

    private fun parseSliceInfo(zip: ZipFile): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        val entry = zip.entries().asSequence()
            .firstOrNull { it.name.equals("Metadata/slice_info.config", true) } ?: return result
        val parser = Xml.newPullParser()
        parser.setInput(zip.getInputStream(entry), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "object") {
                val id = parser.getAttributeValue(null, "identify_id")?.toIntOrNull()
                val name = parser.getAttributeValue(null, "name")?.trim().orEmpty()
                val skipped = parser.getAttributeValue(null, "skipped")?.toBooleanStrictOrNull()
                if (id != null && skipped != true) {
                    result[id] = name.ifBlank { "Деталь $id" }
                }
            }
            event = parser.next()
        }
        return result
    }

    // ------------------------------------------------------------------ mesh geometry

    private class RawObject(
        val vertices: MutableList<FloatArray> = mutableListOf(),
        val components: MutableList<RawComponent> = mutableListOf(),
    )

    private data class RawComponent(
        val objectId: String,
        val path: String?,
        val transform: FloatArray?,
    )

    private class MeshStore {
        val files = mutableMapOf<String, Map<String, RawObject>>()
        private val cache = mutableMapOf<Pair<String, String>, List<FloatArray>>()

        fun verticesFor(file: String, objectId: String, depth: Int = 0): List<FloatArray> {
            if (depth > 12) return emptyList()
            val key = file to objectId
            cache[key]?.let { return it }
            val obj = files[file]?.get(objectId) ?: return emptyList()
            val result = mutableListOf<FloatArray>()
            result.addAll(obj.vertices)
            obj.components.forEach { comp ->
                val childFile = resolvePath(file, comp.path)
                val child = verticesFor(childFile, comp.objectId, depth + 1)
                val transform = comp.transform
                child.forEach { v ->
                    result.add(if (transform != null) applyTransform(transform, v) else v)
                }
            }
            cache[key] = result
            return result
        }

        private fun resolvePath(currentFile: String, raw: String?): String {
            val clean = raw?.trim()?.replace('\\', '/')?.removePrefix("/")
                ?.takeIf { it.isNotBlank() } ?: return currentFile
            if (files.containsKey(clean)) return clean
            val dir = currentFile.substringBeforeLast('/', "")
            val joined = if (dir.isBlank()) clean else "$dir/$clean"
            return if (files.containsKey(joined)) joined else clean
        }
    }

    private fun loadMeshes(zip: ZipFile): MeshStore {
        val store = MeshStore()
        zip.entries().asSequence().filter { it.name.endsWith(".model", true) }.forEach { entry ->
            runCatching {
                val objects = mutableMapOf<String, RawObject>()
                val parser = Xml.newPullParser()
                parser.setInput(zip.getInputStream(entry), "UTF-8")
                var currentId: String? = null
                var current: RawObject? = null
                var inVertices = false
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "object" -> {
                                currentId = parser.getAttributeValue(null, "id")
                                current = RawObject()
                            }
                            "vertices" -> inVertices = true
                            "vertex" -> if (inVertices) {
                                val x = parser.getAttributeValue(null, "x")?.toFloatOrNull()
                                val y = parser.getAttributeValue(null, "y")?.toFloatOrNull()
                                val z = parser.getAttributeValue(null, "z")?.toFloatOrNull() ?: 0f
                                if (x != null && y != null) {
                                    current?.vertices?.add(floatArrayOf(x, y, z))
                                }
                            }
                            "component" -> {
                                val target = parser.getAttributeValue(null, "objectid")
                                val path = attr(parser, "path")
                                val transform = parseTransform(
                                    parser.getAttributeValue(null, "transform"),
                                )
                                if (target != null) {
                                    current?.components?.add(
                                        RawComponent(target, path, transform),
                                    )
                                }
                            }
                        }
                    } else if (event == XmlPullParser.END_TAG) {
                        when (parser.name) {
                            "vertices" -> inVertices = false
                            "object" -> {
                                val id = currentId
                                val obj = current
                                if (id != null && obj != null) objects[id] = obj
                                currentId = null
                                current = null
                            }
                        }
                    }
                    event = parser.next()
                }
                store.files[entry.name] = objects
            }
        }
        return store
    }

    private fun attr(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == name) return parser.getAttributeValue(i)
        }
        return null
    }

    // ------------------------------------------------------------------ math

    private fun parseTransform(raw: String?): FloatArray? {
        val values = raw?.trim()?.split(Regex("\\s+"))
            ?.mapNotNull { it.toFloatOrNull() } ?: return null
        if (values.size < 12) return null
        return values.take(12).toFloatArray()
    }

    /**
     * 3MF-матрица — 4 строки по 3 числа, перевод в последней строке.
     * x' = m0*x + m3*y + m6*z + m9; y' = m1*x + m4*y + m7*z + m10.
     */
    private fun applyTransform(m: FloatArray, v: FloatArray): FloatArray = floatArrayOf(
        m[0] * v[0] + m[3] * v[1] + m[6] * v[2] + m[9],
        m[1] * v[0] + m[4] * v[1] + m[7] * v[2] + m[10],
        m[2] * v[0] + m[5] * v[1] + m[8] * v[2] + m[11],
    )

    private fun hullOf(
        vertices: List<FloatArray>,
        m: FloatArray,
    ): List<PlatePoint> {
        if (vertices.isEmpty()) return emptyList()
        val projected = mutableListOf<PlatePoint>()
        val step = (vertices.size / 6000).coerceAtLeast(1)
        var i = 0
        while (i < vertices.size) {
            val p = applyTransform(m, vertices[i])
            if (p[0].isFinite() && p[1].isFinite()) {
                projected.add(PlatePoint(p[0], p[1]))
            }
            i += step
        }
        if (projected.isEmpty()) return emptyList()
        val hull = convexHull(projected)
        if (hull.size >= 3) return hull
        val minX = projected.minOf { it.x }
        val maxX = projected.maxOf { it.x }
        val minY = projected.minOf { it.y }
        val maxY = projected.maxOf { it.y }
        return listOf(
            PlatePoint(minX, minY), PlatePoint(maxX, minY),
            PlatePoint(maxX, maxY), PlatePoint(minX, maxY),
        )
    }

    /**
     * Bambu Studio раскладывает пластины мультипроекта сеткой: вторая пластина
     * сдвинута на +bedWidth по X, четвёртая — на -bedHeight по Y и т.д.
     * Если контур уже помещается в стол, ничего не двигаем (одиночные 3MF).
     */
    private fun normalizeToBed(
        polygon: List<PlatePoint>,
        plateIndex: Int,
        bedW: Float,
        bedH: Float,
    ): List<PlatePoint> {
        if (polygon.isEmpty() || plateIndex <= 1) return polygon
        if (fits(polygon, bedW, bedH)) return polygon
        val zero = plateIndex - 1
        // Bambu Studio ставит пластины с зазором 20% от стола: 256 → 307.2 мм.
        val stepX = bedW * 1.2f
        val stepY = bedH * 1.2f
        val dx = (zero % 3) * stepX
        val dy = -(zero / 3) * stepY
        val shifted = polygon.map { PlatePoint(it.x - dx, it.y - dy) }
        return if (fits(shifted, bedW, bedH)) shifted else polygon
    }

    private fun fits(polygon: List<PlatePoint>, bedW: Float, bedH: Float): Boolean =
        polygon.all { it.x >= -1f && it.x <= bedW + 1f && it.y >= -1f && it.y <= bedH + 1f }

    private fun convexHull(points: List<PlatePoint>): List<PlatePoint> {
        if (points.size < 3) return points
        val sorted = points.distinctBy { "${it.x}:${it.y}" }
            .sortedWith(compareBy({ it.x }, { it.y }))
        if (sorted.size < 3) return sorted
        fun cross(o: PlatePoint, a: PlatePoint, b: PlatePoint): Float =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

        val lower = mutableListOf<PlatePoint>()
        sorted.forEach { p ->
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }
        val upper = mutableListOf<PlatePoint>()
        sorted.asReversed().forEach { p ->
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }
        return (lower.dropLast(1) + upper.dropLast(1)).take(120)
    }

    fun bedSizeFor(model: String?): Pair<Float, Float> {
        val value = model?.let {
            runCatching { ru.layerpilot.data.model.PrinterModel.fromName(it).buildVolume }.getOrNull()
        }.orEmpty()
        val numbers = Regex("(\\d+(?:[.,]\\d+)?)").findAll(value)
            .mapNotNull { it.groupValues[1].replace(',', '.').toFloatOrNull() }
            .toList()
        return if (numbers.size >= 2) numbers[0] to numbers[1] else 256f to 256f
    }

    fun contains(polygon: List<PlatePoint>, x: Float, y: Float): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            val dy = yj - yi
            if (((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / (if (abs(dy) > 1e-6f) dy else 1e-6f) + xi)
            ) inside = !inside
            j = i
        }
        return inside
    }
}
