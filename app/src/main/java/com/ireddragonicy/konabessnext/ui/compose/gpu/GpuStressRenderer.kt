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
 */
class GpuStressSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    private val rendererImpl = GpuStressRenderer()

    var onError: ((Int) -> Unit)? = null

    // Track the last fixed size per instance so new view instances always apply setFixedSize.
    private var lastFixedW: Int = 0
    private var lastFixedH: Int = 0

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
     * platform whenever this View's laid-out size changes.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val scaledW = (w * RENDER_SCALE).toInt().coerceAtLeast(1)
        val scaledH = (h * RENDER_SCALE).toInt().coerceAtLeast(1)
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
         * the platform stretches it back to fill the View.
         */
        private const val RENDER_SCALE: Float = 0.5f
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

    // Two triangles covering NDC [-1,1]^2. Fullscreen quad.
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
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
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

        // Monotonic seconds since the GL thread started — smooth continuous orbit.
        val t = (System.nanoTime() - startTimeNanos) / 1_000_000_000f
        val ang1 = 2.8f + 0.4f * t
        val ang2 = 0.4f + 0.1f * kotlin.math.sin(t * 0.7f)
        val len = 1.6f
        val cenx = 0f
        val ceny = 0f
        val cenz = 0f

        val cosAng1 = kotlin.math.cos(ang1)
        val sinAng1 = kotlin.math.sin(ang1)
        val cosAng2 = kotlin.math.cos(ang2)
        val sinAng2 = kotlin.math.sin(ang2)

        val xScale = if (widthPx < heightPx) 1f else heightPx.toFloat() / widthPx.toFloat()
        val yScale = if (heightPx < widthPx) 1f else widthPx.toFloat() / heightPx.toFloat()

        GLES20.glUniform1f(uXHandle, xScale)
        GLES20.glUniform1f(uYHandle, yScale)
        GLES20.glUniform1f(uLenHandle, len)
        GLES20.glUniform3f(
            uOriginHandle,
            len * cosAng1 * cosAng2 + cenx,
            len * sinAng2 + ceny,
            len * sinAng1 * cosAng2 + cenz,
        )
        GLES20.glUniform3f(
            uRightHandle,
            sinAng1, 0f, -cosAng1
        )
        GLES20.glUniform3f(
            uUpHandle,
            -sinAng2 * cosAng1,
            cosAng2,
            -sinAng2 * sinAng1,
        )
        GLES20.glUniform3f(
            uForwardHandle,
            -cosAng1 * cosAng2,
            -sinAng2,
            -sinAng1 * cosAng2,
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glFlush()

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
        if (prog == 0) {
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return 0
        }
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "link failed: ${GLES20.glGetProgramInfoLog(prog)}")
            GLES20.glDeleteProgram(prog)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return 0
        }
        GLES20.glDetachShader(prog, vs)
        GLES20.glDetachShader(prog, fs)
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
        private val VERTEX_DATA = floatArrayOf(
            -1f, -1f, 0f,
            1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, 1f, 0f,
        )

        /**
         * Vertex shader — verbatim port from cznull/vsbm.
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
         * Fragment shader — port from cznull/vsbm.
         * The `kernal` function is the spherical-fold Mandelbox distance estimator.
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
            int hitSign;
            float v, v1, v2;
            float r1, r2, r3, r4, m1, m2, m3, m4;
            vec3 n, refl;
            const float step = 0.002;
            vec3 color;
            float kernal(vec3 ver) {
                vec3 a = ver;
                for (int i = 0; i < 5; i++) {
                    float b = length(a);
                    float c = atan(a.y, a.x) * 8.0;
                    float d = acos(clamp(a.z / b, -1.0, 1.0)) * 8.0;
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
                hitSign = 0;
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
                            hitSign = 1;
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
                                hitSign = 1;
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
                                hitSign = 1;
                                break;
                            }
                        }
                    }
                    v2 = v1;
                    v1 = v;
                }
                if (hitSign == 1) {
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
                    refl = n * (-2.0 * dot(ver, n)) + ver;
                    r3 = refl.x * 0.276 + refl.y * 0.920 + refl.z * 0.276;
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