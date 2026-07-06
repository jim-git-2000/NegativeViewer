package com.yangjim.negativeviewer.gl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.yangjim.negativeviewer.state.OrangeMaskSample
import com.yangjim.negativeviewer.state.ProcessingParams
import com.yangjim.negativeviewer.state.PreviewMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.roundToInt

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
    private var previewMode = PreviewMode.COLOR_BASIC_INVERT
    private var processingParams = ProcessingParams.Default
    private var orangeMaskSample: OrangeMaskSample? = null
    private var pendingSampleRequest: PendingSampleRequest? = null

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
        this.previewMode = previewMode
        requestRender()
    }

    fun setProcessingParams(processingParams: ProcessingParams) {
        this.processingParams = processingParams
        requestRender()
    }

    fun setOrangeMaskSample(sample: OrangeMaskSample?) {
        orangeMaskSample = sample
        requestRender()
    }

    fun requestOrangeMaskSample(
        normalizedX: Float,
        normalizedY: Float,
        onResult: (Result<OrangeMaskSample>) -> Unit,
    ) {
        pendingSampleRequest = PendingSampleRequest(
            normalizedX = normalizedX.coerceIn(0f, 1f),
            normalizedY = normalizedY.coerceIn(0f, 1f),
            onResult = onResult,
        )
        requestRender()
    }

    fun captureProcessedFrame(onResult: (Result<Bitmap>) -> Unit) {
        try {
            updateCameraTexture()
            onResult(Result.success(renderProcessedFrameToBitmap()))
        } catch (throwable: Throwable) {
            onResult(Result.failure(throwable))
        } finally {
            GLES20.glViewport(0, 0, viewWidth, viewHeight)
            updateQuadScale()
            requestRender()
        }
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

        updateCameraTexture()

        val sampleRequest = pendingSampleRequest
        if (sampleRequest != null) {
            pendingSampleRequest = null
            try {
                drawCameraFrame(forcePreviewMode = PreviewMode.NORMAL)
                sampleRequest.onResult(Result.success(readOrangeMaskSample(sampleRequest)))
            } catch (throwable: Throwable) {
                sampleRequest.onResult(Result.failure(throwable))
            }
        }

        drawCameraFrame(forcePreviewMode = null)
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
            quad.setScale(1f, viewAspect / bufferAspect)
        } else {
            quad.setScale(bufferAspect / viewAspect, 1f)
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

    private fun drawCameraFrame(forcePreviewMode: PreviewMode?) {
        val mode = forcePreviewMode ?: previewMode
        val sample = orangeMaskSample

        shaderProgram?.use {
            setInt("uCameraTexture", 0)
            setInt("uPreviewMode", mode.ordinal)
            setInt("uHasOrangeMaskSample", if (sample != null) 1 else 0)
            setFloat("uBrightness", processingParams.brightness)
            setFloat("uContrast", processingParams.contrast)
            setFloat("uGamma", processingParams.gamma.coerceAtLeast(MIN_GAMMA))
            setFloat3(
                "uRgbGain",
                processingParams.redGain,
                processingParams.greenGain,
                processingParams.blueGain,
            )
            setFloat3(
                "uOrangeMaskSample",
                sample?.red ?: 1f,
                sample?.green ?: 1f,
                sample?.blue ?: 1f,
            )
            setMat4("uTexMatrix", textureMatrix)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            fullscreenQuad?.draw(this)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        }
    }

    private fun updateCameraTexture() {
        surfaceTexture?.let { texture ->
            try {
                texture.updateTexImage()
                texture.getTransformMatrix(textureMatrix)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Failed to update camera texture", throwable)
            }
        }
    }

    private fun renderProcessedFrameToBitmap(): Bitmap {
        if (shaderProgram == null || fullscreenQuad == null || oesTextureId == 0) {
            error("OpenGL preview is not ready.")
        }

        val targetSize = captureTargetSize()
        val framebufferIds = IntArray(1)
        val textureIds = IntArray(1)

        GLES20.glGenFramebuffers(1, framebufferIds, 0)
        GLES20.glGenTextures(1, textureIds, 0)

        try {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                targetSize.width,
                targetSize.height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null,
            )

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferIds[0])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                textureIds[0],
                0,
            )
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                error("OpenGL preview capture framebuffer is incomplete.")
            }

            val quad = fullscreenQuad ?: error("OpenGL preview is not ready.")
            val previousScaleX = quad.scaleX
            val previousScaleY = quad.scaleY
            GLES20.glViewport(0, 0, targetSize.width, targetSize.height)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            quad.setScale(1f, 1f)
            drawCameraFrame(forcePreviewMode = null)
            quad.setScale(previousScaleX, previousScaleY)

            val rgba = ByteBuffer
                .allocateDirect(targetSize.width * targetSize.height * BYTES_PER_PIXEL)
                .order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(
                0,
                0,
                targetSize.width,
                targetSize.height,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                rgba,
            )
            return rgbaToBitmap(rgba, targetSize.width, targetSize.height)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GLES20.glDeleteFramebuffers(1, framebufferIds, 0)
            GLES20.glDeleteTextures(1, textureIds, 0)
        }
    }

    private fun captureTargetSize(): CaptureSize {
        val sourceWidth = if (bufferWidth > 0) bufferWidth else viewWidth
        val sourceHeight = if (bufferHeight > 0) bufferHeight else viewHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            error("Preview is not ready for capture.")
        }

        val viewAspect = if (viewWidth > 0 && viewHeight > 0) {
            viewWidth.toFloat() / viewHeight.toFloat()
        } else {
            sourceWidth.toFloat() / sourceHeight.toFloat()
        }
        val rawAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        val rotatedAspect = sourceHeight.toFloat() / sourceWidth.toFloat()
        val rotated = abs(rotatedAspect - viewAspect) < abs(rawAspect - viewAspect)
        val width = if (rotated) sourceHeight else sourceWidth
        val height = if (rotated) sourceWidth else sourceHeight
        return clampToMaxTextureSize(width, height)
    }

    private fun clampToMaxTextureSize(width: Int, height: Int): CaptureSize {
        val maxTextureSize = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0)
        val maxSize = maxTextureSize[0].takeIf { it > 0 } ?: return CaptureSize(width, height)
        val scale = minOf(1f, maxSize.toFloat() / maxOf(width, height).toFloat())
        return CaptureSize(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun rgbaToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        buffer.position(0)
        for (y in 0 until height) {
            val targetY = height - 1 - y
            for (x in 0 until width) {
                val red = buffer.get().toInt() and 0xff
                val green = buffer.get().toInt() and 0xff
                val blue = buffer.get().toInt() and 0xff
                val alpha = buffer.get().toInt() and 0xff
                pixels[targetY * width + x] =
                    (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun readOrangeMaskSample(request: PendingSampleRequest): OrangeMaskSample {
        if (viewWidth <= 0 || viewHeight <= 0) {
            error("Preview is not ready for sampling.")
        }

        val sampleSize = minOf(9, viewWidth, viewHeight)
        val centerX = (request.normalizedX * (viewWidth - 1)).roundToInt()
        val centerY = ((1f - request.normalizedY) * (viewHeight - 1)).roundToInt()
        val left = (centerX - sampleSize / 2).coerceIn(0, (viewWidth - sampleSize).coerceAtLeast(0))
        val bottom = (centerY - sampleSize / 2).coerceIn(0, (viewHeight - sampleSize).coerceAtLeast(0))
        val buffer = ByteBuffer.allocateDirect(sampleSize * sampleSize * BYTES_PER_PIXEL)

        GLES20.glReadPixels(
            left,
            bottom,
            sampleSize,
            sampleSize,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer,
        )

        var red = 0
        var green = 0
        var blue = 0
        val pixelCount = sampleSize * sampleSize
        buffer.position(0)
        repeat(pixelCount) {
            red += buffer.get().toInt() and 0xff
            green += buffer.get().toInt() and 0xff
            blue += buffer.get().toInt() and 0xff
            buffer.get()
        }

        return OrangeMaskSample(
            red = red / (pixelCount * 255f),
            green = green / (pixelCount * 255f),
            blue = blue / (pixelCount * 255f),
        )
    }

    private data class PendingSampleRequest(
        val normalizedX: Float,
        val normalizedY: Float,
        val onResult: (Result<OrangeMaskSample>) -> Unit,
    )

    private data class CaptureSize(
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val TAG = "NegativeViewerGL"
        const val BYTES_PER_PIXEL = 4

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
            uniform int uPreviewMode;
            uniform int uHasOrangeMaskSample;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uGamma;
            uniform vec3 uRgbGain;
            uniform vec3 uOrangeMaskSample;

            varying vec2 vTexCoord;

            vec3 applyTone(vec3 color) {
                color = (color - vec3(0.5)) * uContrast + vec3(0.5);
                color += vec3(uBrightness);
                color = clamp(color, 0.0, 1.0);
                return pow(color, vec3(1.0 / max(uGamma, 0.1)));
            }

            vec3 applyOrangeMaskCorrection(vec3 color) {
                float epsilon = 0.001;
                float scale = max(max(uOrangeMaskSample.r, uOrangeMaskSample.g), uOrangeMaskSample.b);
                vec3 normalized = color / max(uOrangeMaskSample, vec3(epsilon)) * scale;
                return clamp(normalized, 0.0, 1.0);
            }

            void main() {
                vec4 color = texture2D(uCameraTexture, vTexCoord);
                if (uPreviewMode == 0) {
                    gl_FragColor = color;
                } else if (uPreviewMode == 2) {
                    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    vec3 processed = vec3(1.0 - gray);
                    gl_FragColor = vec4(applyTone(processed), color.a);
                } else if (uPreviewMode == 3 && uHasOrangeMaskSample == 1) {
                    vec3 normalized = applyOrangeMaskCorrection(color.rgb);
                    vec3 processed = (1.0 - normalized) * uRgbGain;
                    gl_FragColor = vec4(applyTone(processed), color.a);
                } else {
                    vec3 processed = (1.0 - color.rgb) * uRgbGain;
                    gl_FragColor = vec4(applyTone(processed), color.a);
                }
            }
        """

        const val MIN_GAMMA = 0.1f
    }
}
