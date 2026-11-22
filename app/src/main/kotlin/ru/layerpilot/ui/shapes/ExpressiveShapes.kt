@file:Suppress("unused")

package ru.layerpilot.ui.shapes

import androidx.compose.ui.geometry.Offset
import android.graphics.Matrix
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.transformed
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shape library из Material 3 Expressive (androidx.compose.material3.MaterialShapes).
 * Все фигуры нормализованы: вписаны в квадрат 0..1.
 *
 * Используется индикатором загрузки и декоративными акцентами интерфейса.
 */
object ExpressiveShapes {
    private val cornerRound15 = CornerRounding(radius = .15f)
    private val cornerRound20 = CornerRounding(radius = .2f)
    private val cornerRound30 = CornerRounding(radius = .3f)
    private val cornerRound50 = CornerRounding(radius = .5f)
    private val cornerRound100 = CornerRounding(radius = 1f)

    private val rotateNeg45 = Matrix().apply { setRotate(-45f) }
    private val rotateNeg90 = Matrix().apply { setRotate(-90f) }
    private val rotateNeg135 = Matrix().apply { setRotate(-135f) }

    val Circle: RoundedPolygon by lazy { circle().normalized() }
    val Square: RoundedPolygon by lazy { square().normalized() }
    val Slanted: RoundedPolygon by lazy { slanted().normalized() }
    val Arch: RoundedPolygon by lazy { arch().normalized() }
    val Fan: RoundedPolygon by lazy { fan().normalized() }
    val Arrow: RoundedPolygon by lazy { arrow().normalized() }
    val SemiCircle: RoundedPolygon by lazy { semiCircle().normalized() }
    val Oval: RoundedPolygon by lazy { oval().normalized() }
    val Pill: RoundedPolygon by lazy { pill().normalized() }
    val Triangle: RoundedPolygon by lazy { triangle().normalized() }
    val Diamond: RoundedPolygon by lazy { diamond().normalized() }
    val ClamShell: RoundedPolygon by lazy { clamShell().normalized() }
    val Pentagon: RoundedPolygon by lazy { pentagon().normalized() }
    val Gem: RoundedPolygon by lazy { gem().normalized() }
    val Sunny: RoundedPolygon by lazy { sunny().normalized() }
    val VerySunny: RoundedPolygon by lazy { verySunny().normalized() }
    val Cookie4Sided: RoundedPolygon by lazy { cookie4().normalized() }
    val Cookie6Sided: RoundedPolygon by lazy { cookie6().normalized() }
    val Cookie7Sided: RoundedPolygon by lazy { cookie7().normalized() }
    val Cookie9Sided: RoundedPolygon by lazy { cookie9().normalized() }
    val Cookie12Sided: RoundedPolygon by lazy { cookie12().normalized() }
    val Ghostish: RoundedPolygon by lazy { ghostish().normalized() }
    val Clover4Leaf: RoundedPolygon by lazy { clover4().normalized() }
    val Clover8Leaf: RoundedPolygon by lazy { clover8().normalized() }
    val Burst: RoundedPolygon by lazy { burst().normalized() }
    val SoftBurst: RoundedPolygon by lazy { softBurst().normalized() }
    val Boom: RoundedPolygon by lazy { boom().normalized() }
    val SoftBoom: RoundedPolygon by lazy { softBoom().normalized() }
    val Flower: RoundedPolygon by lazy { flower().normalized() }
    val Puffy: RoundedPolygon by lazy { puffy().normalized() }
    val PuffyDiamond: RoundedPolygon by lazy { puffyDiamond().normalized() }
    val PixelCircle: RoundedPolygon by lazy { pixelCircle().normalized() }
    val PixelTriangle: RoundedPolygon by lazy { pixelTriangle().normalized() }
    val Bun: RoundedPolygon by lazy { bun().normalized() }
    val Heart: RoundedPolygon by lazy { heart().normalized() }

    /** Набор фигур для индикатора загрузки — как в Material 3 Expressive. */
    val LoadingSequence: List<RoundedPolygon> = listOf(
        SoftBurst,          // 0 ms
        Cookie9Sided,       // 650 ms
        Pentagon,           // 1300 ms
        Pill,               // 1950 ms
        Sunny,              // 2600 ms
        Cookie4Sided,       // 3250 ms
        Oval,               // 3900 ms
    )

    private fun circle(numVertices: Int = 10) = RoundedPolygon.circle(numVertices = numVertices)

    private fun square() =
        RoundedPolygon.rectangle(width = 1f, height = 1f, rounding = cornerRound30)

    private fun slanted() = customPolygon(
        listOf(
            PointNRound(Offset(0.926f, 0.970f), CornerRounding(0.189f, 0.811f)),
            PointNRound(Offset(-0.021f, 0.967f), CornerRounding(0.187f, 0.057f)),
        ), 2,
    )

    private fun arch(): RoundedPolygon =
        RoundedPolygon(
            numVertices = 4,
            perVertexRounding =
                listOf(cornerRound100, cornerRound100, cornerRound20, cornerRound20),
        ).transformed(rotateNeg135)

    private fun fan() = customPolygon(
        listOf(
            PointNRound(Offset(1.004f, 1.000f), CornerRounding(0.148f, 0.417f)),
            PointNRound(Offset(0.000f, 1.000f), CornerRounding(0.151f)),
            PointNRound(Offset(0.000f, -0.003f), CornerRounding(0.148f)),
            PointNRound(Offset(0.978f, 0.020f), CornerRounding(0.803f)),
        ), 1,
    )

    private fun arrow() = customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 0.892f), CornerRounding(0.313f)),
            PointNRound(Offset(-0.216f, 1.050f), CornerRounding(0.207f)),
            PointNRound(Offset(0.499f, -0.160f), CornerRounding(0.215f, 1.000f)),
            PointNRound(Offset(1.225f, 1.060f), CornerRounding(0.211f)),
        ), 1,
    )

    private fun semiCircle() = RoundedPolygon.rectangle(
        width = 1.6f,
        height = 1f,
        perVertexRounding = listOf(cornerRound20, cornerRound20, cornerRound100, cornerRound100),
    )

    private fun oval() =
        RoundedPolygon.circle().transformed(Matrix().apply { setScale(1f, 0.64f) })
            .transformed(rotateNeg45)

    private fun pill() = customPolygon(
        listOf(
            PointNRound(Offset(0.961f, 0.039f), CornerRounding(0.426f)),
            PointNRound(Offset(1.001f, 0.428f)),
            PointNRound(Offset(1.000f, 0.609f), CornerRounding(1.000f)),
        ), reps = 2, mirroring = true,
    )

    private fun triangle() =
        RoundedPolygon(numVertices = 3, rounding = cornerRound20).transformed(rotateNeg90)

    private fun diamond() = customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 1.096f), CornerRounding(0.151f, 0.524f)),
            PointNRound(Offset(0.040f, 0.500f), CornerRounding(0.159f)),
        ), 2,
    )

    private fun clamShell() = customPolygon(
        listOf(
            PointNRound(Offset(0.171f, 0.841f), CornerRounding(0.159f)),
            PointNRound(Offset(-0.020f, 0.500f), CornerRounding(0.140f)),
            PointNRound(Offset(0.170f, 0.159f), CornerRounding(0.159f)),
        ), 2,
    )

    private fun pentagon() = customPolygon(
        listOf(
            PointNRound(Offset(0.500f, -0.009f), CornerRounding(0.172f)),
            PointNRound(Offset(1.030f, 0.365f), CornerRounding(0.164f)),
            PointNRound(Offset(0.828f, 0.970f), CornerRounding(0.169f)),
        ), reps = 1, mirroring = true,
    )

    private fun gem() = customPolygon(
        listOf(
            PointNRound(Offset(0.499f, 1.023f), CornerRounding(0.241f, 0.778f)),
            PointNRound(Offset(-0.005f, 0.792f), CornerRounding(0.208f)),
            PointNRound(Offset(0.073f, 0.258f), CornerRounding(0.228f)),
            PointNRound(Offset(0.433f, -0.000f), CornerRounding(0.491f)),
        ), 1, mirroring = true,
    )

    private fun sunny() = RoundedPolygon.star(
        numVerticesPerRadius = 8, innerRadius = .8f, rounding = cornerRound15,
    )

    private fun verySunny() = customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 1.080f), CornerRounding(0.085f)),
            PointNRound(Offset(0.358f, 0.843f), CornerRounding(0.085f)),
        ), 8,
    )

    private fun cookie4() = customPolygon(
        listOf(
            PointNRound(Offset(1.237f, 1.236f), CornerRounding(0.258f)),
            PointNRound(Offset(0.500f, 0.918f), CornerRounding(0.233f)),
        ), 4,
    )

    private fun cookie6() = customPolygon(
        listOf(
            PointNRound(Offset(0.723f, 0.884f), CornerRounding(0.394f)),
            PointNRound(Offset(0.500f, 1.099f), CornerRounding(0.398f)),
        ), 6,
    )

    private fun cookie7() = RoundedPolygon.star(
        numVerticesPerRadius = 7, innerRadius = .75f, rounding = cornerRound50,
    ).transformed(rotateNeg90)

    private fun cookie9() = RoundedPolygon.star(
        numVerticesPerRadius = 9, innerRadius = .8f, rounding = cornerRound50,
    ).transformed(rotateNeg90)

    private fun cookie12() = RoundedPolygon.star(
        numVerticesPerRadius = 12, innerRadius = .8f, rounding = cornerRound50,
    ).transformed(rotateNeg90)

    private fun ghostish() = customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 0f), CornerRounding(1.000f)),
            PointNRound(Offset(1f, 0f), CornerRounding(1.000f)),
            PointNRound(Offset(1f, 1.140f), CornerRounding(0.254f, 0.106f)),
            PointNRound(Offset(0.575f, 0.906f), CornerRounding(0.253f)),
        ), reps = 1, mirroring = true,
    )

    private fun clover4() = customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 0.074f)),
            PointNRound(Offset(0.725f, -0.099f), CornerRounding(0.476f)),
        ), reps = 4, mirroring = true,
    )

    private fun clover8() = customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 0.036f)),
            PointNRound(Offset(0.758f, -0.101f), CornerRounding(0.209f)),
        ), reps = 8,
    )

    private fun burst() = customPolygon(
        listOf(
            PointNRound(Offset(0.500f, -0.006f), CornerRounding(0.006f)),
            PointNRound(Offset(0.592f, 0.158f), CornerRounding(0.006f)),
        ), reps = 12,
    )

    private fun softBurst() = customPolygon(
        listOf(
            PointNRound(Offset(0.193f, 0.277f), CornerRounding(0.053f)),
            PointNRound(Offset(0.176f, 0.055f), CornerRounding(0.053f)),
        ), reps = 10,
    )

    private fun boom() = customPolygon(
        listOf(
            PointNRound(Offset(0.457f, 0.296f), CornerRounding(0.007f)),
            PointNRound(Offset(0.500f, -0.051f), CornerRounding(0.007f)),
        ), reps = 15,
    )

    private fun softBoom() = customPolygon(
        listOf(
            PointNRound(Offset(0.733f, 0.454f)),
            PointNRound(Offset(0.839f, 0.437f), CornerRounding(0.532f)),
            PointNRound(Offset(0.949f, 0.449f), CornerRounding(0.439f, 1.000f)),
            PointNRound(Offset(0.998f, 0.478f), CornerRounding(0.174f)),
        ), reps = 16, mirroring = true,
    )

    private fun flower() = customPolygon(
        listOf(
            PointNRound(Offset(0.370f, 0.187f)),
            PointNRound(Offset(0.416f, 0.049f), CornerRounding(0.381f)),
            PointNRound(Offset(0.479f, 0.001f), CornerRounding(0.095f)),
        ), reps = 8, mirroring = true,
    )

    private fun puffy(): RoundedPolygon {
        val m = Matrix().apply { setScale(1f, 0.742f) }
        return customPolygon(
            listOf(
                PointNRound(Offset(0.500f, 0.053f)),
                PointNRound(Offset(0.545f, -0.040f), CornerRounding(0.405f)),
                PointNRound(Offset(0.670f, -0.035f), CornerRounding(0.426f)),
                PointNRound(Offset(0.717f, 0.066f), CornerRounding(0.574f)),
                PointNRound(Offset(0.722f, 0.128f)),
                PointNRound(Offset(0.777f, 0.002f), CornerRounding(0.360f)),
                PointNRound(Offset(0.914f, 0.149f), CornerRounding(0.660f)),
                PointNRound(Offset(0.926f, 0.289f), CornerRounding(0.660f)),
                PointNRound(Offset(0.881f, 0.346f)),
                PointNRound(Offset(0.940f, 0.344f), CornerRounding(0.126f)),
                PointNRound(Offset(1.003f, 0.437f), CornerRounding(0.255f)),
            ), reps = 2, mirroring = true,
        ).transformed(m)
    }

    private fun puffyDiamond() = customPolygon(
        listOf(
            PointNRound(Offset(0.870f, 0.130f), CornerRounding(0.146f)),
            PointNRound(Offset(0.818f, 0.357f)),
            PointNRound(Offset(1.000f, 0.332f), CornerRounding(0.853f)),
        ), reps = 4, mirroring = true,
    )

    private fun pixelCircle() = customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 0.000f)),
            PointNRound(Offset(0.704f, 0.000f)),
            PointNRound(Offset(0.704f, 0.065f)),
            PointNRound(Offset(0.843f, 0.065f)),
            PointNRound(Offset(0.843f, 0.148f)),
            PointNRound(Offset(0.926f, 0.148f)),
            PointNRound(Offset(0.926f, 0.296f)),
            PointNRound(Offset(1.000f, 0.296f)),
        ), reps = 2, mirroring = true,
    )

    private fun pixelTriangle() = customPolygon(
        listOf(
            PointNRound(Offset(0.110f, 0.500f)),
            PointNRound(Offset(0.113f, 0.000f)),
            PointNRound(Offset(0.287f, 0.000f)),
            PointNRound(Offset(0.287f, 0.087f)),
            PointNRound(Offset(0.421f, 0.087f)),
            PointNRound(Offset(0.421f, 0.170f)),
            PointNRound(Offset(0.560f, 0.170f)),
            PointNRound(Offset(0.560f, 0.265f)),
            PointNRound(Offset(0.674f, 0.265f)),
            PointNRound(Offset(0.675f, 0.344f)),
            PointNRound(Offset(0.789f, 0.344f)),
            PointNRound(Offset(0.789f, 0.439f)),
            PointNRound(Offset(0.888f, 0.439f)),
        ), reps = 1, mirroring = true,
    )

    private fun bun() = customPolygon(
        listOf(
            PointNRound(Offset(0.796f, 0.500f)),
            PointNRound(Offset(0.853f, 0.518f), CornerRounding(1f)),
            PointNRound(Offset(0.992f, 0.631f), CornerRounding(1f)),
            PointNRound(Offset(0.968f, 1.000f), CornerRounding(1f)),
        ), reps = 2, mirroring = true,
    )

    private fun heart() = customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 0.268f), CornerRounding(0.016f)),
            PointNRound(Offset(0.792f, -0.066f), CornerRounding(0.958f)),
            PointNRound(Offset(1.064f, 0.276f), CornerRounding(1.000f)),
            PointNRound(Offset(0.501f, 0.946f), CornerRounding(0.129f)),
        ), reps = 1, mirroring = true,
    )

    private data class PointNRound(
        val o: Offset,
        val r: CornerRounding = CornerRounding.Unrounded,
    )

    private fun doRepeat(
        points: List<PointNRound>,
        reps: Int,
        center: Offset,
        mirroring: Boolean,
    ): List<PointNRound> =
        if (mirroring) {
            buildList {
                val angles = points.map { (it.o - center).angleDegrees() }
                val distances = points.map { (it.o - center).getDistance() }
                val actualReps = reps * 2
                val sectionAngle = 360f / actualReps
                repeat(actualReps) {
                    points.indices.forEach { index ->
                        val i = if (it % 2 == 0) index else points.lastIndex - index
                        if (i > 0 || it % 2 == 0) {
                            val a = (sectionAngle * it +
                                if (it % 2 == 0) angles[i]
                                else sectionAngle - angles[i] + 2 * angles[0]).toRadians()
                            val finalPoint = Offset(cos(a), sin(a)) * distances[i] + center
                            add(PointNRound(finalPoint, points[i].r))
                        }
                    }
                }
            }
        } else {
            points.size.let { np ->
                (0 until np * reps).map {
                    val point = points[it % np].o.rotateDegrees((it / np) * 360f / reps, center)
                    PointNRound(point, points[it % np].r)
                }
            }
        }

    private fun Offset.rotateDegrees(angle: Float, center: Offset = Offset.Zero) =
        angle.toRadians().let { a ->
            val off = this - center
            Offset(off.x * cos(a) - off.y * sin(a), off.x * sin(a) + off.y * cos(a)) + center
        }

    private fun Float.toRadians(): Float = this / 360f * 2 * PI.toFloat()

    private fun Offset.angleDegrees() = atan2(y, x) * 180f / PI.toFloat()

    private fun customPolygon(
        pnr: List<PointNRound>,
        reps: Int,
        center: Offset = Offset(0.5f, 0.5f),
        mirroring: Boolean = false,
    ): RoundedPolygon {
        val actualPoints = doRepeat(pnr, reps, center, mirroring)
        return RoundedPolygon(
            vertices = FloatArray(actualPoints.size * 2) { ix ->
                actualPoints[ix / 2].o.let { if (ix % 2 == 0) it.x else it.y }
            },
            perVertexRounding = buildList { for (p in actualPoints) add(p.r) },
            centerX = center.x,
            centerY = center.y,
        )
    }
}
