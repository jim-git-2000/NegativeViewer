# Negative Viewer Android

A minimal Android app for viewing film negatives through the camera.

This repository is currently at the early MVP stage. It contains CI build setup, camera permission flow, a basic CameraX PreviewView camera preview, ImageCapture, and MediaStore JPEG saving.

## Planned MVP Features

- CameraX camera pipeline
- ImageCapture photo capture
- MediaStore JPEG saving to `Pictures/NegativeViewer/`
- Real-time inverted camera preview
- OpenGL ES shader preview
- Normal / inverted mode
- ImageCapture photo capture
- MediaStore JPEG saving

## Non-goals for MVP

- Color negative film base correction
- Automatic cropping
- RAW capture
- iOS support

## Build

Debug APKs are built by GitHub Actions.

## Install

1. Open GitHub Actions.
2. Select the latest successful Android Debug Build.
3. Download `negative-viewer-debug-apk`.
4. Unzip the artifact.
5. Install the APK on an Android device.

```bash
adb install -r app-debug.apk
```

## Known Limitations

- Photo capture currently saves the original JPEG to `Pictures/NegativeViewer/`.
- Camera preview is currently normal CameraX PreviewView output, not OpenGL shader output.
- Pure RGB inversion only is planned for the first MVP.
- Color negative correction is not implemented.
- MVP is portrait-first.
