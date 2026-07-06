package com.yangjim.negativeviewer.state

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CameraViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun setCameraPermission(granted: Boolean) {
        _uiState.update { state ->
            state.copy(
                hasCameraPermission = granted,
                lastError = if (granted) null else "Camera permission is required.",
            )
        }
    }

    fun setPreviewMode(mode: PreviewMode) {
        _uiState.update { state ->
            state.copy(
                previewMode = mode,
                saveOutputMode = if (mode == PreviewMode.NORMAL) {
                    SaveOutputMode.PROCESSED_ONLY
                } else {
                    state.saveOutputMode
                },
                lastError = if (
                    mode == PreviewMode.COLOR_NEGATIVE_CORRECTED &&
                    state.orangeMaskSample == null
                ) {
                    "请先采样片基，未采样时按照COLOR普通反色预览"
                } else {
                    null
                },
            )
        }
    }

    fun togglePreviewMode() {
        val modes = PreviewMode.entries
        val currentIndex = modes.indexOf(_uiState.value.previewMode)
        val nextMode = modes[(currentIndex + 1).mod(modes.size)]
        setPreviewMode(nextMode)
    }

    fun toggleSaveOutputMode() {
        _uiState.update { state ->
            if (state.previewMode == PreviewMode.NORMAL) {
                state.copy(saveOutputMode = SaveOutputMode.PROCESSED_ONLY)
            } else {
                state.copy(
                    saveOutputMode = when (state.saveOutputMode) {
                        SaveOutputMode.PROCESSED_ONLY -> SaveOutputMode.ORIGINAL_AND_PROCESSED_STITCH
                        SaveOutputMode.ORIGINAL_AND_PROCESSED_STITCH -> SaveOutputMode.PROCESSED_ONLY
                    },
                    lastError = null,
                )
            }
        }
    }

    fun setBrightness(value: Float) {
        updateProcessingParams {
            copy(brightness = value)
        }
    }

    fun setContrast(value: Float) {
        updateProcessingParams {
            copy(contrast = value)
        }
    }

    fun setGamma(value: Float) {
        updateProcessingParams {
            copy(gamma = value.coerceAtLeast(MIN_GAMMA))
        }
    }

    fun setRedGain(value: Float) {
        updateProcessingParams {
            copy(redGain = value)
        }
    }

    fun setGreenGain(value: Float) {
        updateProcessingParams {
            copy(greenGain = value)
        }
    }

    fun setBlueGain(value: Float) {
        updateProcessingParams {
            copy(blueGain = value)
        }
    }

    fun resetTone() {
        updateProcessingParams {
            copy(
                brightness = ProcessingParams.Default.brightness,
                contrast = ProcessingParams.Default.contrast,
                gamma = ProcessingParams.Default.gamma,
            )
        }
    }

    fun resetRgbGain() {
        updateProcessingParams {
            copy(
                redGain = ProcessingParams.Default.redGain,
                greenGain = ProcessingParams.Default.greenGain,
                blueGain = ProcessingParams.Default.blueGain,
            )
        }
    }

    fun startOrangeMaskSampling() {
        _uiState.update { state ->
            state.copy(
                orangeMaskSamplingState = OrangeMaskSamplingState.ARMING,
                lastError = null,
            )
        }
    }

    fun setOrangeMaskSample(sample: OrangeMaskSample) {
        _uiState.update { state ->
            state.copy(
                orangeMaskSamplingState = OrangeMaskSamplingState.LOCKED,
                orangeMaskSample = sample,
                lastError = null,
            )
        }
    }

    fun resetOrangeMaskSample() {
        _uiState.update { state ->
            state.copy(
                orangeMaskSamplingState = OrangeMaskSamplingState.IDLE,
                orangeMaskSample = null,
                lastError = if (state.previewMode == PreviewMode.COLOR_NEGATIVE_CORRECTED) {
                    "请先采样片基，未采样时按照COLOR普通反色预览"
                } else {
                    null
                },
            )
        }
    }

    fun onCaptureStarted() {
        _uiState.update { state ->
            state.copy(
                isCapturing = true,
                lastError = null,
            )
        }
    }

    fun onCaptureSucceeded() {
        _uiState.update { state ->
            state.copy(
                isCapturing = false,
                lastError = null,
            )
        }
    }

    fun onCaptureFailed(message: String) {
        _uiState.update { state ->
            state.copy(
                isCapturing = false,
                lastError = message,
            )
        }
    }

    fun onCameraError(message: String) {
        _uiState.update { state ->
            state.copy(
                lastError = message,
            )
        }
    }

    private fun updateProcessingParams(update: ProcessingParams.() -> ProcessingParams) {
        _uiState.update { state ->
            state.copy(
                processingParams = state.processingParams.update(),
                lastError = null,
            )
        }
    }

    private companion object {
        const val MIN_GAMMA = 0.1f
    }
}
