package com.yangjim.negativeviewer.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.yangjim.negativeviewer.state.PreviewMode
import java.io.File

class NegativeBitmapProcessor(
    private val context: Context,
) {
    fun createProcessedJpeg(
        sourceFile: File,
        previewMode: PreviewMode,
    ): File {
        val decodedBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            ?: error("Failed to decode captured JPEG.")

        val orientedBitmap = try {
            ExifOrientationUtils.applyOrientation(sourceFile, decodedBitmap)
        } catch (throwable: Throwable) {
            if (!decodedBitmap.isRecycled) {
                decodedBitmap.recycle()
            }
            throw throwable
        }

        val outputBitmap = try {
            when (previewMode) {
                PreviewMode.NORMAL -> orientedBitmap
                PreviewMode.INVERT -> invertBitmap(orientedBitmap)
            }
        } finally {
            if (previewMode == PreviewMode.INVERT && !orientedBitmap.isRecycled) {
                orientedBitmap.recycle()
            }
        }

        val outputFile = File(context.cacheDir, "captures/${previewMode.name}_${sourceFile.name}")
        outputFile.parentFile?.mkdirs()

        try {
            outputFile.outputStream().use { outputStream ->
                if (!outputBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)) {
                    error("Failed to encode processed JPEG.")
                }
            }
            Log.d(TAG, "Created ${previewMode.name} JPEG at ${outputFile.absolutePath}")
            return outputFile
        } finally {
            if (!outputBitmap.isRecycled) {
                outputBitmap.recycle()
            }
        }
    }

    private fun invertBitmap(src: Bitmap): Bitmap {
        val bitmap = src.copy(Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val alpha = color ushr 24 and 0xff
            val red = color ushr 16 and 0xff
            val green = color ushr 8 and 0xff
            val blue = color and 0xff

            pixels[i] =
                (alpha shl 24) or
                    ((255 - red) shl 16) or
                    ((255 - green) shl 8) or
                    (255 - blue)
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private companion object {
        const val TAG = "NegativeViewerProcess"
        const val JPEG_QUALITY = 95
    }
}
