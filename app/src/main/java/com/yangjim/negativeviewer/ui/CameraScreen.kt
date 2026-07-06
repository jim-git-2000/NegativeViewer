package com.yangjim.negativeviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import com.yangjim.negativeviewer.state.ProcessingParams
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
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onGammaChange: (Float) -> Unit,
    onRedGainChange: (Float) -> Unit,
    onGreenGainChange: (Float) -> Unit,
    onBlueGainChange: (Float) -> Unit,
    onResetTone: () -> Unit,
    onResetRgb: () -> Unit,
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
                    glView.setProcessingParams(uiState.processingParams)
                    cameraGlView = glView
                }
            },
            update = { glView ->
                glView.setPreviewMode(uiState.previewMode)
                glView.setProcessingParams(uiState.processingParams)
                glView.requestRender()
            },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(PREVIEW_ASPECT_RATIO)
                .background(Color.Black),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ModeToggleButton(
                previewMode = uiState.previewMode,
                onClick = onToggleMode,
            )
            if (uiState.previewMode == PreviewMode.COLOR_NEGATIVE_CORRECTED) {
                Button(onClick = { }) {
                    Text(text = "片基采样")
                }
            }
            if (uiState.previewMode != PreviewMode.NORMAL) {
                ProcessingControls(
                    previewMode = uiState.previewMode,
                    processingParams = uiState.processingParams,
                    onBrightnessChange = onBrightnessChange,
                    onContrastChange = onContrastChange,
                    onGammaChange = onGammaChange,
                    onRedGainChange = onRedGainChange,
                    onGreenGainChange = onGreenGainChange,
                    onBlueGainChange = onBlueGainChange,
                    onResetTone = onResetTone,
                    onResetRgb = onResetRgb,
                )
            }
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
                val captureParams = uiState.processingParams
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
                                        processingParams = captureParams,
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

@Composable
private fun ProcessingControls(
    previewMode: PreviewMode,
    processingParams: ProcessingParams,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onGammaChange: (Float) -> Unit,
    onRedGainChange: (Float) -> Unit,
    onGreenGainChange: (Float) -> Unit,
    onBlueGainChange: (Float) -> Unit,
    onResetTone: () -> Unit,
    onResetRgb: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(224.dp),
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.58f),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ParameterSlider(
                label = "Brightness",
                value = processingParams.brightness,
                valueRange = -0.5f..0.5f,
                onValueChange = onBrightnessChange,
            )
            ParameterSlider(
                label = "Contrast",
                value = processingParams.contrast,
                valueRange = 0.2f..3f,
                onValueChange = onContrastChange,
            )
            ParameterSlider(
                label = "Gamma",
                value = processingParams.gamma,
                valueRange = 0.1f..3f,
                onValueChange = onGammaChange,
            )
            Button(
                onClick = onResetTone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Reset")
            }

            if (previewMode != PreviewMode.BW_NEGATIVE) {
                ParameterSlider(
                    label = "Red",
                    value = processingParams.redGain,
                    valueRange = 0.2f..3f,
                    onValueChange = onRedGainChange,
                )
                ParameterSlider(
                    label = "Green",
                    value = processingParams.greenGain,
                    valueRange = 0.2f..3f,
                    onValueChange = onGreenGainChange,
                )
                ParameterSlider(
                    label = "Blue",
                    value = processingParams.blueGain,
                    valueRange = 0.2f..3f,
                    onValueChange = onBlueGainChange,
                )
                Button(
                    onClick = onResetRgb,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Reset RGB")
                }
            }
        }
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
    }
}

private const val PREVIEW_ASPECT_RATIO = 3f / 4f
