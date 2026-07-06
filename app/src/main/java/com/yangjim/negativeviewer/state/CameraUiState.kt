package com.yangjim.negativeviewer.state

data class CameraUiState(
    val hasCameraPermission: Boolean = false,
    val previewMode: PreviewMode = PreviewMode.INVERT,
    val isCapturing: Boolean = false,
    val lastError: String? = null,
)
