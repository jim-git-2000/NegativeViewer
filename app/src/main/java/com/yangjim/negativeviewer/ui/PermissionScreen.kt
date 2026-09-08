package com.yangjim.negativeviewer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionScreen(
    permissionDenied: Boolean,
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text("NEGATIVE VIEWER", style = MaterialTheme.typography.labelLarge,
                letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                modifier = Modifier.size(112.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            ) {
                val filmColor = MaterialTheme.colorScheme.primary
                Canvas(Modifier.padding(26.dp)) {
                    drawRoundRect(filmColor, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx()))
                    for (index in 0..3) {
                        val y = size.height * (0.12f + index * 0.23f)
                        for (x in listOf(size.width * 0.08f, size.width * 0.8f)) {
                            drawRoundRect(filmColor, Offset(x, y), Size(size.width * 0.12f, size.height * 0.09f), CornerRadius(1.dp.toPx()))
                        }
                    }
                    drawRect(filmColor.copy(alpha = 0.35f), Offset(size.width * 0.29f, size.height * 0.12f), Size(size.width * 0.42f, size.height * 0.76f))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("让底片，重现光彩", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("将镜头对准胶片底片，实时预览反相效果，\n微调影调与色彩，保存眼前的画面。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
            }
            Surface(
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("从相机开始", style = MaterialTheme.typography.titleSmall)
                    Text("需要相机权限以显示实时画面。照片仅在你点击快门时保存到系统相册。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (permissionDenied) {
                        Text(
                            if (permanentlyDenied) "相机权限已关闭，请前往系统设置开启。" else "尚未获得相机权限，可以点击下方按钮重试。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(if (permanentlyDenied) "前往设置" else "开启相机", fontWeight = FontWeight.SemiBold)
            }
            Text("实时反相  /  色彩微调  /  一键保存", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PermissionScreenPreview() {
    NegativeViewerTheme {
        PermissionScreen(permissionDenied = false, permanentlyDenied = false, onRequestPermission = {})
    }
}
