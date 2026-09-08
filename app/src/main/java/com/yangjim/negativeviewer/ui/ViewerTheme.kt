package com.yangjim.negativeviewer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 中性色取景环境，暖色仅用于标记可操作和选中状态。
@Composable
fun NegativeViewerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFE2C69A),
            onPrimary = Color(0xFF292217),
            primaryContainer = Color(0xFF393125),
            onPrimaryContainer = Color(0xFFF2DCB9),
            secondary = Color(0xFFCDC5B9),
            background = Color(0xFF101113),
            onBackground = Color(0xFFF3F0EA),
            surface = Color(0xFF1B1C1F),
            onSurface = Color(0xFFF3F0EA),
            surfaceVariant = Color(0xFF28292C),
            onSurfaceVariant = Color(0xFFB8B7B2),
            outline = Color(0xFF595A5D),
            outlineVariant = Color(0xFF343538),
            error = Color(0xFFFFB4AB),
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
