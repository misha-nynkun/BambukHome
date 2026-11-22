package ru.layerpilot.data.models

import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.sqrt

data class SceneBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float,
) {
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
    val centerZ: Float get() = (minZ + maxZ) / 2f
    val sizeX: Float get() = maxX - minX
    val sizeY: Float get() = maxY - minY
    val sizeZ: Float get() = maxZ - minZ
    val diagonal: Float get() = sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ)
}

/**
 * Готовый меш для OpenGL: на каждую вершину position(3) + normal(3) + color(3).
 * Треугольники плоские, без индексов — так проще и надёжнее для любых 3MF.
 */
data class ThreeMfScene(
    val vertices: FloatArray,
    val vertexCount: Int,
    val bounds: SceneBounds,
    val bedWidth: Float,
    val bedHeight: Float,
    val triangles: Int,
    val parts: Int,
    val title: String,
)

/** Полный разбор геометрии 3MF: объекты, компоненты, build-трансформы и пластина. */
object ThreeMfSceneReader {

    private val PALETTE = intArrayOf(
        0xFF4CC9B0.toInt(), 0xFFFF9F43.toInt(), 0xFF5B8DEF.toInt(),
        0xFFE17BC4.toInt(), 0xFF9CCC65.toInt(), 0xFFFFD166.toInt(),
        0xFF9B8CFF.toInt(), 0xFF4DD0E1.toInt(), 0xFFF06292.toInt(),
        0xFFA1887F.toInt(),
    )

    fun load(file: File, bedWidth: Float = 256f, bedHeight: Float = 256f): ThreeMfScene {
        ZipFile(file).use { zip ->
            val settings = parseSettings(zip)
            val meshes = loadMeshes(zip)
            val colors = parseFilamentColors(zip)
            val objectIds = settings.plateObjects.ifEmpty {
                settings.buildItems.keys.toList()
            }
            val vertexBag = FloatBag()
            val objectNames = settings.objectNames
            var triangleCount = 0
            var partCount = 0
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = -Float.MAX_VALUE

            objectIds.distinct().forEachIndexed { index, objectId ->
                val flattened = runCatching {
                    meshes.flatten(settings.rootFile, objectId)
                }.getOrNull() ?: return@forEachIndexed
                if (flattened.indices.isEmpty()) return@forEachIndexed
                val transforms = settings.buildItems[objectId]
                    ?: listOf(IDENTITY)
                val extruder = settings.objectExtruders[objectId] ?: (index + 1)
                val color = colors.getOrNull((extruder - 1).coerceAtLeast(0))?.toInt()
                    ?: PALETTE[index % PALETTE.size]
                transforms.forEach { transform ->
                    appendMesh(
                        bag = vertexBag,
                        positions = flattened.positions,
                        indices = flattened.indices,
                        transform = transform,
                        color = color,
                    )
                    triangleCount += flattened.indices.size / 3
                    partCount++
                    val step = 3
                    var i = 0
                    while (i < flattened.positions.size) {
                        val p = applyTransform(transform, flattened.positions, i)
                        if (p[0] < minX) minX = p[0]
                        if (p[0] > maxX) maxX = p[0]
                        if (p[1] < minY) minY = p[1]
                        if (p[1] > maxY) maxY = p[1]
                        if (p[2] < minZ) minZ = p[2]
                        if (p[2] > maxZ) maxZ = p[2]
                        i += step
                    }
                }
            }

            if (vertexBag.size == 0) {
                throw IllegalStateException("В 3MF нет треугольной геометрии")
            }
            if (minX == Float.MAX_VALUE) {
                minX = 0f; maxX = bedWidth
                minY = 0f; maxY = bedHeight
                minZ = 0f; maxZ = 1f
            }
            return ThreeMfScene(
                vertices = vertexBag.toArray(),
                vertexCount = vertexBag.size / 9,
                bounds = SceneBounds(minX, maxX, minY, maxY, minZ, maxZ),
                bedWidth = bedWidth,
                bedHeight = bedHeight,
                triangles = triangleCount,
                parts = partCount,
                title = file.name.substringBeforeLast('.').ifBlank { file.name },
            )
        }
    }

    private fun appendMesh(
        bag: FloatBag,
        positions: FloatArray,
        indices: IntArray,
        transform: FloatArray,
        color: Int,
    ) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        var i = 0
        while (i + 2 < indices.size) {
            val ia = indices[i] * 3
            val ib = indices[i + 1] * 3
            val ic = indices[i + 2] * 3
            if (ia + 2 >= positions.size || ib + 2 >= positions.size || ic + 2 >= positions.size) {
                i += 3
                continue
            }
            val a = applyTransform(transform, positions, ia)
            val bb = applyTransform(transform, positions, ib)
            val c = applyTransform(transform, positions, ic)
            val ux = bb[0] - a[0]
            val uy = bb[1] - a[1]
            val uz = bb[2] - a[2]
            val vx = c[0] - a[0]
            val vy = c[1] - a[1]
            val vz = c[2] - a[2]
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len < 1e-6f) {
                i += 3
                continue
            }
            nx /= len; ny /= len; nz /= len
            bag.addVertex(a[0], a[1], a[2], nx, ny, nz, r, g, b)
            bag.addVertex(bb[0], bb[1], bb[2], nx, ny, nz, r, g, b)
            bag.addVertex(c[0], c[1], c[2], nx, ny, nz, r, g, b)
            i += 3
        }
    }

    // ------------------------------------------------------------------ config

    private data class SettingsData(
        val objectNames: Map<String, String>,
        val objectExtruders: Map<String, Int>,
        val plateObjects: List<String>,
        val buildItems: Map<String, List<FloatArray>>,
        val rootFile: String,
    )

    private fun parseSettings(zip: ZipFile): SettingsData {
        val names = mutableMapOf<String, String>()
        val extruders = mutableMapOf<String, Int>()
        val plateObjects = mutableListOf<String>()
        var currentObject: String? = null
        var inPart = false
        var currentPlate = false
        var plateIndex = 0
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
                        "plate" -> {
                            plateIndex++
                            currentPlate = plateIndex == 1
                        }
                        "model_instance" -> {
                            // object_id/identify_id приходят в дочерних metadata
                        }
                        "metadata" -> {
                            val key = attr(parser, "key")
                            val value = attr(parser, "value").orEmpty()
                            if (currentPlate && key == "object_id") {
                                if (value.isNotBlank()) plateObjects.add(value)
                            } else if (currentObject != null && !inPart) {
                                when (key) {
                                    "name" -> if (value.isNotBlank()) names[currentObject!!] = value
                                    "extruder" -> value.toIntOrNull()?.let { extruders[currentObject!!] = it }
                                }
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (parser.name) {
                        "part" -> inPart = false
                        "plate" -> { currentPlate = false; inPart = false }
                        "object" -> currentObject = null
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
        return SettingsData(names, extruders, plateObjects, buildItems, rootFile)
    }

    private fun parseFilamentColors(zip: ZipFile): List<Int> {
        val entry = zip.entries().asSequence()
            .firstOrNull { it.name.equals("Metadata/project_settings.config", true) }
            ?: return emptyList()
        val text = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
        return runCatching {
            val root = JSONObject(text)
            val arr = root.optJSONArray("filament_colour") ?: return emptyList()
            (0 until arr.length()).mapNotNull { index ->
                val value = arr.optString(index).trim().removePrefix("#")
                if (value.length < 6) null
                else runCatching { 0xFF000000.toInt() or value.take(6).toLong(16).toInt() }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------------ meshes

    private class RawObject(
        val positions: FloatBag = FloatBag(),
        val indices: IntBag = IntBag(),
        val components: MutableList<Component> = mutableListOf(),
    )

    private data class Component(val objectId: String, val path: String?, val transform: FloatArray?)

    private class MeshStore {
        val files = mutableMapOf<String, Map<String, RawObject>>()
        private val cache = mutableMapOf<Pair<String, String>, FlatMesh>()

        fun flatten(file: String, objectId: String, depth: Int = 0): FlatMesh {
            if (depth > 12) return FlatMesh(FloatArray(0), IntArray(0))
            val key = file to objectId
            cache[key]?.let { return it }
            val obj = files[file]?.get(objectId) ?: return FlatMesh(FloatArray(0), IntArray(0))
            val positions = FloatBag()
            val indices = IntBag()
            positions.addAll(obj.positions)
            indices.addAll(obj.indices)
            obj.components.forEach { component ->
                val childFile = resolve(file, component.path)
                val child = flatten(childFile, component.objectId, depth + 1)
                if (child.positions.isEmpty()) return@forEach
                val baseVertex = positions.size / 3
                val t = component.transform
                var i = 0
                while (i < child.positions.size) {
                    val p = if (t != null) applyTransform(t, child.positions, i)
                    else floatArrayOf(child.positions[i], child.positions[i + 1], child.positions[i + 2])
                    positions.add(p[0]); positions.add(p[1]); positions.add(p[2])
                    i += 3
                }
                var j = 0
                while (j < child.indices.size) {
                    indices.add(child.indices[j] + baseVertex)
                    indices.add(child.indices[j + 1] + baseVertex)
                    indices.add(child.indices[j + 2] + baseVertex)
                    j += 3
                }
            }
            val result = FlatMesh(positions.toArray(), indices.toArray())
            cache[key] = result
            return result
        }

        private fun resolve(current: String, raw: String?): String {
            val clean = raw?.trim()?.replace('\\', '/')?.removePrefix("/")
                ?.takeIf { it.isNotBlank() } ?: return current
            if (files.containsKey(clean)) return clean
            val dir = current.substringBeforeLast('/', "")
            val joined = if (dir.isBlank()) clean else "$dir/$clean"
            return if (files.containsKey(joined)) joined else clean
        }
    }

    private data class FlatMesh(val positions: FloatArray, val indices: IntArray)

    private fun loadMeshes(zip: ZipFile): MeshStore {
        val store = MeshStore()
        zip.entries().asSequence().filter { it.name.endsWith(".model", true) }.forEach { entry ->
            runCatching {
                val objects = mutableMapOf<String, RawObject>()
                val parser = Xml.newPullParser()
                parser.setInput(zip.getInputStream(entry), "UTF-8")
                var id: String? = null
                var current: RawObject? = null
                var inVertices = false
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "object" -> {
                                id = parser.getAttributeValue(null, "id")
                                current = RawObject()
                            }
                            "vertices" -> inVertices = true
                            "vertex" -> if (inVertices) {
                                val x = parser.getAttributeValue(null, "x")?.toFloatOrNull()
                                val y = parser.getAttributeValue(null, "y")?.toFloatOrNull()
                                val z = parser.getAttributeValue(null, "z")?.toFloatOrNull() ?: 0f
                                if (x != null && y != null) {
                                    current?.positions?.add(x); current?.positions?.add(y)
                                    current?.positions?.add(z)
                                }
                            }
                            "triangle" -> {
                                val a = parser.getAttributeValue(null, "v1")?.toIntOrNull()
                                val b = parser.getAttributeValue(null, "v2")?.toIntOrNull()
                                val c = parser.getAttributeValue(null, "v3")?.toIntOrNull()
                                if (a != null && b != null && c != null) {
                                    current?.indices?.add(a); current?.indices?.add(b)
                                    current?.indices?.add(c)
                                }
                            }
                            "component" -> {
                                val target = parser.getAttributeValue(null, "objectid")
                                val path = attr(parser, "path")
                                val transform = parseTransform(parser.getAttributeValue(null, "transform"))
                                if (target != null) current?.components?.add(Component(target, path, transform))
                            }
                        }
                    } else if (event == XmlPullParser.END_TAG) {
                        when (parser.name) {
                            "vertices" -> inVertices = false
                            "object" -> {
                                val objId = id
                                val obj = current
                                if (objId != null && obj != null) objects[objId] = obj
                                id = null
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

    // ------------------------------------------------------------------ utils

    private val IDENTITY = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f,
        0f, 0f, 0f,
    )

    private fun parseTransform(raw: String?): FloatArray? {
        val values = raw?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toFloatOrNull() } ?: return null
        if (values.size < 12) return null
        return values.take(12).toFloatArray()
    }

    private fun applyTransform(m: FloatArray, positions: FloatArray, index: Int): FloatArray {
        val x = positions[index]
        val y = positions[index + 1]
        val z = positions[index + 2]
        return floatArrayOf(
            m[0] * x + m[3] * y + m[6] * z + m[9],
            m[1] * x + m[4] * y + m[7] * z + m[10],
            m[2] * x + m[5] * y + m[8] * z + m[11],
        )
    }

    private fun attr(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == name) return parser.getAttributeValue(i)
        }
        return null
    }

    private class FloatBag(initial: Int = 4096) {
        var data = FloatArray(initial)
            private set
        var size = 0
            private set

        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }

        fun addAll(other: FloatBag) {
            if (size + other.size > data.size) {
                data = data.copyOf((size + other.size).coerceAtLeast(data.size * 2))
            }
            System.arraycopy(other.data, 0, data, size, other.size)
            size += other.size
        }

        fun addVertex(
            x: Float, y: Float, z: Float,
            nx: Float, ny: Float, nz: Float,
            r: Float, g: Float, b: Float,
        ) {
            add(x); add(y); add(z); add(nx); add(ny); add(nz); add(r); add(g); add(b)
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }

    private class IntBag(initial: Int = 4096) {
        var data = IntArray(initial)
            private set
        var size = 0
            private set

        fun add(value: Int) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }

        fun addAll(other: IntBag) {
            if (size + other.size > data.size) {
                data = data.copyOf((size + other.size).coerceAtLeast(data.size * 2))
            }
            System.arraycopy(other.data, 0, data, size, other.size)
            size += other.size
        }

        fun toArray(): IntArray = data.copyOf(size)
    }
}
