package com.yangjim.negativeviewer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yangjim.negativeviewer.camera.CameraXController
import com.yangjim.negativeviewer.gl.CameraGlView
import com.yangjim.negativeviewer.processing.StitchComposer
import com.yangjim.negativeviewer.state.CameraUiState
import com.yangjim.negativeviewer.state.OrangeMaskSample
import com.yangjim.negativeviewer.state.OrangeMaskSamplingState
import com.yangjim.negativeviewer.state.ProcessingParams
import com.yangjim.negativeviewer.state.PreviewMode
import com.yangjim.negativeviewer.state.SaveOutputMode
import com.yangjim.negativeviewer.storage.MediaStoreImageSaver
import com.yangjim.negativeviewer.ui.components.CaptureButton
import com.yangjim.negativeviewer.ui.components.ModeToggleButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LeftControlColumnWidth = 118.dp
private val LeftControlHorizontalPadding = 16.dp

private data class FocusIndicator(
    val normalizedX: Float,
    val normalizedY: Float,
    val locked: Boolean,
)

private data class ExposureControlGeometry(
    val trackX: Float,
    val trackTop: Float,
    val trackBottom: Float,
)

private data class FocusPoint(
    val indicatorX: Float,
    val indicatorY: Float,
    val cameraX: Float,
    val cameraY: Float,
)

@Composable
fun CameraScreen(
    uiState: CameraUiState,
    onToggleMode: () -> Unit,
    onExposureChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onGammaChange: (Float) -> Unit,
    onRedGainChange: (Float) -> Unit,
    onGreenGainChange: (Float) -> Unit,
    onBlueGainChange: (Float) -> Unit,
    onResetTone: () -> Unit,
    onResetRgb: () -> Unit,
    onStartOrangeMaskSampling: () -> Unit,
    onOrangeMaskSampled: (OrangeMaskSample) -> Unit,
    onResetOrangeMaskSample: () -> Unit,
    onToggleSaveOutputMode: () -> Unit,
    onCaptureStarted: () -> Unit,
    onCaptureSucceeded: () -> Unit,
    onCaptureFailed: (String) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraXController = remember { CameraXController() }
    val mediaStoreImageSaver = remember(context) { MediaStoreImageSaver(context) }
    val stitchComposer = remember(context) { StitchComposer(context) }
    val coroutineScope = rememberCoroutineScope()
    var cameraGlView by remember { mutableStateOf<CameraGlView?>(null) }
    var markerX by remember { mutableStateOf(0.5f) }
    var markerY by remember { mutableStateOf(0.5f) }
    var showToneControls by remember { mutableStateOf(false) }
    var showRgbControls by remember { mutableStateOf(false) }
    var focusIndicator by remember { mutableStateOf<FocusIndicator?>(null) }
    var exposureDragActive by remember { mutableStateOf(false) }
    val allModesPreview = uiState.previewMode == PreviewMode.ALL_MODES
    val colorPlusControlsVisible =
        uiState.previewMode == PreviewMode.COLOR_NEGATIVE_CORRECTED || allModesPreview

    LaunchedEffect(focusIndicator, exposureDragActive) {
        val indicator = focusIndicator
        if (indicator != null && !indicator.locked && !exposureDragActive) {
            delay(3200)
            if (focusIndicator == indicator) {
                focusIndicator = null
            }
        }
    }

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
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(PREVIEW_ASPECT_RATIO)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { viewContext ->
                    CameraGlView(viewContext).also { glView ->
                        glView.setPreviewMode(uiState.previewMode)
                        glView.setProcessingParams(uiState.processingParams)
                        glView.setOrangeMaskSample(uiState.orangeMaskSample)
                        cameraGlView = glView
                    }
                },
                update = { glView ->
                    glView.setPreviewMode(uiState.previewMode)
                    glView.setProcessingParams(uiState.processingParams)
                    glView.setOrangeMaskSample(uiState.orangeMaskSample)
                    glView.requestRender()
                },
                modifier = Modifier.fillMaxSize(),
            )

            val orangeMaskMarkerActive =
                colorPlusControlsVisible &&
                    uiState.orangeMaskSamplingState == OrangeMaskSamplingState.ARMING

            FocusTouchOverlay(
                enabled = !orangeMaskMarkerActive,
                indicator = focusIndicator,
                allModesPreview = allModesPreview,
                exposure = uiState.processingParams.exposure,
                onFocus = { focusPoint, previewSize, lock ->
                    val focusStarted = cameraXController.focusAt(
                        normalizedX = focusPoint.cameraX,
                        normalizedY = focusPoint.cameraY,
                        previewWidth = previewSize.width,
                        previewHeight = previewSize.height,
                        display = localView.display,
                        lock = lock,
                        onError = { throwable ->
                            onCaptureFailed(throwable.message ?: "Focus failed.")
                        },
                    )
                    if (focusStarted) {
                        focusIndicator = FocusIndicator(
                            normalizedX = focusPoint.indicatorX,
                            normalizedY = focusPoint.indicatorY,
                            locked = lock,
                        )
                    }
                },
                onZoom = { scaleFactor ->
                    cameraXController.zoomBy(scaleFactor)
                },
                onExposureChange = onExposureChange,
                onExposureDragStarted = {
                    exposureDragActive = true
                },
                onExposureDragFinished = {
                    exposureDragActive = false
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (
                colorPlusControlsVisible &&
                uiState.orangeMaskSamplingState == OrangeMaskSamplingState.ARMING
            ) {
                OrangeMaskMarkerOverlay(
                    markerX = markerX,
                    markerY = markerY,
                    onMarkerChange = { x, y ->
                        markerX = x
                        markerY = y
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

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
            if (uiState.previewMode != PreviewMode.NORMAL && !allModesPreview) {
                Button(onClick = onToggleSaveOutputMode) {
                    Text(
                        text = when (uiState.saveOutputMode) {
                            SaveOutputMode.PROCESSED_ONLY -> "单图"
                            SaveOutputMode.ORIGINAL_AND_PROCESSED_STITCH -> "拼接"
                        },
                    )
                }
            }
        }

        if (colorPlusControlsVisible) {
            OrangeMaskControls(
                samplingState = uiState.orangeMaskSamplingState,
                sample = uiState.orangeMaskSample,
                onStartSampling = onStartOrangeMaskSampling,
                onConfirmSample = {
                    val samplePoint = mapToCameraPoint(
                        normalizedX = markerX,
                        normalizedY = markerY,
                        allModesPreview = allModesPreview,
                    )
                    cameraGlView?.sampleOrangeMask(
                        normalizedX = samplePoint.cameraX,
                        normalizedY = samplePoint.cameraY,
                    ) { result ->
                        result
                            .onSuccess(onOrangeMaskSampled)
                            .onFailure { throwable ->
                                onCaptureFailed(throwable.message ?: "片基采样失败。")
                            }
                    } ?: onCaptureFailed("Preview is not ready for sampling.")
                },
                onResetSample = onResetOrangeMaskSample,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = LeftControlHorizontalPadding, top = 16.dp)
                    .width(LeftControlColumnWidth),
            )
        }

        if (
            colorPlusControlsVisible &&
            uiState.orangeMaskSample != null
        ) {
            OrangeMaskSampleInfo(
                sample = uiState.orangeMaskSample,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 16.dp, start = 132.dp, end = 132.dp),
            )
        }

        val showActiveRgbControls = showRgbControls && uiState.previewMode != PreviewMode.BW_NEGATIVE
        if (uiState.previewMode != PreviewMode.NORMAL && (showToneControls || showActiveRgbControls)) {
            ProcessingControls(
                previewMode = uiState.previewMode,
                processingParams = uiState.processingParams,
                showTone = showToneControls,
                showRgb = showActiveRgbControls,
                onExposureChange = onExposureChange,
                onBrightnessChange = onBrightnessChange,
                onContrastChange = onContrastChange,
                onGammaChange = onGammaChange,
                onRedGainChange = onRedGainChange,
                onGreenGainChange = onGreenGainChange,
                onBlueGainChange = onBlueGainChange,
                onResetTone = onResetTone,
                onResetRgb = onResetRgb,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .offset(y = (-178).dp),
            )
        }

        if (uiState.previewMode != PreviewMode.NORMAL) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = LeftControlHorizontalPadding,
                        bottom = 24.dp,
                    )
                    .width(LeftControlColumnWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val nextVisible = !showToneControls
                        showToneControls = nextVisible
                        if (nextVisible) {
                            showRgbControls = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Tone")
                }
                if (uiState.previewMode != PreviewMode.BW_NEGATIVE) {
                    Button(
                        onClick = {
                            val nextVisible = !showRgbControls
                            showRgbControls = nextVisible
                            if (nextVisible) {
                                showToneControls = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "RGB")
                    }
                }
            }
        }

        CaptureButton(
            enabled = !uiState.isCapturing,
            isProcessing = uiState.isCapturing,
            onClick = captureClick@{
                val captureMode = uiState.previewMode
                val captureSaveOutputMode = if (
                    captureMode == PreviewMode.NORMAL ||
                    captureMode == PreviewMode.ALL_MODES
                ) {
                    SaveOutputMode.PROCESSED_ONLY
                } else {
                    uiState.saveOutputMode
                }
                onCaptureStarted()
                try {
                    val glView = cameraGlView
                    if (glView == null) {
                        onCaptureFailed("Preview is not ready for capture.")
                        return@captureClick
                    }
                    if (captureSaveOutputMode == SaveOutputMode.ORIGINAL_AND_PROCESSED_STITCH) {
                        glView.captureProcessedAndOriginalFrames { result ->
                            result
                                .onSuccess { frames ->
                                    coroutineScope.launch {
                                        var originalFile: File? = null
                                        var previewFile: File? = null
                                        var stitchedFile: File? = null
                                        try {
                                            originalFile = createPreviewCaptureJpeg(
                                                context = context,
                                                bitmap = frames.original,
                                                previewMode = PreviewMode.NORMAL,
                                            )
                                            previewFile = createPreviewCaptureJpeg(
                                                context = context,
                                                bitmap = frames.processed,
                                                previewMode = captureMode,
                                            )
                                            stitchedFile = withContext(Dispatchers.Default) {
                                                stitchComposer.createStitchedJpeg(
                                                    originalFile = originalFile ?: error("Original preview JPEG missing."),
                                                    processedFile = previewFile ?: error("Preview JPEG missing."),
                                                )
                                            }
                                            withContext(Dispatchers.IO) {
                                                mediaStoreImageSaver.saveJpeg(
                                                    sourceFile = stitchedFile ?: error("Stitched JPEG missing."),
                                                    previewMode = captureMode,
                                                    nameSuffix = "STITCH_${captureMode.name}",
                                                )
                                            }
                                            originalFile?.delete()
                                            previewFile?.delete()
                                            onCaptureSucceeded()
                                        } catch (oom: OutOfMemoryError) {
                                            if (!frames.original.isRecycled) {
                                                frames.original.recycle()
                                            }
                                            if (!frames.processed.isRecycled) {
                                                frames.processed.recycle()
                                            }
                                            originalFile?.delete()
                                            previewFile?.delete()
                                            stitchedFile?.delete()
                                            onCaptureFailed("Not enough memory to process image.")
                                        } catch (throwable: Throwable) {
                                            if (!frames.original.isRecycled) {
                                                frames.original.recycle()
                                            }
                                            if (!frames.processed.isRecycled) {
                                                frames.processed.recycle()
                                            }
                                            originalFile?.delete()
                                            previewFile?.delete()
                                            stitchedFile?.delete()
                                            onCaptureFailed(throwable.message ?: "Image processing failed.")
                                        }
                                    }
                                }
                                .onFailure { throwable ->
                                    onCaptureFailed(throwable.message ?: "Preview capture failed.")
                                }
                        }
                    } else {
                        glView.captureProcessedFrame { result ->
                            result
                                .onSuccess { bitmap ->
                                    coroutineScope.launch {
                                        var previewFile: File? = null
                                        try {
                                            previewFile = createPreviewCaptureJpeg(
                                                context = context,
                                                bitmap = bitmap,
                                                previewMode = captureMode,
                                            )
                                            withContext(Dispatchers.IO) {
                                                mediaStoreImageSaver.saveJpeg(
                                                    sourceFile = previewFile ?: error("Preview JPEG missing."),
                                                    previewMode = captureMode,
                                                    nameSuffix = if (captureMode == PreviewMode.NORMAL) {
                                                        "PREVIEW_NORMAL"
                                                    } else {
                                                        captureMode.name
                                                    },
                                                )
                                            }
                                            onCaptureSucceeded()
                                        } catch (oom: OutOfMemoryError) {
                                            previewFile?.delete()
                                            onCaptureFailed("Not enough memory to capture preview.")
                                        } catch (throwable: Throwable) {
                                            previewFile?.delete()
                                            onCaptureFailed(throwable.message ?: "Preview capture failed.")
                                        }
                                    }
                                }
                                .onFailure { throwable ->
                                    onCaptureFailed(throwable.message ?: "Preview capture failed.")
                                }
                        }
                    }
                } catch (throwable: Throwable) {
                    onCaptureFailed(throwable.message ?: "Capture failed.")
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 30.dp),
        )

        uiState.lastError?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 168.dp, start = 24.dp, end = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private suspend fun createPreviewCaptureJpeg(
    context: android.content.Context,
    bitmap: Bitmap,
    previewMode: PreviewMode,
): File = withContext(Dispatchers.IO) {
    val capturesDir = File(context.cacheDir, "captures").apply {
        if (!exists() && !mkdirs()) {
            error("Failed to create capture cache directory.")
        }
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val outputFile = File(capturesDir, "PREVIEW_${previewMode.name}_$timestamp.jpg")
    try {
        outputFile.outputStream().use { outputStream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, PREVIEW_CAPTURE_JPEG_QUALITY, outputStream)) {
                error("Failed to encode preview JPEG.")
            }
        }
        outputFile
    } finally {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

@Composable
private fun ProcessingControls(
    previewMode: PreviewMode,
    processingParams: ProcessingParams,
    showTone: Boolean,
    showRgb: Boolean,
    onExposureChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onGammaChange: (Float) -> Unit,
    onRedGainChange: (Float) -> Unit,
    onGreenGainChange: (Float) -> Unit,
    onBlueGainChange: (Float) -> Unit,
    onResetTone: () -> Unit,
    onResetRgb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.width(216.dp),
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.42f),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 280.dp)
                .verticalScroll(scrollState)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showTone) {
                ParameterSlider(
                    label = "Exposure",
                    value = processingParams.exposure,
                    valueRange = EXPOSURE_MIN..EXPOSURE_MAX,
                    onValueChange = onExposureChange,
                )
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
            }

            if (showRgb && previewMode != PreviewMode.BW_NEGATIVE) {
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
private fun OrangeMaskControls(
    samplingState: OrangeMaskSamplingState,
    sample: OrangeMaskSample?,
    onStartSampling: () -> Unit,
    onConfirmSample: () -> Unit,
    onResetSample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val samplingActive = samplingState != OrangeMaskSamplingState.IDLE || sample != null

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = {
                if (samplingActive) {
                    onResetSample()
                } else {
                    onStartSampling()
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = if (samplingActive) "重置片基" else "片基采样")
        }
        if (samplingActive) {
            Button(
                onClick = {
                    if (samplingState == OrangeMaskSamplingState.ARMING) {
                        onConfirmSample()
                    } else {
                        onStartSampling()
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = if (samplingState == OrangeMaskSamplingState.ARMING) {
                        "确定采样"
                    } else {
                        "重新采样"
                    },
                )
            }
        }
    }
}

@Composable
private fun OrangeMaskSampleInfo(
    sample: OrangeMaskSample,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(156.dp),
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.58f),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 18.dp)
                    .background(
                        Color(
                            red = sample.red.coerceIn(0f, 1f),
                            green = sample.green.coerceIn(0f, 1f),
                            blue = sample.blue.coerceIn(0f, 1f),
                        ),
                    ),
            )
            RgbValue(label = "R", value = sample.red)
            RgbValue(label = "G", value = sample.green)
            RgbValue(label = "B", value = sample.blue)
        }
    }
}

@Composable
private fun RgbValue(
    label: String,
    value: Float,
) {
    Text(
        text = "$label ${(value * 255f).toInt()}",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun OrangeMaskMarkerOverlay(
    markerX: Float,
    markerY: Float,
    onMarkerChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val updateMarker = { offset: Offset ->
        if (size.width > 0 && size.height > 0) {
            onMarkerChange(
                (offset.x / size.width).coerceIn(0f, 1f),
                (offset.y / size.height).coerceIn(0f, 1f),
            )
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    updateMarker(offset)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        updateMarker(offset)
                    },
                    onDrag = { change, _ ->
                        updateMarker(change.position)
                    },
                )
            },
    ) {
        val center = Offset(markerX * this.size.width, markerY * this.size.height)
        val arm = 22.dp.toPx()
        val gap = 5.dp.toPx()
        drawLine(
            color = Color.White,
            start = Offset(center.x - arm, center.y),
            end = Offset(center.x - gap, center.y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(center.x + gap, center.y),
            end = Offset(center.x + arm, center.y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(center.x, center.y - arm),
            end = Offset(center.x, center.y - gap),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(center.x, center.y + gap),
            end = Offset(center.x, center.y + arm),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun FocusTouchOverlay(
    enabled: Boolean,
    indicator: FocusIndicator?,
    allModesPreview: Boolean,
    exposure: Float,
    onFocus: (FocusPoint, IntSize, Boolean) -> Unit,
    onZoom: (Float) -> Unit,
    onExposureChange: (Float) -> Unit,
    onExposureDragStarted: () -> Unit,
    onExposureDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val controlOffsetPx = with(density) { 38.dp.toPx() }
    val controlEdgePaddingPx = with(density) { 18.dp.toPx() }
    val controlHalfHeightPx = with(density) { 50.dp.toPx() }
    val controlHitSlopPx = with(density) { 22.dp.toPx() }
    val currentExposure by rememberUpdatedState(exposure)
    val currentOnExposureChange by rememberUpdatedState(onExposureChange)
    val currentOnExposureDragStarted by rememberUpdatedState(onExposureDragStarted)
    val currentOnExposureDragFinished by rememberUpdatedState(onExposureDragFinished)
    var exposureDragActive by remember { mutableStateOf(false) }
    var exposureDragStartY by remember { mutableStateOf(0f) }
    var exposureDragStartValue by remember { mutableStateOf(0f) }

    fun exposureGeometry(): ExposureControlGeometry? {
        val current = indicator ?: return null
        if (size.width <= 0 || size.height <= 0) return null

        val centerX = current.normalizedX * size.width
        val centerY = current.normalizedY * size.height
        val side = if (centerX + controlOffsetPx + controlEdgePaddingPx <= size.width) 1f else -1f
        val maxTrackX = (size.width - controlEdgePaddingPx).coerceAtLeast(controlEdgePaddingPx)
        val trackX = (centerX + side * controlOffsetPx)
            .coerceIn(controlEdgePaddingPx, maxTrackX)
        val availableHeight = size.height - 2f * controlEdgePaddingPx
        if (availableHeight <= 1f) return null
        val trackHeight = minOf(controlHalfHeightPx * 2f, availableHeight)
        val trackTop = (centerY - trackHeight / 2f)
            .coerceIn(controlEdgePaddingPx, size.height - controlEdgePaddingPx - trackHeight)
        val trackBottom = trackTop + trackHeight

        return ExposureControlGeometry(
            trackX = trackX,
            trackTop = trackTop,
            trackBottom = trackBottom,
        )
    }

    fun isExposureControlHit(offset: Offset): Boolean {
        val geometry = exposureGeometry() ?: return false
        return kotlin.math.abs(offset.x - geometry.trackX) <= controlHitSlopPx &&
            offset.y >= geometry.trackTop - controlHitSlopPx &&
            offset.y <= geometry.trackBottom + controlHitSlopPx
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(enabled, indicator) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    exposureDragActive = isExposureControlHit(down.position)
                    if (!exposureDragActive) {
                        return@awaitEachGesture
                    }

                    down.consume()
                    currentOnExposureDragStarted()
                    exposureDragStartY = down.position.y
                    exposureDragStartValue = currentExposure

                    try {
                        while (exposureDragActive) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                exposureDragActive = false
                                continue
                            }

                            val geometry = exposureGeometry()
                            if (geometry != null) {
                                val trackHeight = (geometry.trackBottom - geometry.trackTop).coerceAtLeast(1f)
                                val exposureDelta = (exposureDragStartY - change.position.y) /
                                    trackHeight * (EXPOSURE_MAX - EXPOSURE_MIN)
                                currentOnExposureChange(
                                    (exposureDragStartValue + exposureDelta)
                                        .coerceIn(EXPOSURE_MIN, EXPOSURE_MAX),
                                )
                                change.consume()
                            }
                        }
                    } finally {
                        exposureDragActive = false
                        currentOnExposureDragFinished()
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        if (size.width > 0 && size.height > 0 && !isExposureControlHit(offset)) {
                            val normalizedX = (offset.x / size.width).coerceIn(0f, 1f)
                            val normalizedY = (offset.y / size.height).coerceIn(0f, 1f)
                            onFocus(
                                mapToCameraPoint(
                                    normalizedX = normalizedX,
                                    normalizedY = normalizedY,
                                    allModesPreview = allModesPreview,
                                ),
                                size,
                                false,
                            )
                        }
                    },
                    onLongPress = { offset ->
                        if (size.width > 0 && size.height > 0 && !isExposureControlHit(offset)) {
                            val normalizedX = (offset.x / size.width).coerceIn(0f, 1f)
                            val normalizedY = (offset.y / size.height).coerceIn(0f, 1f)
                            onFocus(
                                mapToCameraPoint(
                                    normalizedX = normalizedX,
                                    normalizedY = normalizedY,
                                    allModesPreview = allModesPreview,
                                ),
                                size,
                                true,
                            )
                        }
                    },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        onZoom(zoom)
                    }
                }
            },
    ) {
        val current = indicator ?: return@Canvas
        val center = Offset(
            x = current.normalizedX * this.size.width,
            y = current.normalizedY * this.size.height,
        )
        val color = if (current.locked) Color(0xFFE20612) else Color.White
        drawCircle(
            color = color,
            radius = 24.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        if (current.locked) {
            drawLine(
                color = color,
                start = Offset(center.x - 10.dp.toPx(), center.y),
                end = Offset(center.x + 10.dp.toPx(), center.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        val geometry = exposureGeometry() ?: return@Canvas
        val trackHeight = (geometry.trackBottom - geometry.trackTop).coerceAtLeast(1f)
        val exposureProgress = ((exposure - EXPOSURE_MIN) / (EXPOSURE_MAX - EXPOSURE_MIN))
            .coerceIn(0f, 1f)
        val sunY = geometry.trackBottom - exposureProgress * trackHeight
        val sunCenter = Offset(geometry.trackX, sunY)
        val sunColor = Color(0xFFFFD54F)
        val trackColor = Color.White.copy(alpha = 0.72f)
        val radius = 4.dp.toPx()
        val rayInner = 7.dp.toPx()
        val rayOuter = 10.dp.toPx()
        val sunStrokeWidth = 1.5.dp.toPx()

        drawLine(
            color = trackColor,
            start = Offset(geometry.trackX, geometry.trackTop),
            end = Offset(geometry.trackX, geometry.trackBottom),
            strokeWidth = sunStrokeWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.45f),
            radius = 14.dp.toPx(),
            center = sunCenter,
        )
        drawCircle(
            color = sunColor,
            radius = radius,
            center = sunCenter,
            style = Stroke(width = sunStrokeWidth),
        )
        drawLine(
            color = sunColor,
            start = Offset(sunCenter.x - rayOuter, sunCenter.y),
            end = Offset(sunCenter.x - rayInner, sunCenter.y),
            strokeWidth = sunStrokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = sunColor,
            start = Offset(sunCenter.x + rayInner, sunCenter.y),
            end = Offset(sunCenter.x + rayOuter, sunCenter.y),
            strokeWidth = sunStrokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = sunColor,
            start = Offset(sunCenter.x, sunCenter.y - rayOuter),
            end = Offset(sunCenter.x, sunCenter.y - rayInner),
            strokeWidth = sunStrokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = sunColor,
            start = Offset(sunCenter.x, sunCenter.y + rayInner),
            end = Offset(sunCenter.x, sunCenter.y + rayOuter),
            strokeWidth = sunStrokeWidth,
            cap = StrokeCap.Round,
        )
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

private fun mapToCameraPoint(
    normalizedX: Float,
    normalizedY: Float,
    allModesPreview: Boolean,
): FocusPoint {
    val indicatorX = normalizedX.coerceIn(0f, 1f)
    val indicatorY = normalizedY.coerceIn(0f, 1f)
    if (!allModesPreview) {
        return FocusPoint(
            indicatorX = indicatorX,
            indicatorY = indicatorY,
            cameraX = indicatorX,
            cameraY = indicatorY,
        )
    }

    val cameraX = if (indicatorX >= 0.5f) {
        (indicatorX - 0.5f) * 2f
    } else {
        indicatorX * 2f
    }
    val cameraY = if (indicatorY >= 0.5f) {
        (indicatorY - 0.5f) * 2f
    } else {
        indicatorY * 2f
    }

    return FocusPoint(
        indicatorX = indicatorX,
        indicatorY = indicatorY,
        cameraX = cameraX.coerceIn(0f, 1f),
        cameraY = cameraY.coerceIn(0f, 1f),
    )
}

private const val EXPOSURE_MIN = -3f
private const val EXPOSURE_MAX = 3f
private const val PREVIEW_ASPECT_RATIO = 3f / 4f
private const val PREVIEW_CAPTURE_JPEG_QUALITY = 95
