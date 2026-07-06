package com.yangjim.negativeviewer.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.core.content.ContextCompat

class CameraGlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {
    private val cameraSurfaceProvider = CameraSurfaceProvider(ContextCompat.getMainExecutor(context))
    private val cameraRenderer = CameraRenderer(
        cameraSurfaceProvider = cameraSurfaceProvider,
        requestRender = ::requestRender,
    )

    init {
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
}
