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
                lastMessage = "${mode.name} preview selected.",
                lastError = null,
            )
        }
    }

    fun togglePreviewMode() {
        val nextMode = when (_uiState.value.previewMode) {
            PreviewMode.NORMAL -> PreviewMode.INVERT
            PreviewMode.INVERT -> PreviewMode.NORMAL
        }
        setPreviewMode(nextMode)
    }

    fun onCaptureClicked() {
        _uiState.update { state ->
            state.copy(
                lastMessage = "Capture is not implemented yet.",
                lastError = null,
            )
        }
    }

    fun onCameraError(message: String) {
        _uiState.update { state ->
            state.copy(
                lastMessage = null,
                lastError = message,
            )
        }
    }
}
