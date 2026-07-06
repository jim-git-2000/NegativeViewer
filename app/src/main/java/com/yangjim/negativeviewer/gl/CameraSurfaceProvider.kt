package com.yangjim.negativeviewer.gl

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import java.util.concurrent.Executor

class CameraSurfaceProvider(
    private val executor: Executor,
) : Preview.SurfaceProvider {
    private val lock = Any()
    private var surfaceTexture: SurfaceTexture? = null
    private var surfaceRequest: SurfaceRequest? = null
    private var activeSurface: Surface? = null
    private var surfaceProvided = false

    override fun onSurfaceRequested(request: SurfaceRequest) {
        synchronized(lock) {
            if (!surfaceProvided) {
                surfaceRequest?.willNotProvideSurface()
            }
            activeSurface?.release()
            activeSurface = null
            surfaceProvided = false
            surfaceRequest = request
        }
        tryProvideSurface()
    }

    fun setSurfaceTexture(texture: SurfaceTexture?) {
        synchronized(lock) {
            surfaceTexture = texture
            if (texture == null) {
                if (!surfaceProvided) {
                    surfaceRequest?.willNotProvideSurface()
                }
                surfaceRequest = null
                activeSurface?.release()
                activeSurface = null
                surfaceProvided = false
            }
        }
        tryProvideSurface()
    }

    fun release() {
        synchronized(lock) {
            if (!surfaceProvided) {
                surfaceRequest?.willNotProvideSurface()
            }
            surfaceRequest = null
            activeSurface?.release()
            activeSurface = null
            surfaceTexture = null
            surfaceProvided = false
        }
    }

    private fun tryProvideSurface() {
        val request: SurfaceRequest
        val texture: SurfaceTexture

        synchronized(lock) {
            request = surfaceRequest ?: return
            texture = surfaceTexture ?: return
            if (surfaceProvided) {
                return
            }
            activeSurface?.release()

            val resolution = request.resolution
            texture.setDefaultBufferSize(resolution.width, resolution.height)
            activeSurface = Surface(texture)
            surfaceProvided = true
        }

        val surface = synchronized(lock) { activeSurface ?: return }
        request.provideSurface(surface, executor) { result ->
            Log.d(TAG, "CameraX preview surface result: ${result.resultCode}")
            synchronized(lock) {
                if (activeSurface == surface) {
                    activeSurface?.release()
                    activeSurface = null
                    surfaceRequest = null
                    surfaceProvided = false
                } else {
                    surface.release()
                }
            }
        }
    }

    private companion object {
        const val TAG = "NegativeViewerGL"
    }
}
