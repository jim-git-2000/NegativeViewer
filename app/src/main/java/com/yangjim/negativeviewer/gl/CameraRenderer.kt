package com.yangjim.negativeviewer.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraRenderer : GLSurfaceView.Renderer {
    private var shaderProgram: ShaderProgram? = null
    private var fullscreenQuad: FullscreenQuad? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.02f, 0.02f, 0.02f, 1f)
        shaderProgram = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        fullscreenQuad = FullscreenQuad()
        Log.d(TAG, "OpenGL surface created")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Log.d(TAG, "OpenGL viewport changed: ${width}x$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        shaderProgram?.use {
            fullscreenQuad?.draw(this)
        }
    }

    private companion object {
        const val TAG = "NegativeViewerGL"

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;

            varying vec2 vTexCoord;

            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord.xy;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;

            varying vec2 vTexCoord;

            void main() {
                vec3 topColor = vec3(0.07, 0.10, 0.12);
                vec3 bottomColor = vec3(0.52, 0.54, 0.47);
                vec3 accentColor = vec3(0.78, 0.20, 0.18);
                vec3 color = mix(bottomColor, topColor, vTexCoord.y);
                float center = 1.0 - smoothstep(0.0, 0.72, distance(vTexCoord, vec2(0.5, 0.5)));
                color = mix(color, accentColor, center * 0.24);
                gl_FragColor = vec4(color, 1.0);
            }
        """
    }
}
