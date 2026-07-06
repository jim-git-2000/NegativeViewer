package com.yangjim.negativeviewer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CaptureButton(
    enabled: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = if (isProcessing) {
        rememberInfiniteTransition(label = "capture_processing").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "capture_ring_rotation",
        ).value
    } else {
        0f
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(78.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(4.dp, Color.White.copy(alpha = if (enabled) 0.95f else 0.45f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isProcessing) {
                Surface(
                    modifier = Modifier
                        .size(74.dp)
                        .rotate(rotation),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(4.dp, Color(0xFF7A1E1E)),
                    content = {},
                )
            }
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = if (enabled) 0.92f else 0.38f),
                content = {},
            )
        }
    }
}
