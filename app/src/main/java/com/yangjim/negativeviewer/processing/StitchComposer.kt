package com.yangjim.negativeviewer.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import java.io.File

class StitchComposer(
    private val context: Context,
) {
    fun createStitchedJpeg(
        originalFile: File,
        processedFile: File,
    ): File {
        val decodedOriginal = BitmapFactory.decodeFile(originalFile.absolutePath)
            ?: error("Failed to decode original JPEG.")
        val originalBitmap = ExifOrientationUtils.applyOrientation(originalFile, decodedOriginal)
        val processedBitmap = BitmapFactory.decodeFile(processedFile.absolutePath)
            ?: error("Failed to decode processed JPEG.")

        val stitchedBitmap = try {
            if (originalBitmap.height >= originalBitmap.width) {
                stitchPortrait(originalBitmap, processedBitmap)
            } else {
                stitchLandscape(originalBitmap, processedBitmap)
            }
        } finally {
            if (!originalBitmap.isRecycled) {
                originalBitmap.recycle()
            }
            if (!processedBitmap.isRecycled) {
                processedBitmap.recycle()
            }
        }

        val outputFile = File(context.cacheDir, "captures/STITCH_${processedFile.name}")
        outputFile.parentFile?.mkdirs()
        try {
            outputFile.outputStream().use { outputStream ->
                if (!stitchedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)) {
                    error("Failed to encode stitched JPEG.")
                }
            }
            return outputFile
        } finally {
            if (!stitchedBitmap.isRecycled) {
                stitchedBitmap.recycle()
            }
        }
    }

    private fun stitchPortrait(original: Bitmap, processed: Bitmap): Bitmap {
        val targetHeight = maxOf(original.height, processed.height)
        val originalWidth = scaledWidth(original, targetHeight)
        val processedWidth = scaledWidth(processed, targetHeight)
        val output = Bitmap.createBitmap(originalWidth + processedWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(original, null, Rect(0, 0, originalWidth, targetHeight), null)
        canvas.drawBitmap(processed, null, Rect(originalWidth, 0, originalWidth + processedWidth, targetHeight), null)
        return output
    }

    private fun stitchLandscape(original: Bitmap, processed: Bitmap): Bitmap {
        val targetWidth = maxOf(original.width, processed.width)
        val originalHeight = scaledHeight(original, targetWidth)
        val processedHeight = scaledHeight(processed, targetWidth)
        val output = Bitmap.createBitmap(targetWidth, originalHeight + processedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(original, null, Rect(0, 0, targetWidth, originalHeight), null)
        canvas.drawBitmap(processed, null, Rect(0, originalHeight, targetWidth, originalHeight + processedHeight), null)
        return output
    }

    private fun scaledWidth(bitmap: Bitmap, targetHeight: Int): Int {
        return (bitmap.width * (targetHeight.toFloat() / bitmap.height)).toInt().coerceAtLeast(1)
    }

    private fun scaledHeight(bitmap: Bitmap, targetWidth: Int): Int {
        return (bitmap.height * (targetWidth.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
    }

    private companion object {
        const val JPEG_QUALITY = 95
    }
}
