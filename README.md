# Negative Viewer Android

`Negative Viewer Android` 是一个用于胶片底片翻拍预览与拍摄保存的 Android 应用。它通过相机实时取景、着色器预览和拍照后处理，帮助用户直接在手机上查看底片反转效果。

## 功能简介

- 实时相机预览，取景区域为 3:4 比例，便于贴近常见底片翻拍构图
- 支持多种预览模式
- `NORMAL`：原始相机画面
- `COLOR`：普通彩色反色预览
- `B&W`：黑白负片预览
- `COLOR+`：带片基采样的彩色负片校正预览
- 支持 Tone 参数调整
- `Brightness`
- `Contrast`
- `Gamma`
- 支持 RGB 三通道增益微调
- `COLOR+` 模式支持片基采样、重新采样和重置片基
- 拍照后可保存处理结果，非 `NORMAL` 模式支持单图保存或原图+处理图拼接保存
- 图片保存到系统相册 `Pictures/NegativeViewer/`

## 界面与工作流

1. 打开应用并授予相机权限。
2. 在右上角切换预览模式。
3. 在左侧打开 `Tone` 或 `RGB` 控制面板，调节实时预览效果。
4. 如果使用 `COLOR+`：
   片基采样按钮位于左上角。
   先进入采样，再确认采样；采样完成后可重新采样或重置片基。
5. 点击底部快门拍摄并保存。

## 发布信息

- 当前发布版本：`v1.0.0`
- Release APK 命名：`NegativeViewer-Android-v1.0.0.apk`
- 适用平台：Android
- 发布方式：推送 `v*` 标签后自动构建 release APK，并创建 GitHub Release

## 构建

生成 release APK：

```bash
./gradlew assembleRelease
```

默认产物路径：

```text
app/build/outputs/apk/release/NegativeViewer-Android-v1.0.0.apk
```

## 安装

```bash
adb install -r app/build/outputs/apk/release/NegativeViewer-Android-v1.0.0.apk
```

## 当前限制

- 目前仅提供 Android 版本
- 片基采样与校正依赖现场光源和底片实际状态，强背光或色偏严重时仍需手动微调
- 当前不包含自动裁切、批量处理、RAW 工作流

## 适用场景

- 使用手机或平板对胶片底片进行快速翻拍预览
- 在拍摄现场确认彩色负片反转方向和大致色彩
- 对黑白底片进行快速反相查看与保存
