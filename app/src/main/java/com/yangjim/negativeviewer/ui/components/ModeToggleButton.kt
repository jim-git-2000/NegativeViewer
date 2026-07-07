package com.yangjim.negativeviewer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yangjim.negativeviewer.state.PreviewMode

@Composable
fun ModeToggleButton(
    previewMode: PreviewMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.58f),
        contentColor = Color(0xFFE20612),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f)),
    ) {
        Text(
            text = when (previewMode) {
                PreviewMode.NORMAL -> "NORMAL"
                PreviewMode.COLOR_BASIC_INVERT -> "COLOR"
                PreviewMode.BW_NEGATIVE -> "B&W"
                PreviewMode.COLOR_NEGATIVE_CORRECTED -> "COLOR+"
                PreviewMode.ALL_MODES -> "ALL"
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
