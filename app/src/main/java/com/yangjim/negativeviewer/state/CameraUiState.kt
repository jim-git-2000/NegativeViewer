package com.yangjim.negativeviewer.state

data class CameraUiState(
    val hasCameraPermission: Boolean = false,
    val previewMode: PreviewMode = PreviewMode.COLOR_BASIC_INVERT,
    val saveOutputMode: SaveOutputMode = SaveOutputMode.PROCESSED_ONLY,
    val cropCorrectionMode: CropCorrectionMode = CropCorrectionMode.NONE,
    val orangeMaskSamplingState: OrangeMaskSamplingState = OrangeMaskSamplingState.IDLE,
    val orangeMaskSample: OrangeMaskSample? = null,
    val processingParams: ProcessingParams = ProcessingParams.Default,
    val isCapturing: Boolean = false,
    val lastError: String? = null,
)
