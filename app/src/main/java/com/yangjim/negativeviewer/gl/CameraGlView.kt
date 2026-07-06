package com.yangjim.negativeviewer.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.yangjim.negativeviewer.state.ProcessingParams
import com.yangjim.negativeviewer.state.PreviewMode

class CameraGlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {
    private val cameraRenderer = CameraRenderer(
        requestRender = ::requestRender,
    )
    private val cameraSurfaceProvider = CameraSurfaceProvider(
        executor = ContextCompat.getMainExecutor(context),
        onResolutionChanged = { resolution ->
            queueEvent {
                cameraRenderer.setCameraBufferSize(resolution.width, resolution.height)
            }
            requestRender()
        },
    )

    init {
        cameraRenderer.setCameraSurfaceProvider(cameraSurfaceProvider)
        setEGLContextClientVersion(2)
        setRenderer(cameraRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
        requestRender()
    }

    override fun onDetachedFromWindow() {
        queueEvent {
            cameraRenderer.release()
        }
        cameraSurfaceProvider.release()
        onPause()
        super.onDetachedFromWindow()
    }

    fun surfaceProvider(): CameraSurfaceProvider = cameraSurfaceProvider

    fun setPreviewMode(previewMode: PreviewMode) {
        queueEvent {
            cameraRenderer.setPreviewMode(previewMode)
        }
        requestRender()
    }

    fun setProcessingParams(processingParams: ProcessingParams) {
        queueEvent {
            cameraRenderer.setProcessingParams(processingParams)
        }
        requestRender()
    }
}
