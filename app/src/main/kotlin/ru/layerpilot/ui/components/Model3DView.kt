package ru.layerpilot.ui.components

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import ru.layerpilot.data.models.SceneBounds
import ru.layerpilot.data.models.ThreeMfScene
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Интерактивная 3D-пластина: вращение одним пальцем, масштаб двумя. */
@Composable
fun Model3DView(
    scene: ThreeMfScene,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF0C110D),
    bedColor: Color = Color(0xFF161D18),
    gridColor: Color = Color(0xFF3A4A3C),
) {
    key(scene) {
        val colors = remember(background, bedColor, gridColor) {
            intArrayOf(background.toArgb(), bedColor.toArgb(), gridColor.toArgb())
        }
        var glView by remember { mutableStateOf<Model3DGLView?>(null) }
        DisposableEffect(Unit) {
            onDispose { glView?.onPause() }
        }
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                Model3DGLView(context, scene, colors).also {
                    glView = it
                    it.onResume()
                }
            },
            update = { view ->
                view.setPalette(colors)
            },
        )
    }
}

private class Model3DGLView(
    context: Context,
    scene: ThreeMfScene,
    palette: IntArray,
) : GLSurfaceView(context) {

    private val renderer = Model3DRenderer(scene, palette)
    private var lastX = 0f
    private var lastY = 0f
    private var lastDistance = 0f

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 0, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        isFocusable = true
    }

    fun setPalette(colors: IntArray) {
        renderer.setPalette(colors)
        requestRender()
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                lastDistance = 0f
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                lastDistance = spacing(event)
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val distance = spacing(event)
                    if (lastDistance > 1f && distance > 1f) {
                        renderer.zoom(lastDistance / distance)
                    }
                    lastDistance = distance
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    renderer.orbit(dx, dy)
                    lastX = event.x
                    lastY = event.y
                }
                requestRender()
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                lastDistance = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun spacing(event: android.view.MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

private class Model3DRenderer(
    private val scene: ThreeMfScene,
    palette: IntArray,
) : GLSurfaceView.Renderer {

    private var background = palette[0]
    private var bedColor = palette[1]
    private var gridColor = palette[2]

    private var program = 0
    private var aPosition = 0
    private var aNormal = 0
    private var aColor = 0
    private var uMvp = 0

    private var meshBuffer: FloatBuffer? = null
    private var plateBuffer: FloatBuffer? = null
    private var plateVertices = 0

    private val projection = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vp = FloatArray(16)

    private var yaw = -52f
    private var pitch = 38f
    private var distance = 300f
    private var fitted = false
    private val target = floatArrayOf(128f, 128f, 8f)

    init {
        val bed = max(scene.bedWidth, scene.bedHeight)
        val b: SceneBounds = scene.bounds
        target[0] = b.centerX
        target[1] = b.centerY
        target[2] = max(b.centerZ, b.sizeZ * 0.35f)
        // Сначала показываем модель крупно, но оставляем край стола для масштаба.
        distance = max(b.diagonal * 2.7f, 130f).coerceAtMost(bed * 2.4f)
    }

    fun setPalette(colors: IntArray) {
        background = colors[0]
        bedColor = colors[1]
        gridColor = colors[2]
    }

    fun orbit(dx: Float, dy: Float) {
        yaw -= dx * 0.35f
        pitch = (pitch + dy * 0.3f).coerceIn(8f, 82f)
    }

    fun zoom(scale: Float) {
        distance = (distance * scale).coerceIn(35f, 2400f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(red(background), green(background), blue(background), 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        program = buildProgram()
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        meshBuffer = scene.vertices.toBuffer()
        plateBuffer = buildPlate()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projection, 0, 43f, ratio, 1f, 4000f)
        if (!fitted) {
            fitted = true
            val radius = (scene.bounds.diagonal / 2f).coerceAtLeast(35f)
            val vFov = Math.toRadians(43.0)
            val hFov = 2.0 * kotlin.math.atan(kotlin.math.tan(vFov / 2.0) * ratio)
            val fov = kotlin.math.min(vFov, hFov)
            distance = (radius / kotlin.math.sin(fov / 2.0)).toFloat().coerceIn(70f, 2500f)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(red(background), green(background), blue(background), 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        updateCamera()
        Matrix.multiplyMM(vp, 0, projection, 0, viewMatrix, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glEnableVertexAttribArray(aColor)

        plateBuffer?.let { buffer ->
            bind(buffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            GLES20.glLineWidth(1.1f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 6, plateVertices - 6)
        }
        meshBuffer?.let { buffer ->
            bind(buffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, scene.vertexCount)
        }

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aNormal)
        GLES20.glDisableVertexAttribArray(aColor)
    }

    private fun bind(buffer: FloatBuffer) {
        buffer.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 9 * 4, buffer)
        buffer.position(3)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 9 * 4, buffer)
        buffer.position(6)
        GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 9 * 4, buffer)
    }

    private fun updateCamera() {
        val cp = cos(Math.toRadians(pitch.toDouble())).toFloat()
        val sp = sin(Math.toRadians(pitch.toDouble())).toFloat()
        val cy = cos(Math.toRadians(yaw.toDouble())).toFloat()
        val sy = sin(Math.toRadians(yaw.toDouble())).toFloat()
        val eyeX = target[0] + distance * cp * cy
        val eyeY = target[1] + distance * cp * sy
        val eyeZ = target[2] + distance * sp
        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,
            target[0], target[1], target[2],
            0f, 0f, 1f,
        )
    }

    private fun buildPlate(): FloatBuffer {
        val bag = ArrayList<Float>()
        fun vertex(x: Float, y: Float, z: Float, color: Int) {
            bag.add(x); bag.add(y); bag.add(z)
            bag.add(0f); bag.add(0f); bag.add(1f)
            bag.add(red(color)); bag.add(green(color)); bag.add(blue(color))
        }
        val bed = bedColor
        val z = -0.25f
        val w = scene.bedWidth
        val h = scene.bedHeight
        // Две треугольные площадки стола.
        vertex(0f, 0f, z, bed); vertex(w, 0f, z, bed); vertex(w, h, z, bed)
        vertex(0f, 0f, z, bed); vertex(w, h, z, bed); vertex(0f, h, z, bed)
        val grid = gridColor
        val step = 10f
        var x = 0f
        while (x <= w + 0.01f) {
            vertex(x, 0f, 0f, grid); vertex(x, h, 0f, grid)
            x += step
        }
        var y = 0f
        while (y <= h + 0.01f) {
            vertex(0f, y, 0f, grid); vertex(w, y, 0f, grid)
            y += step
        }
        // Контур стола поярче.
        val edge = gridColor
        vertex(0f, 0f, 0.05f, edge); vertex(w, 0f, 0.05f, edge)
        vertex(w, 0f, 0.05f, edge); vertex(w, h, 0.05f, edge)
        vertex(w, h, 0.05f, edge); vertex(0f, h, 0.05f, edge)
        vertex(0f, h, 0.05f, edge); vertex(0f, 0f, 0.05f, edge)
        plateVertices = bag.size / 9
        return bag.toFloatArray().toBuffer()
    }

    private fun buildProgram(): Int {
        val vertex = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            uniform mat4 uMvp;
            varying vec3 vNormal;
            varying vec3 vColor;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vNormal = aNormal;
                vColor = aColor;
            }
        """.trimIndent()
        val fragment = """
            precision mediump float;
            varying vec3 vNormal;
            varying vec3 vColor;
            void main() {
                vec3 n = normalize(vNormal);
                float diff = max(dot(n, normalize(vec3(-0.35, -0.55, 0.78))), 0.0);
                float light = 0.48 + 0.62 * diff;
                gl_FragColor = vec4(vColor * light, 1.0);
            }
        """.trimIndent()
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun FloatArray.toBuffer(): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(this)
        buffer.position(0)
        return buffer
    }

    private fun red(color: Int): Float = ((color shr 16) and 0xFF) / 255f
    private fun green(color: Int): Float = ((color shr 8) and 0xFF) / 255f
    private fun blue(color: Int): Float = (color and 0xFF) / 255f
}
