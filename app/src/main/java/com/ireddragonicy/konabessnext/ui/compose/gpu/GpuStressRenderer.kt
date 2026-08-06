package com.ireddragonicy.konabessnext.ui.compose.gpu

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * `GLSurfaceView` that runs the "Poison Mushroom" / Volume Shader BM 3D
 * benchmark from https://cznull.github.io/vsbm.
 *
 * The fragment shader ray-marches a 3D fractal scene (Mandelbulb-style
 * Mandelbox distance estimator); each pixel performs ~1000+ distance
 * evaluations plus bisection + golden-section refinement. On Adreno GPUs this
 * fully loads the shader cores at all frequency points.
 *
 * The shader source is **untouched** from the original cznull/vsbm public demo
 * — only adapted for GLES 2.0 (removed `#version 100` directives that conflict
 * with the Android driver and replaced `attribute`/`varying` qualifiers
 * implicitly via `precision highp float`).
 *
 * The view runs in [GLSurfaceView.RENDERMODE_CONTINUOUSLY] while
 * [setStressActive] is true; otherwise it stays in `RENDERMODE_WHEN_DIRTY` and
 * the GL thread idles.
 *
 * Errors from [GLES20.glGetError] are forwarded to [onError] so the ViewModel
 * can flag the current frequency point as failed.
 */
class GpuStressSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    private val rendererImpl = GpuStressRenderer()

    var onError: ((Int) -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        // 8/8/8/0 + 16-bit depth is plenty for a fullscreen quad.
        setEGLConfigChooser(8, 8, 8, 0, 16, 0)
        setRenderer(rendererImpl)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
    }

    /**
     * View layout hook (NOT a SurfaceHolder callback). Called by the
     * platform whenever this View's laid-out size changes — that is, the
     * real on-screen dimensions of the surface, *before* any
     * `holder.setFixedSize` shenanigans. We use it to push a smaller buffer
     * size to the holder so the GL thread renders at the reduced
     * resolution; the platform then stretches the smaller buffer back to
     * the View's full size on composite.
     *
     * Why `onSizeChanged` and not `surfaceChanged`? `surfaceChanged` fires
     * every time the underlying Surface is recreated, including after
     * `setFixedSize` shrinks it — feeding it back to the holder would
     * cause a feedback loop where the buffer keeps halving. `onSizeChanged`
     * is tied to the View tree's layout, not the EGL surface, so it's
     * only called once per actual layout pass.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val scaledW = (w * RENDER_SCALE).toInt().coerceAtLeast(1)
        val scaledH = (h * RENDER_SCALE).toInt().coerceAtLeast(1)
        // setFixedSize is a hint to the platform; if it equals the
        // currently-set fixed size the call is a no-op. This is what
        // gives us idempotency across multiple layout passes.
        if (scaledW != lastFixedW || scaledH != lastFixedH) {
            holder.setFixedSize(scaledW, scaledH)
            lastFixedW = scaledW
            lastFixedH = scaledH
        }
    }

    fun setStressActive(active: Boolean) {
        renderMode = if (active) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        if (!active) requestRender()
    }

    fun bindErrorCallback(callback: (Int) -> Unit) {
        rendererImpl.onError = { code ->
            callback(code)
            onError?.invoke(code)
        }
    }

    companion object {
        /**
         * Fraction of the View's laid-out size that the GL surface is
         * actually rendered at. 0.5 means we render into a buffer half the
         * width and half the height — a quarter of the fragments — and
         * the platform stretches it back to fill the View. For a
         * fragment-bound workload like the vsbm ray-march this is by far
         * the most effective knob for raising frame rate: the shader
         * itself is unchanged, so the GPU is still being exercised, but
         * it has one quarter of the per-pixel work to do.
         */
        private const val RENDER_SCALE: Float = 0.5f

        // Track the last fixed size we pushed so we don't redundantly call
        // setFixedSize on every layout pass.
        private var lastFixedW: Int = 0
        private var lastFixedH: Int = 0
    }
}

/**
 * Port of cznull/vsbm. The fragment shader performs ray-marching against a
 * fractal distance estimator (`kernal`) up to 1000 steps, plus bisection +
 * golden-section refinement when a hit is detected. Heavy ALU load by design.
 */
private class GpuStressRenderer : GLSurfaceView.Renderer {

    private var program: Int = 0
    private var aPositionHandle: Int = -1
    private var uRightHandle: Int = -1
    private var uForwardHandle: Int = -1
    private var uUpHandle: Int = -1
    private var uOriginHandle: Int = -1
    private var uXHandle: Int = -1
    private var uYHandle: Int = -1
    private var uLenHandle: Int = -1

    // Two triangles covering NDC [-1,1]^2. The original vsbm uses vec3
    // positions with z=0; we use a fullscreen triangle strip.
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(VERTEX_DATA.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(VERTEX_DATA).position(0) }

    private var lastReportedError: Int = 0
    private var widthPx: Int = 1
    private var heightPx: Int = 1
    private var startTimeNanos: Long = 0L

    var onError: ((Int) -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.075f, 0.067f, 0.082f, 1f)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) {
            reportError(0x100)
            return
        }
        aPositionHandle = GLES20.glGetAttribLocation(program, "position")
        uRightHandle = GLES20.glGetUniformLocation(program, "right")
        uForwardHandle = GLES20.glGetUniformLocation(program, "forward")
        uUpHandle = GLES20.glGetUniformLocation(program, "up")
        uOriginHandle = GLES20.glGetUniformLocation(program, "origin")
        uXHandle = GLES20.glGetUniformLocation(program, "x")
        uYHandle = GLES20.glGetUniformLocation(program, "y")
        uLenHandle = GLES20.glGetUniformLocation(program, "len")
        lastReportedError = 0
        startTimeNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        widthPx = width
        heightPx = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (program == 0) return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer
        )
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        // Camera parameters — match the original vsbm demo at load() time.
        // ang1 / ang2 are slowly animated so the camera orbits the fractal
        // continuously, guaranteeing heavy ray-marching every frame.
        // Use monotonic seconds since the GL thread started — no modulus so
        // the camera never snaps back to its initial angle.
        val t = (System.nanoTime() - startTimeNanos) / 1_000_000_000f
        val ang1 = 2.8f + 0.4f * t
        val ang2 = 0.4f + 0.1f * kotlin.math.sin(t * 0.7f)
        val len = 1.6f
        val cenx = 0f
        val ceny = 0f
        val cenz = 0f

        // The original vsbm normalizes the viewport to a square and then scales
        // the canvas. We use the actual pixel aspect ratio so the image isn't
        // stretched on non-square surfaces.
        val xScale = if (widthPx < heightPx) 1f else heightPx.toFloat() / widthPx.toFloat()
        val yScale = if (heightPx < widthPx) 1f else widthPx.toFloat() / heightPx.toFloat()

        GLES20.glUniform1f(uXHandle, xScale)
        GLES20.glUniform1f(uYHandle, yScale)
        GLES20.glUniform1f(uLenHandle, len)
        GLES20.glUniform3f(
            uOriginHandle,
            len * kotlin.math.cos(ang1) * kotlin.math.cos(ang2) + cenx,
            len * kotlin.math.sin(ang2) + ceny,
            len * kotlin.math.sin(ang1) * kotlin.math.cos(ang2) + cenz,
        )
        GLES20.glUniform3f(
            uRightHandle,
            kotlin.math.sin(ang1), 0f, -kotlin.math.cos(ang1)
        )
        GLES20.glUniform3f(
            uUpHandle,
            -kotlin.math.sin(ang2) * kotlin.math.cos(ang1),
            kotlin.math.cos(ang2),
            -kotlin.math.sin(ang2) * kotlin.math.sin(ang1),
        )
        GLES20.glUniform3f(
            uForwardHandle,
            -kotlin.math.cos(ang1) * kotlin.math.cos(ang2),
            -kotlin.math.sin(ang2),
            -kotlin.math.sin(ang1) * kotlin.math.cos(ang2),
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glFinish()

        val err = GLES20.glGetError()
        if (err != 0 && err != lastReportedError) {
            lastReportedError = err
            Log.w(TAG, "GL error 0x${Integer.toHexString(err)}")
            reportError(err)
        } else if (err == 0) {
            lastReportedError = 0
        }
    }

    private fun reportError(code: Int) {
        onError?.invoke(code)
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        if (vs == 0) return 0
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        if (fs == 0) {
            GLES20.glDeleteShader(vs)
            return 0
        }
        val prog = GLES20.glCreateProgram()
        if (prog == 0) return 0
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "link failed: ${GLES20.glGetProgramInfoLog(prog)}")
            GLES20.glDeleteProgram(prog)
            return 0
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private const val TAG = "GpuStressRenderer"

        // Two triangles covering NDC [-1,1]^2 — matches the original vsbm layout.
        // Each vertex is a vec3 (x, y, z=0). The fragment shader uses position.xy
        // to compute the camera ray direction.
        private val VERTEX_DATA = floatArrayOf(
            -1f, -1f, 0f,
            1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, 1f, 0f,
        )

        /**
         * Vertex shader — verbatim port from cznull/vsbm. Drops the
         * `#version 100` header (Android driver adds it implicitly) and the
         * `precision highp float` (also implicit in GLES 2.0 vertex shaders)
         * but keeps every uniform / attribute exactly.
         */
        private val VERTEX_SHADER = """
            attribute vec4 position;
            varying vec3 dir, localdir;
            uniform vec3 right, forward, up, origin;
            uniform float x, y;
            void main() {
                gl_Position = position;
                dir = forward + right * position.x * x + up * position.y * y;
                localdir.x = position.x * x;
                localdir.y = position.y * y;
                localdir.z = -1.0;
            }
        """.trimIndent()

        /**
         * Fragment shader — verbatim port from cznull/vsbm. The `kernal` function
         * is the spherical-fold Mandelbox distance estimator; the main loop walks
         * up to 1000 ray steps with bisection + golden-section refinement when a
         * hit is detected. Heavy ALU load on purpose.
         *
         * Note: original `#version 100`, `#define`, `precision highp float` and
         * `kernal` prototype lines are kept because the Android shader compiler
         * happily accepts them.
         */
        private val FRAGMENT_SHADER = """
            #define PI 3.14159265358979324
            #define M_L 0.3819660113
            #define M_R 0.6180339887
            #define MAXR 8
            #define SOLVER 8
            precision highp float;
            float kernal(vec3 ver);
            uniform vec3 right, forward, up, origin;
            varying vec3 dir, localdir;
            uniform float len;
            vec3 ver;
            int sign;
            float v, v1, v2;
            float r1, r2, r3, r4, m1, m2, m3, m4;
            vec3 n, reflect;
            const float step = 0.002;
            vec3 color;
            float kernal(vec3 ver) {
                vec3 a;
                float b, c, d, e;
                a = ver;
                for (int i = 0; i < 5; i++) {
                    b = length(a);
                    c = atan(a.y, a.x) * 8.0;
                    e = 1.0 / b;
                    d = acos(a.z / b) * 8.0;
                    b = pow(b, 8.0);
                    a = vec3(b * sin(d) * cos(c), b * sin(d) * sin(c), b * cos(d)) + ver;
                    if (b > 6.0) {
                        break;
                    }
                }
                return 4.0 - a.x * a.x - a.y * a.y - a.z * a.z;
            }
            void main() {
                color.r = 0.0;
                color.g = 0.0;
                color.b = 0.0;
                sign = 0;
                v1 = kernal(origin + dir * (step * len));
                v2 = kernal(origin);
                for (int k = 2; k < 1002; k++) {
                    ver = origin + dir * (step * len * float(k));
                    v = kernal(ver);
                    if (v > 0.0 && v1 < 0.0) {
                        r1 = step * len * float(k - 1);
                        r2 = step * len * float(k);
                        m1 = kernal(origin + dir * r1);
                        m2 = kernal(origin + dir * r2);
                        for (int l = 0; l < SOLVER; l++) {
                            r3 = r1 * 0.5 + r2 * 0.5;
                            m3 = kernal(origin + dir * r3);
                            if (m3 > 0.0) {
                                r2 = r3;
                                m2 = m3;
                            } else {
                                r1 = r3;
                                m1 = m3;
                            }
                        }
                        if (r3 < 2.0 * len) {
                            sign = 1;
                            break;
                        }
                    }
                    if (v < v1 && v1 > v2 && v1 < 0.0 && (v1 * 2.0 > v || v1 * 2.0 > v2)) {
                        r1 = step * len * float(k - 2);
                        r2 = step * len * (float(k) - 2.0 + 2.0 * M_L);
                        r3 = step * len * (float(k) - 2.0 + 2.0 * M_R);
                        r4 = step * len * float(k);
                        m2 = kernal(origin + dir * r2);
                        m3 = kernal(origin + dir * r3);
                        for (int l = 0; l < MAXR; l++) {
                            if (m2 > m3) {
                                r4 = r3;
                                r3 = r2;
                                r2 = r4 * M_L + r1 * M_R;
                                m3 = m2;
                                m2 = kernal(origin + dir * r2);
                            } else {
                                r1 = r2;
                                r2 = r3;
                                r3 = r4 * M_R + r1 * M_L;
                                m2 = m3;
                                m3 = kernal(origin + dir * r3);
                            }
                        }
                        if (m2 > 0.0) {
                            r1 = step * len * float(k - 2);
                            r2 = r2;
                            m1 = kernal(origin + dir * r1);
                            m2 = kernal(origin + dir * r2);
                            for (int l = 0; l < SOLVER; l++) {
                                r3 = r1 * 0.5 + r2 * 0.5;
                                m3 = kernal(origin + dir * r3);
                                if (m3 > 0.0) {
                                    r2 = r3;
                                    m2 = m3;
                                } else {
                                    r1 = r3;
                                    m1 = m3;
                                }
                            }
                            if (r3 < 2.0 * len && r3 > step * len) {
                                sign = 1;
                                break;
                            }
                        } else if (m3 > 0.0) {
                            r1 = step * len * float(k - 2);
                            r2 = r3;
                            m1 = kernal(origin + dir * r1);
                            m2 = kernal(origin + dir * r2);
                            for (int l = 0; l < SOLVER; l++) {
                                r3 = r1 * 0.5 + r2 * 0.5;
                                m3 = kernal(origin + dir * r3);
                                if (m3 > 0.0) {
                                    r2 = r3;
                                    m2 = m3;
                                } else {
                                    r1 = r3;
                                    m1 = m3;
                                }
                            }
                            if (r3 < 2.0 * len && r3 > step * len) {
                                sign = 1;
                                break;
                            }
                        }
                    }
                    v2 = v1;
                    v1 = v;
                }
                if (sign == 1) {
                    ver = origin + dir * r3;
                    r1 = ver.x * ver.x + ver.y * ver.y + ver.z * ver.z;
                    n.x = kernal(ver - right * (r3 * 0.00025)) - kernal(ver + right * (r3 * 0.00025));
                    n.y = kernal(ver - up * (r3 * 0.00025)) - kernal(ver + up * (r3 * 0.00025));
                    n.z = kernal(ver + forward * (r3 * 0.00025)) - kernal(ver - forward * (r3 * 0.00025));
                    r3 = n.x * n.x + n.y * n.y + n.z * n.z;
                    n = n * (1.0 / sqrt(r3));
                    ver = localdir;
                    r3 = ver.x * ver.x + ver.y * ver.y + ver.z * ver.z;
                    ver = ver * (1.0 / sqrt(r3));
                    reflect = n * (-2.0 * dot(ver, n)) + ver;
                    r3 = reflect.x * 0.276 + reflect.y * 0.920 + reflect.z * 0.276;
                    r4 = n.x * 0.276 + n.y * 0.920 + n.z * 0.276;
                    r3 = max(0.0, r3);
                    r3 = r3 * r3 * r3 * r3;
                    r3 = r3 * 0.45 + r4 * 0.25 + 0.3;
                    n.x = sin(r1 * 10.0) * 0.5 + 0.5;
                    n.y = sin(r1 * 10.0 + 2.05) * 0.5 + 0.5;
                    n.z = sin(r1 * 10.0 - 2.05) * 0.5 + 0.5;
                    color = n * r3;
                }
                gl_FragColor = vec4(color.x, color.y, color.z, 1.0);
            }
        """.trimIndent()
    }
}