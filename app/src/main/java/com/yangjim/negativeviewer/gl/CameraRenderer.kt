package com.yangjim.negativeviewer.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.yangjim.negativeviewer.state.PreviewMode
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

class CameraRenderer(
    private val requestRender: () -> Unit,
) : GLSurfaceView.Renderer {
    private val textureMatrix = FloatArray(16)
    private var cameraSurfaceProvider: CameraSurfaceProvider? = null
    private var shaderProgram: ShaderProgram? = null
    private var fullscreenQuad: FullscreenQuad? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var oesTextureId = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var bufferWidth = 0
    private var bufferHeight = 0
    private var invertEnabled = true

    fun setCameraSurfaceProvider(provider: CameraSurfaceProvider) {
        cameraSurfaceProvider = provider
    }

    fun setCameraBufferSize(width: Int, height: Int) {
        bufferWidth = width
        bufferHeight = height
        updateQuadScale()
        Log.d(TAG, "Camera buffer size changed: ${width}x$height")
    }

    fun setPreviewMode(previewMode: PreviewMode) {
        invertEnabled = previewMode == PreviewMode.INVERT
        requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.02f, 0.02f, 0.02f, 1f)
        oesTextureId = createExternalTexture()
        surfaceTexture = SurfaceTexture(oesTextureId).apply {
            setOnFrameAvailableListener {
                requestRender()
            }
            getTransformMatrix(textureMatrix)
        }
        shaderProgram = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        fullscreenQuad = FullscreenQuad()
        updateQuadScale()
        cameraSurfaceProvider?.setSurfaceTexture(surfaceTexture)
        Log.d(TAG, "OpenGL camera surface created")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewWidth = width
        viewHeight = height
        updateQuadScale()
        Log.d(TAG, "OpenGL viewport changed: ${width}x$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        surfaceTexture?.let { texture ->
            try {
                texture.updateTexImage()
                texture.getTransformMatrix(textureMatrix)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Failed to update camera texture", throwable)
            }
        }

        shaderProgram?.use {
            setInt("uCameraTexture", 0)
            setInt("uInvertEnabled", if (invertEnabled) 1 else 0)
            setMat4("uTexMatrix", textureMatrix)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            fullscreenQuad?.draw(this)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        }
    }

    fun release() {
        cameraSurfaceProvider?.setSurfaceTexture(null)
        surfaceTexture?.release()
        surfaceTexture = null
        if (oesTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
            oesTextureId = 0
        }
        Log.d(TAG, "OpenGL camera surface released")
    }

    private fun updateQuadScale() {
        val quad = fullscreenQuad ?: return
        if (viewWidth <= 0 || viewHeight <= 0 || bufferWidth <= 0 || bufferHeight <= 0) {
            quad.setScale(1f, 1f)
            return
        }

        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val rawBufferAspect = bufferWidth.toFloat() / bufferHeight.toFloat()
        val rotatedBufferAspect = 1f / rawBufferAspect
        val bufferAspect = if (
            abs(rawBufferAspect - viewAspect) <= abs(rotatedBufferAspect - viewAspect)
        ) {
            rawBufferAspect
        } else {
            rotatedBufferAspect
        }

        if (bufferAspect > viewAspect) {
            quad.setScale(bufferAspect / viewAspect, 1f)
        } else {
            quad.setScale(1f, viewAspect / bufferAspect)
        }
    }

    private fun createExternalTexture(): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return textureId
    }

    private companion object {
        const val TAG = "NegativeViewerGL"

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;

            uniform mat4 uTexMatrix;

            varying vec2 vTexCoord;

            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * vec4(aTexCoord.xy, 0.0, 1.0)).xy;
            }
        """

        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;

            uniform samplerExternalOES uCameraTexture;
            uniform int uInvertEnabled;

            varying vec2 vTexCoord;

            void main() {
                vec4 color = texture2D(uCameraTexture, vTexCoord);
                if (uInvertEnabled == 1) {
                    gl_FragColor = vec4(1.0 - color.rgb, color.a);
                } else {
                    gl_FragColor = color;
                }
            }
        """
    }
}
