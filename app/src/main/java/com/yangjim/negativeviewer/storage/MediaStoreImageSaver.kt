package com.yangjim.negativeviewer.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.yangjim.negativeviewer.state.PreviewMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStoreImageSaver(
    private val context: Context,
) {
    fun saveJpeg(
        sourceFile: File,
        previewMode: PreviewMode,
        nameSuffix: String = previewMode.name,
    ): Uri {
        var imageUri: Uri? = null
        try {
            val resolver = context.contentResolver
            val values = createContentValues(nameSuffix)
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create MediaStore image.")

            resolver.openOutputStream(imageUri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: error("Failed to open MediaStore output stream.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishedValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(imageUri, publishedValues, null, null)
            }

            sourceFile.delete()
            Log.d(TAG, "Saved ${previewMode.name} JPEG to MediaStore: $imageUri")
            return imageUri
        } catch (throwable: Throwable) {
            imageUri?.let { uri ->
                runCatching {
                    context.contentResolver.delete(uri, null, null)
                }
            }
            Log.e(TAG, "Failed to save ${previewMode.name} JPEG to MediaStore", throwable)
            throw throwable
        }
    }

    private fun createContentValues(nameSuffix: String): ContentValues {
        val timestamp = SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date())
        return ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "NEG_${timestamp}_$nameSuffix.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
    }

    private companion object {
        const val TAG = "NegativeViewerStorage"
        const val TIME_PATTERN = "yyyyMMdd_HHmmss_SSS"
        const val RELATIVE_PATH = "Pictures/NegativeViewer"
    }
}
