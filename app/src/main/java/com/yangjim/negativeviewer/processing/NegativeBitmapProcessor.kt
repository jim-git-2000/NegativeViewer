package com.yangjim.negativeviewer.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.yangjim.negativeviewer.state.ProcessingParams
import com.yangjim.negativeviewer.state.PreviewMode
import java.io.File
import kotlin.math.pow

class NegativeBitmapProcessor(
    private val context: Context,
) {
    fun createProcessedJpeg(
        sourceFile: File,
        previewMode: PreviewMode,
        processingParams: ProcessingParams,
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
                PreviewMode.COLOR_BASIC_INVERT,
                PreviewMode.COLOR_NEGATIVE_CORRECTED -> processColorNegativeBitmap(orientedBitmap, processingParams)
                PreviewMode.BW_NEGATIVE -> processBwNegativeBitmap(orientedBitmap, processingParams)
            }
        } finally {
            if (previewMode != PreviewMode.NORMAL && !orientedBitmap.isRecycled) {
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

    private fun processColorNegativeBitmap(src: Bitmap, processingParams: ProcessingParams): Bitmap {
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

            val processed = applyRgbGainAndTone(
                red = 255 - red,
                green = 255 - green,
                blue = 255 - blue,
                processingParams = processingParams,
            )

            pixels[i] =
                (alpha shl 24) or
                    (processed.red shl 16) or
                    (processed.green shl 8) or
                    processed.blue
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun processBwNegativeBitmap(src: Bitmap, processingParams: ProcessingParams): Bitmap {
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
            val gray = (0.299f * red + 0.587f * green + 0.114f * blue).toInt()
            val invertedGray = 255 - gray

            val processedGray = applyTone(invertedGray / 255f, processingParams)

            pixels[i] =
                (alpha shl 24) or
                    (processedGray shl 16) or
                    (processedGray shl 8) or
                    processedGray
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun applyRgbGainAndTone(
        red: Int,
        green: Int,
        blue: Int,
        processingParams: ProcessingParams,
    ): Rgb {
        return Rgb(
            red = applyTone(red / 255f * processingParams.redGain, processingParams),
            green = applyTone(green / 255f * processingParams.greenGain, processingParams),
            blue = applyTone(blue / 255f * processingParams.blueGain, processingParams),
        )
    }

    private fun applyTone(value: Float, processingParams: ProcessingParams): Int {
        val contrasted = (value - 0.5f) * processingParams.contrast + 0.5f
        val brightened = contrasted + processingParams.brightness
        val clamped = brightened.coerceIn(0f, 1f)
        val gamma = processingParams.gamma.coerceAtLeast(MIN_GAMMA)
        return (clamped.pow(1f / gamma) * 255f).toInt().coerceIn(0, 255)
    }

    private data class Rgb(
        val red: Int,
        val green: Int,
        val blue: Int,
    )

    private companion object {
        const val TAG = "NegativeViewerProcess"
        const val JPEG_QUALITY = 95
        const val MIN_GAMMA = 0.1f
    }
}
