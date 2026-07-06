package com.yangjim.negativeviewer.state

enum class PreviewMode {
    NORMAL,
    COLOR_BASIC_INVERT,
    BW_NEGATIVE,
    COLOR_NEGATIVE_CORRECTED,
}

enum class SaveOutputMode {
    JPEG,
}

enum class CropCorrectionMode {
    NONE,
}

enum class OrangeMaskSamplingState {
    IDLE,
    SAMPLING,
    SAMPLED,
}

data class OrangeMaskSample(
    val red: Float,
    val green: Float,
    val blue: Float,
)

data class ProcessingParams(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val gamma: Float = 1f,
    val redGain: Float = 1f,
    val greenGain: Float = 1f,
    val blueGain: Float = 1f,
) {
    companion object {
        val Default = ProcessingParams()
    }
}
