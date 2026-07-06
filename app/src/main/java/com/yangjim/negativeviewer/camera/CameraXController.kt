package com.yangjim.negativeviewer.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraXController {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var bindRequestId = 0

    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onError: (Throwable) -> Unit,
    ) {
        val appContext = context.applicationContext
        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        val requestId = ++bindRequestId

        cameraProviderFuture.addListener(
            {
                try {
                    if (requestId == bindRequestId) {
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()

                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture,
                        )

                        cameraProvider = provider
                        imageCapture = capture
                        Log.d(TAG, "CameraX preview and ImageCapture bound")
                    }
                } catch (throwable: Throwable) {
                    Log.e(TAG, "Failed to bind CameraX camera use cases", throwable)
                    onError(throwable)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun getImageCapture(): ImageCapture? = imageCapture

    fun unbind() {
        bindRequestId++
        try {
            cameraProvider?.unbindAll()
            Log.d(TAG, "CameraX camera use cases unbound")
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to unbind CameraX camera use cases", throwable)
        } finally {
            cameraProvider = null
            imageCapture = null
        }
    }

    private companion object {
        const val TAG = "NegativeViewerCamera"
    }
}
