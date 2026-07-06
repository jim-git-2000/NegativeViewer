package com.yangjim.negativeviewer.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraXController {
    private var cameraProvider: ProcessCameraProvider? = null
    private var bindRequestId = 0

    fun bindPreview(
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

                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                        )

                        cameraProvider = provider
                        Log.d(TAG, "CameraX preview bound")
                    }
                } catch (throwable: Throwable) {
                    Log.e(TAG, "Failed to bind CameraX preview", throwable)
                    onError(throwable)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun unbind() {
        bindRequestId++
        try {
            cameraProvider?.unbindAll()
            Log.d(TAG, "CameraX preview unbound")
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to unbind CameraX preview", throwable)
        } finally {
            cameraProvider = null
        }
    }

    private companion object {
        const val TAG = "NegativeViewerCamera"
    }
}
