# Negative Viewer Android

A minimal Android app for viewing film negatives through the camera.

## Features

- CameraX camera pipeline
- CameraX preview rendered through an OpenGL ES external OES texture
- Real-time `NORMAL` / `INVERT` shader preview
- ImageCapture photo capture
- MediaStore JPEG saving to `Pictures/NegativeViewer/`
- `NORMAL` original JPEG saving and `INVERT` RGB-inverted JPEG saving
- Minimum EXIF orientation correction for saved JPEG pixels

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

## Test

```bash
adb install -r -g app-debug.apk
adb logcat -c
adb logcat | grep -E "NegativeViewer|CameraX|AndroidRuntime|GLRenderer"
```

Manual smoke test:

- Launch, grant camera permission, and confirm live preview is visible.
- Toggle `NORMAL` / `INVERT` and confirm preview switches immediately.
- Capture in both modes and confirm images appear in `Pictures/NegativeViewer/`.
- Background and foreground the app several times and confirm preview recovers.

## Known Limitations

- Pure RGB inversion only.
- Color negative correction is not implemented.
- MVP is portrait-first.
- Rolling-shutter distortion can still appear with movement, dim light, or flickering backlights.
- Preview is shown in a 4:3 letterboxed area to better match default ImageCapture framing.
