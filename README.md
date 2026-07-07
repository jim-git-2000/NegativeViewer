# Negative Viewer Android

`Negative Viewer Android` 是一个用于胶片底片翻拍预览与拍摄保存的 Android 应用。它通过实时相机取景、OpenGL 预览和拍照后处理，帮助用户在翻拍底片时快速查看反相效果并保存结果。

## 功能简介

- 实时相机预览，取景区域为 3:4 比例，适合常见底片翻拍构图
- 支持 `NORMAL`、`COLOR`、`B&W`、`COLOR+`、`ALL` 五种预览模式
- `ALL` 模式以 2x2 四宫格同时显示四种图像效果，并固定保存四宫格结果
- `COLOR+` 模式支持片基采样、重新采样、重置片基，并显示采样色块与 RGB 数值
- 支持 Tone 调整：`Exposure`、`Brightness`、`Contrast`、`Gamma`
- 支持 RGB 三通道增益微调
- 支持点击对焦；长按可保持当前对焦/测光区域不自动取消；对焦环旁可拖动小太阳调节曝光
- 拍照后可保存处理结果，非 `NORMAL` 模式支持单图保存或原图 + 处理图拼接保存
- 图片保存到系统相册 `Pictures/NegativeViewer/`

## 界面与工作流

1. 打开应用并授予相机权限。
2. 在右上角切换预览模式。
3. 点击预览画面进行对焦，长按可保持当前对焦/测光区域。
4. 在左侧打开 `Tone` 或 `RGB` 控制面板，调节实时预览效果。
5. 使用 `COLOR+` 时，先在左上角进入片基采样，再确认采样；采样完成后可重新采样或重置片基。
6. 点击底部快门拍摄并保存。

## 发布信息

- 当前发布版本：`v1.1.0`
- Release APK 命名：`NegativeViewer-Android-v1.1.0.apk`
- 适用平台：Android
- 发布方式：推送 `v*` 标签后自动构建 release APK，并创建 GitHub Release

## 构建

生成 release APK：

```bash
./gradlew assembleRelease
```

默认产物路径：

```text
app/build/outputs/apk/release/NegativeViewer-Android-v1.1.0.apk
```

## 安装

```bash
adb install -r app/build/outputs/apk/release/NegativeViewer-Android-v1.1.0.apk
```

## 当前限制

- 目前仅提供 Android 版本
- 片基采样与校正依赖现场光源和底片实际状态，强背光或色偏严重时仍需手动微调
- 长按对焦会保持 CameraX 的对焦/测光区域，但不同设备对连续自动对焦和镜头锁定的支持可能不同
- 当前不包含自动裁切、批量处理、RAW 工作流

## 适用场景

- 使用手机或平板对胶片底片进行快速翻拍预览
- 在拍摄现场确认彩色负片反转方向和大致色彩
- 对黑白底片进行快速反相查看与保存
