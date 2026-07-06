package com.yangjim.negativeviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yangjim.negativeviewer.camera.CameraXController
import com.yangjim.negativeviewer.camera.ImageCaptureController
import com.yangjim.negativeviewer.gl.CameraGlView
import com.yangjim.negativeviewer.processing.NegativeBitmapProcessor
import com.yangjim.negativeviewer.state.CameraUiState
import com.yangjim.negativeviewer.state.PreviewMode
import com.yangjim.negativeviewer.storage.MediaStoreImageSaver
import com.yangjim.negativeviewer.ui.components.CaptureButton
import com.yangjim.negativeviewer.ui.components.ModeToggleButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraScreen(
    uiState: CameraUiState,
    onToggleMode: () -> Unit,
    onCaptureStarted: () -> Unit,
    onCaptureSucceeded: () -> Unit,
    onCaptureFailed: (String) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraXController = remember { CameraXController() }
    val imageCaptureController = remember(context) { ImageCaptureController(context) }
    val mediaStoreImageSaver = remember(context) { MediaStoreImageSaver(context) }
    val negativeBitmapProcessor = remember(context) { NegativeBitmapProcessor(context) }
    val coroutineScope = rememberCoroutineScope()
    var cameraGlView by remember { mutableStateOf<CameraGlView?>(null) }

    DisposableEffect(lifecycleOwner, cameraGlView) {
        val glView = cameraGlView
        if (glView != null) {
            cameraXController.bindCameraToSurfaceProvider(
                context = context,
                lifecycleOwner = lifecycleOwner,
                surfaceProvider = glView.surfaceProvider(),
                onError = { throwable ->
                    onCameraError(throwable.message ?: "Camera preview failed.")
                },
            )
        }

        onDispose {
            cameraXController.unbind()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { viewContext ->
                CameraGlView(viewContext).also { glView ->
                    glView.setPreviewMode(uiState.previewMode)
                    cameraGlView = glView
                }
            },
            update = { glView ->
                glView.setPreviewMode(uiState.previewMode)
                glView.requestRender()
            },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(PREVIEW_ASPECT_RATIO)
                .background(Color.Black),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
        ) {
            ModeToggleButton(
                previewMode = uiState.previewMode,
                onClick = onToggleMode,
            )
        }

        uiState.lastError?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 96.dp, start = 24.dp, end = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }

        CaptureButton(
            enabled = !uiState.isCapturing,
            onClick = {
                val captureMode = uiState.previewMode
                onCaptureStarted()
                imageCaptureController.captureToTempFile(
                    imageCapture = cameraXController.getImageCapture(),
                    onSuccess = { rawFile ->
                        coroutineScope.launch {
                            var fileToSave: java.io.File? = null
                            try {
                                fileToSave = withContext(Dispatchers.Default) {
                                    negativeBitmapProcessor.createProcessedJpeg(
                                        sourceFile = rawFile,
                                        previewMode = captureMode,
                                    )
                                }
                                val outputFile = fileToSave ?: error("No JPEG output file was created.")
                                withContext(Dispatchers.IO) {
                                    mediaStoreImageSaver.saveJpeg(
                                        sourceFile = outputFile,
                                        previewMode = captureMode,
                                    )
                                }
                                rawFile.delete()
                                onCaptureSucceeded()
                            } catch (oom: OutOfMemoryError) {
                                rawFile.delete()
                                fileToSave?.let { processedFile ->
                                    if (processedFile != rawFile) {
                                        processedFile.delete()
                                    }
                                }
                                onCaptureFailed("Not enough memory to process image.")
                            } catch (throwable: Throwable) {
                                rawFile.delete()
                                fileToSave?.let { processedFile ->
                                    if (processedFile != rawFile) {
                                        processedFile.delete()
                                    }
                                }
                                onCaptureFailed(throwable.message ?: "Image processing failed.")
                            }
                        }
                    },
                    onError = { throwable ->
                        onCaptureFailed(throwable.message ?: "Capture failed.")
                    },
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        )
    }
}

private const val PREVIEW_ASPECT_RATIO = 4f / 3f
