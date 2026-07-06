package com.yangjim.negativeviewer.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageCaptureController(
    private val context: Context,
) {
    fun captureToTempFile(
        imageCapture: ImageCapture?,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        if (imageCapture == null) {
            onError(IllegalStateException("Camera is not ready."))
            return
        }

        val outputFile = try {
            createOutputFile()
        } catch (throwable: Throwable) {
            onError(throwable)
            return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Saved raw capture to ${outputFile.absolutePath}")
                    onSuccess(outputFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    outputFile.delete()
                    Log.e(TAG, "Failed to save raw capture", exception)
                    onError(exception)
                }
            },
        )
    }

    private fun createOutputFile(): File {
        val capturesDir = File(context.cacheDir, CAPTURES_DIR).apply {
            if (!exists() && !mkdirs()) {
                error("Failed to create capture cache directory.")
            }
        }
        val timestamp = SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date())
        return File(capturesDir, "RAW_$timestamp.jpg")
    }

    private companion object {
        const val TAG = "NegativeViewerCapture"
        const val CAPTURES_DIR = "captures"
        const val TIME_PATTERN = "yyyyMMdd_HHmmss"
    }
}
