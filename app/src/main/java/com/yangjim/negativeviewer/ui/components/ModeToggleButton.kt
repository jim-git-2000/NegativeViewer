package com.yangjim.negativeviewer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yangjim.negativeviewer.state.PreviewMode

fun PreviewMode.displayName(): String = when (this) {
    PreviewMode.NORMAL -> "原片"
    PreviewMode.COLOR_BASIC_INVERT -> "彩色"
    PreviewMode.BW_NEGATIVE -> "黑白"
    PreviewMode.COLOR_NEGATIVE_CORRECTED -> "彩色增强"
    PreviewMode.ALL_MODES -> "四宫格"
}

@Composable
fun ModeToggleButton(
    previewMode: PreviewMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics {
            contentDescription = "当前模式：${previewMode.displayName()}，点击切换到下一模式"
        },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("${previewMode.displayName()}  ⇄", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("切换模式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
