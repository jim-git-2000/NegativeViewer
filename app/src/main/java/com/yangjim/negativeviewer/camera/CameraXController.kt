package com.yangjim.negativeviewer.camera

import android.content.Context
import android.util.Log
import android.view.Display
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class CameraXController {
    private var appContext: Context? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var bindRequestId = 0
    private var focusRequestId = 0

    fun bindCameraToSurfaceProvider(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        onError: (Throwable) -> Unit,
    ) {
        val appContext = context.applicationContext
        this.appContext = appContext
        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        val requestId = ++bindRequestId

        cameraProviderFuture.addListener(
            {
                try {
                    if (requestId == bindRequestId) {
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .build()
                            .also {
                                it.setSurfaceProvider(surfaceProvider)
                            }
                        val capture = ImageCapture.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()

                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture,
                        )

                        cameraProvider = provider
                        imageCapture = capture
                        Log.d(TAG, "CameraX preview bound to OpenGL surface")
                    }
                } catch (throwable: Throwable) {
                    Log.e(TAG, "Failed to bind CameraX OpenGL preview", throwable)
                    onError(throwable)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun getImageCapture(): ImageCapture? = imageCapture

    fun focusAt(
        normalizedX: Float,
        normalizedY: Float,
        previewWidth: Int,
        previewHeight: Int,
        display: Display,
        lock: Boolean,
        onError: (Throwable) -> Unit,
    ): Boolean {
        val activeCamera = camera ?: return false
        if (previewWidth <= 0 || previewHeight <= 0) return false
        val executorContext = appContext ?: return false
        val requestId = ++focusRequestId

        val meteringFactory = DisplayOrientedMeteringPointFactory(
            display,
            activeCamera.cameraInfo,
            previewWidth.toFloat(),
            previewHeight.toFloat(),
        )
        val meteringPoint = meteringFactory.createPoint(
            normalizedX.coerceIn(0f, 1f) * previewWidth,
            normalizedY.coerceIn(0f, 1f) * previewHeight,
            0.18f,
        )
        val actionBuilder = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or
                FocusMeteringAction.FLAG_AE or
                FocusMeteringAction.FLAG_AWB,
        )
        if (lock) {
            actionBuilder.disableAutoCancel()
        } else {
            actionBuilder.setAutoCancelDuration(3, TimeUnit.SECONDS)
        }

        val action = actionBuilder.build()
        if (!activeCamera.cameraInfo.isFocusMeteringSupported(action)) {
            Log.d(TAG, "Focus metering is not supported by this camera")
            return false
        }

        val future = activeCamera.cameraControl.startFocusAndMetering(action)
        future.addListener(
            {
                if (requestId == focusRequestId) {
                    try {
                        future.get()
                    } catch (throwable: Throwable) {
                        val focusThrowable = throwable.unwrapExecutionCause()
                        if (focusThrowable.isCanceledFocusOperation()) {
                            Log.d(TAG, "Focus metering was canceled by a newer request")
                        } else {
                            Log.w(TAG, "Focus metering failed", focusThrowable)
                            onError(focusThrowable)
                        }
                    }
                }
            },
            ContextCompat.getMainExecutor(executorContext),
        )
        return true
    }

    fun unbind() {
        bindRequestId++
        try {
            cameraProvider?.unbindAll()
            Log.d(TAG, "CameraX camera use cases unbound")
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to unbind CameraX camera use cases", throwable)
        } finally {
            cameraProvider = null
            camera = null
            imageCapture = null
            appContext = null
            focusRequestId++
        }
    }

    private fun Throwable.unwrapExecutionCause(): Throwable =
        if (this is ExecutionException && cause != null) {
            cause ?: this
        } else {
            this
        }

    private fun Throwable.isCanceledFocusOperation(): Boolean =
        this is androidx.camera.core.CameraControl.OperationCanceledException ||
            message?.contains("Cancelled by another startFocusAndMetering", ignoreCase = true) == true

    private companion object {
        const val TAG = "NegativeViewerCamera"
    }
}
