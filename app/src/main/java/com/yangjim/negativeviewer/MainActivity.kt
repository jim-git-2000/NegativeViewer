package com.yangjim.negativeviewer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yangjim.negativeviewer.state.CameraViewModel
import com.yangjim.negativeviewer.ui.CameraScreen
import com.yangjim.negativeviewer.ui.PermissionScreen

class MainActivity : ComponentActivity() {
    private val cameraViewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraViewModel.setCameraPermission(hasCameraPermission())

        setContent {
            NegativeViewerTheme {
                NegativeViewerApp(cameraViewModel = cameraViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cameraViewModel.setCameraPermission(hasCameraPermission())
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun NegativeViewerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFE0E0E0),
            background = Color(0xFF101010),
            surface = Color(0xFF101010),
            onBackground = Color(0xFFF2F2F2),
            onSurface = Color(0xFFF2F2F2),
        ),
        content = content,
    )
}

@Composable
private fun NegativeViewerApp(cameraViewModel: CameraViewModel) {
    val context = LocalContext.current
    val activity = context as Activity
    val uiState by cameraViewModel.uiState.collectAsState()
    var hasRequestedCameraPermission by rememberSaveable { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRequestedCameraPermission = true
        cameraViewModel.setCameraPermission(granted)
    }

    val permanentlyDenied = hasRequestedCameraPermission &&
        !uiState.hasCameraPermission &&
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.CAMERA,
        )

    if (uiState.hasCameraPermission) {
        CameraScreen(
            uiState = uiState,
            onToggleMode = cameraViewModel::togglePreviewMode,
            onCapture = cameraViewModel::onCaptureClicked,
            onCameraError = cameraViewModel::onCameraError,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        PermissionScreen(
            permissionDenied = hasRequestedCameraPermission,
            permanentlyDenied = permanentlyDenied,
            onRequestPermission = {
                if (permanentlyDenied) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
