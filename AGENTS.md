# NegativeViewer 项目规则

## 项目定位

这是一个 Android 胶片底片实时预览与保存应用。CameraX 提供相机纹理，OpenGL ES shader 负责实时处理和预览帧导出。

## 构建与验证

- 正式构建与检查以 `.github/workflows/` 中的 GitHub Actions 为准。
- 本地已有 Android SDK/JDK 时可运行 `./gradlew :app:assembleDebug`；不要为了验证临时安装或升级开发环境。
- 改动至少应保持 debug 编译通过；涉及相机、OpenGL、对焦或保存链路时，还需在 Android 真机验收。
- 发布由 `v*` 标签触发 `.github/workflows/android-release.yml`；不要自动推送或打标签。

## 技术栈

- Kotlin、Jetpack Compose、CameraX、OpenGL ES、MediaStore
- Android `minSdk 29`、`compileSdk/targetSdk 35`、JDK 17
- Gradle Wrapper 与 GitHub Actions

## 目录与约定

- `app/src/main/java/com/yangjim/negativeviewer/`：应用代码；按 `camera`、`gl`、`processing`、`state`、`storage`、`ui` 分层。
- `app/src/main/res/`：Android 资源；`.github/workflows/`：CI 与发布流程。
- `README.md` 是现役功能、使用和构建说明的权威入口；`docs/PLAN*.md` 是早期规划，不作为当前实现依据。
- 面向用户的文档与注释使用中文；代码标识符和配置键使用英文。
- 不提交密钥、keystore、APK、AAB、构建目录或本机 Android 配置。

## 当前状态与下一步

- 当前候选版本为 `1.4.0`；快门保存 OpenGL 渲染的预览帧，不是 CameraX 高分辨率静态照片。
- 当前没有自动化测试；CI 只执行 debug/release 编译，功能正确性依赖 Android 真机验收。
- 后续修改优先保持预览与保存共用同一 shader 参数，并补充关键状态与图像处理测试。
