package com.yangjim.negativeviewer.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class CameraGlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {
    private val cameraRenderer = CameraRenderer()

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
        onPause()
        super.onDetachedFromWindow()
    }
}
