package com.yangjim.negativeviewer.processing

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ExifOrientationUtils {
    fun applyOrientation(sourceFile: File, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(sourceFile.absolutePath)
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform(bitmap) { preScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_180 -> transform(bitmap) { postRotate(180f) }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform(bitmap) { preScale(1f, -1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> transform(bitmap) {
                postRotate(90f)
                preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> transform(bitmap) { postRotate(90f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> transform(bitmap) {
                postRotate(-90f)
                preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> transform(bitmap) { postRotate(270f) }
            else -> bitmap
        }
    }

    private inline fun transform(
        bitmap: Bitmap,
        matrixBlock: Matrix.() -> Unit,
    ): Bitmap {
        val matrix = Matrix().apply(matrixBlock)
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed != bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return transformed
    }
}
