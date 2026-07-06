# Negative Viewer Android

A minimal Android app for viewing film negatives through the camera.

This repository is currently at the MVP skeleton stage. It only contains a Compose placeholder screen and CI build setup.

## Planned MVP Features

- Real-time inverted camera preview
- CameraX camera pipeline
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

- This is a skeleton app; camera functionality is not implemented yet.
- Pure RGB inversion only is planned for the first MVP.
- Color negative correction is not implemented.
- MVP is portrait-first.
