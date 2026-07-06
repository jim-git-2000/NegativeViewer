# Negative Viewer Android

A minimal Android app for viewing film negatives through the camera.

This repository is currently at the early MVP stage. It contains CI build setup, camera permission flow, CameraX preview rendered through OpenGL, real-time NORMAL/INVERT shader mode, ImageCapture, MediaStore JPEG saving, RGB inversion for saved photos, and minimum EXIF orientation correction.

## Planned MVP Features

- CameraX camera pipeline
- ImageCapture photo capture
- MediaStore JPEG saving to `Pictures/NegativeViewer/`
- Normal / inverted photo saving
- EXIF orientation correction for saved JPEG pixels
- CameraX preview rendered through an OpenGL ES external OES texture
- OpenGL ES shader preview
- Real-time NORMAL / INVERT preview mode
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

- Photo capture saves `NORMAL` as the original JPEG and `INVERT` as an RGB-inverted JPEG.
- Saved JPEG pixels are rotated/flipped from the capture EXIF orientation before writing to MediaStore.
- Preview is live CameraX frames rendered by OpenGL, with NORMAL / INVERT shader switching.
- CameraX also provides ImageCapture for saved photos.
- Pure RGB inversion only is planned for the first MVP.
- Color negative correction is not implemented.
- MVP is portrait-first.
