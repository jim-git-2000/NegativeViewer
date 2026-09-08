package com.yangjim.negativeviewer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
fun CaptureButton(
    enabled: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(76.dp).semantics {
            contentDescription = "保存当前画面到相册"
            stateDescription = if (isProcessing) "正在保存" else "就绪"
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.25f),
                content = {},
            )
            if (isProcessing) {
                CircularProgressIndicator(Modifier.size(34.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            }
        }
    }
}
