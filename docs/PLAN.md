
# PLAN.md — Android 负片实时查看器 MVP 工程计划

版本：v2  
日期：2026-07-06  
目标环境：Ubuntu + VS Code + Codex + GitHub Actions + Android 真机  
项目暂定名：Negative Viewer Android  
建议包名：`com.yangjim.negativeviewer`

---

## 0. 一句话目标

开发一个 Android App，通过后置摄像头实时查看胶片负片。预览画面走 CameraX + OpenGL ES shader 实时反色；拍照走 CameraX ImageCapture 获取高质量原始照片，再后台反色处理，并通过 MediaStore 保存到系统相册。

---

## 1. 约束条件

### 1.1 明确约束

本项目第一阶段必须遵守以下约束：

- 只做 Android。
- 只做 MVP，不做完整扫描软件。
- 本地开发环境尽量少安装东西。
- Ubuntu 上主要使用 VS Code 和 Codex 写代码。
- 构建尽量交给 GitHub Actions。
- APK 从 GitHub Actions artifact 下载。
- 本地只做安装、试用、日志查看和少量手动排查。
- 预览实时反色必须走 GPU shader，不走 CPU 逐帧 Bitmap。
- 拍照保存必须使用 CameraX ImageCapture。
- 保存到相册必须使用 MediaStore。
- 彩色负片校正只预留，不在 MVP 中实现。

### 1.2 推荐本地安装

最低本地工具：

```bash
sudo apt update
sudo apt install git
```

强烈建议额外安装：

```bash
sudo apt install android-tools-adb
```

本地建议安装但不是强制：

- VS Code
- GitHub CLI
- Codex CLI 或 VS Code 内的 Codex 插件/调用方式

### 1.3 第一阶段尽量不安装

第一阶段尽量不在 Ubuntu 本地安装：

- Android Studio
- Android SDK
- Android Emulator
- 全局 Gradle
- 全局 JDK

原因：

- 本地构建 Android 项目需要 SDK、JDK、AGP、Gradle、license 等环境，配置成本较高。
- 本项目初期可接受“编辑 -> 推送 -> GitHub Actions 编译 -> 下载 APK -> 真机测试”的循环。
- 真机调试比模拟器更符合相机/GPU项目特性。

### 1.4 这个约束带来的代价

少装本地工具会降低反馈速度。每次编译错误都需要推送到 GitHub Actions 后才知道。为降低损失，必须采用小步提交策略：

- 每次只改一个功能点。
- 每个阶段都要保证仓库可构建。
- Codex 每次只接收一个小任务。
- GitHub Actions 失败后先修编译，不继续加功能。
- 每个 milestone 完成后打 tag 或保留清晰 commit。

---

## 2. 技术决策

## 2.1 技术栈

| 模块 | 选择 | 原因 |
|---|---|---|
| App 平台 | Android | 第一阶段只做 Android |
| 语言 | Kotlin | Android 原生开发主流选择 |
| UI | Jetpack Compose | 新项目简洁；也便于状态驱动 UI |
| 相机框架 | CameraX | 比 Camera2 低复杂度，兼容性更好 |
| 预览 GPU 处理 | OpenGL ES 2.0/3.0 | 适合实时纹理反色 |
| 相机帧到 GPU | SurfaceTexture + external OES texture | Camera preview 可输出到 SurfaceTexture |
| 拍照 | CameraX ImageCapture | 用于高质量静态照片 |
| 保存 | MediaStore | 适合保存到系统相册 |
| 构建 | Gradle Wrapper + GitHub Actions | 降低本地环境要求 |
| 安装调试 | adb | 下载 APK 后安装和看 logcat |

---

## 2.2 MVP 的核心链路

### 实时预览链路

```text
CameraX Preview
  -> Preview.setSurfaceProvider(...)
  -> 自定义 SurfaceProvider
  -> SurfaceTexture / Surface
  -> OpenGL ES external OES texture
  -> Fragment shader
  -> GLSurfaceView 显示
```

预览模式：

```text
NORMAL: 显示原始相机画面
INVERT: 显示 RGB 纯反色画面
```

MVP 默认模式：

```text
INVERT
```

### 拍照保存链路

```text
用户点击拍照
  -> CameraX ImageCapture 拍摄原始 JPEG
  -> 原始 JPEG 写入 app cache 临时文件
  -> 后台线程读取临时 JPEG
  -> 处理 EXIF 方向
  -> Bitmap RGB 反色
  -> JPEG 压缩
  -> MediaStore 写入 Pictures/NegativeViewer/
  -> 删除临时文件
  -> Toast / Snackbar 提示保存成功
```

MVP 不直接保存预览帧。预览帧分辨率通常低于拍照分辨率，保存预览帧会降低成片质量。

---

## 2.3 为什么先做 PreviewView，再换 OpenGL

虽然最终目标是 CameraX + OpenGL ES，但第一步仍然建议先用 CameraX + PreviewView 跑通普通预览。

原因：

- PreviewView 是 CameraX 官方推荐的基础预览 View。
- PreviewView 可以快速验证相机权限、生命周期、后置摄像头、ImageCapture 是否正常。
- OpenGL 接入失败时，可以和普通 PreviewView 版本对比定位问题。
- 如果一开始同时做权限、CameraX、SurfaceTexture、GLSL、ImageCapture、MediaStore，调试复杂度会过高。

阶段策略：

```text
先普通预览 -> 再拍照保存 -> 再拍照反色 -> 再接 OpenGL -> 再实时反色
```

---

## 2.4 Android 版本策略

建议 MVP 先设：

```kotlin
minSdk = 29
targetSdk = 当前 Android Gradle Plugin 支持的稳定 SDK
compileSdk = 当前 Android Gradle Plugin 支持的稳定 SDK
```

选择 `minSdk = 29` 的原因：

- Android 10/API 29 引入 scoped storage 体系后，使用 MediaStore 保存自己创建的图片不需要额外存储权限。
- 避免 Android 8/9 的 `WRITE_EXTERNAL_STORAGE` 分支。
- 降低 MVP 权限和存储兼容复杂度。

如果后续需要支持 Android 8/9：

```text
新增 legacy storage 分支
新增 WRITE_EXTERNAL_STORAGE 兼容逻辑
重新验收相册保存行为
```

---

## 2.5 权限策略

MVP 只申请：

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

建议同时声明：

```xml
<uses-feature
    android:name="android.hardware.camera"
    android:required="true" />
```

是否设置 `required=true` 的取舍：

- 这个 App 没有摄像头基本不可用。
- 因此 MVP 可以将 camera 硬件设为 required。
- 如果后续想让无相机设备也可安装并查看导入图片，再改为 `required=false`。

MVP 不申请：

```xml
READ_MEDIA_IMAGES
READ_EXTERNAL_STORAGE
WRITE_EXTERNAL_STORAGE
MANAGE_EXTERNAL_STORAGE
```

原因：

- App 只保存自己拍摄并处理的图片，不读取其他 App 的相册图片。
- Android 10+ 上保存自己创建的 MediaStore 图片不需要存储读取权限。
- 不应申请大而无用的媒体库权限。

---

## 3. 产品范围

## 3.1 MVP 必须实现

### 启动与权限

- App 启动。
- 检查 CAMERA 权限。
- 未授权时显示解释和授权按钮。
- 授权后进入相机页。
- 拒绝权限时显示可恢复的提示，不崩溃。

### 相机预览

- 默认使用后置主摄。
- 默认竖屏。
- 默认 `INVERT` 模式。
- 支持 `NORMAL / INVERT` 切换。
- 预览画面实时更新。
- 前后台切换后能恢复。

### 拍照

- 底部拍照按钮。
- 拍照期间按钮禁用。
- 拍照完成后恢复按钮。
- 拍照失败显示错误提示。
- 连续拍照不崩溃。

### 图片处理

- NORMAL 模式保存原图。
- INVERT 模式保存 RGB 反色图。
- RGB 反色公式：

```text
R' = 255 - R
G' = 255 - G
B' = 255 - B
```

### 保存

- 使用 MediaStore 保存。
- 保存目录：

```text
Pictures/NegativeViewer/
```

- 文件名格式：

```text
NEG_yyyyMMdd_HHmmss_INVERT.jpg
NEG_yyyyMMdd_HHmmss_NORMAL.jpg
```

- 保存成功后系统相册可见。
- 保存失败时提示错误。

### 构建与下载

- GitHub Actions 可自动构建 debug APK。
- APK 作为 artifact 上传。
- 用户可从 Actions 页面下载并安装。

---

## 3.2 MVP 暂不实现

以下功能明确不属于 MVP：

- 彩色负片橙色片基校正。
- 自动白平衡。
- 自动色阶。
- 手动 RGB 通道增益。
- 亮度/对比度/Gamma 滑杆。
- 黑白负片专用 shader。
- 胶片边框自动裁切。
- 多镜头选择。
- 手动对焦。
- 手动曝光。
- 闪光灯/手电筒。
- 水平仪。
- 网格线。
- RAW/DNG 保存。
- 批量扫描。
- 导入已有照片反色。
- 分享按钮。
- Play Store 发布。
- release 签名。
- iOS 版本。

---

## 3.3 Post-MVP 优先级

后续版本建议按以下顺序推进：

1. 黑白负片专用模式。
2. 亮度/对比度/Gamma。
3. RGB 通道增益。
4. 点选片基区域做橙色片基校正。
5. 保存原图 + 处理图双文件。
6. EXIF 方向完整保留。
7. 预览比例和旋转全面修复。
8. 多设备兼容性测试。
9. 拍照处理 GPU offscreen 化。
10. 导入已有负片照片进行反色。

---

## 4. 目标用户流程

## 4.1 首次使用

```text
打开 App
  -> 显示权限说明
  -> 点击允许相机权限
  -> 进入相机页
  -> 默认看到反色预览
  -> 对准胶片负片
  -> 调整手机距离和底片背光
  -> 点击拍照
  -> 系统相册出现反色 JPEG
```

## 4.2 非首次使用

```text
打开 App
  -> 直接进入反色相机预览
  -> 可切换 NORMAL / INVERT
  -> 点击拍照保存
```

## 4.3 权限被拒绝

```text
打开 App
  -> 无相机权限
  -> 显示说明：该 App 需要相机来实时查看胶片负片
  -> 提供“授权相机”按钮
  -> 如果永久拒绝，提供“去系统设置开启权限”的提示
```

---

## 5. UI 设计草图

MVP 界面只保留必要控件。

```text
┌──────────────────────────┐
│                          │
│                          │
│       相机实时预览        │
│      默认为反色画面       │
│                          │
│                          │
│                    [INV] │
│                          │
├──────────────────────────┤
│                          │
│          [  ●  ]          │
│                          │
└──────────────────────────┘
```

控件说明：

| 控件 | 位置 | 功能 |
|---|---|---|
| 预览区 | 全屏背景 | 显示 OpenGL 相机画面 |
| 模式按钮 | 右上角 | NORMAL / INVERT 切换 |
| 拍照按钮 | 底部居中 | 触发 ImageCapture |
| 状态提示 | 底部或 Toast | 保存成功/失败 |

MVP 不做复杂设置页。

---

## 6. 推荐仓库结构

```text
negative-viewer-android/
├── .github/
│   └── workflows/
│       ├── android-debug.yml
│       └── android-pr-check.yml
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/
│           │   └── com/
│           │       └── yangjim/
│           │           └── negativeviewer/
│           │               ├── MainActivity.kt
│           │               ├── NegativeViewerApp.kt
│           │               ├── ui/
│           │               │   ├── CameraScreen.kt
│           │               │   ├── PermissionScreen.kt
│           │               │   └── components/
│           │               │       ├── CaptureButton.kt
│           │               │       └── ModeToggleButton.kt
│           │               ├── state/
│           │               │   ├── PreviewMode.kt
│           │               │   ├── CameraUiState.kt
│           │               │   └── CameraViewModel.kt
│           │               ├── camera/
│           │               │   ├── CameraXController.kt
│           │               │   ├── ImageCaptureController.kt
│           │               │   └── CameraError.kt
│           │               ├── gl/
│           │               │   ├── CameraGlView.kt
│           │               │   ├── CameraRenderer.kt
│           │               │   ├── CameraSurfaceProvider.kt
│           │               │   ├── ShaderProgram.kt
│           │               │   ├── FullscreenQuad.kt
│           │               │   └── GlUtils.kt
│           │               ├── processing/
│           │               │   ├── NegativeBitmapProcessor.kt
│           │               │   ├── BitmapDecodeUtils.kt
│           │               │   └── ExifOrientationUtils.kt
│           │               ├── storage/
│           │               │   ├── MediaStoreImageSaver.kt
│           │               │   └── TempImageStore.kt
│           │               └── util/
│           │                   ├── AppDispatchers.kt
│           │                   ├── TimeNames.kt
│           │                   └── LogTags.kt
│           └── res/
│               ├── values/
│               │   ├── strings.xml
│               │   └── themes.xml
│               └── xml/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── README.md
├── PLAN.md
└── .gitignore
```

---

## 7. 状态模型

### 7.1 PreviewMode

```kotlin
enum class PreviewMode {
    NORMAL,
    INVERT
}
```

### 7.2 CameraUiState

```kotlin
data class CameraUiState(
    val hasCameraPermission: Boolean = false,
    val previewMode: PreviewMode = PreviewMode.INVERT,
    val isCapturing: Boolean = false,
    val lastMessage: String? = null,
    val lastError: String? = null
)
```

### 7.3 状态原则

- UI 不直接持有 CameraX 对象。
- UI 只表达状态和触发事件。
- CameraXController 管理 CameraX 生命周期。
- CameraRenderer 管理 GL 生命周期。
- ViewModel 保存模式、拍照状态、提示信息。
- 拍照时读取 ViewModel 当前模式，保证预览和保存结果一致。

---

## 8. OpenGL 实现规格

## 8.1 OpenGL 目标

MVP 只需要：

- 创建 external OES texture。
- 接收 CameraX Preview 的帧。
- 绘制一个覆盖全屏的矩形。
- 在 fragment shader 中做 NORMAL / INVERT。
- 按需渲染，减少功耗。

## 8.2 GLSurfaceView 设置

建议：

```kotlin
setEGLContextClientVersion(2)
setRenderer(cameraRenderer)
renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
```

原则：

- 不使用连续渲染。
- `SurfaceTexture.onFrameAvailable` 回调时调用 `requestRender()`。
- `onDrawFrame()` 中调用 `surfaceTexture.updateTexImage()`。
- 每次 draw 使用最新 transform matrix。

## 8.3 SurfaceTexture 接入流程

推荐流程：

```text
CameraScreen 创建 CameraGlView
  -> CameraGlView 创建 CameraRenderer
  -> CameraRenderer.onSurfaceCreated 创建 OES texture
  -> CameraRenderer 创建 SurfaceTexture
  -> CameraRenderer 通知 CameraXController：SurfaceTexture ready
  -> CameraXController 创建 Preview
  -> Preview.setSurfaceProvider(customProvider)
  -> 收到 SurfaceRequest
  -> renderer 设置 SurfaceTexture buffer size
  -> 用 SurfaceTexture 创建 Surface
  -> surfaceRequest.provideSurface(surface, executor, resultListener)
```

关键点：

- `SurfaceTexture` 必须绑定到 GL texture。
- `SurfaceTexture.setDefaultBufferSize(width, height)` 应使用 CameraX 请求的分辨率。
- `Surface` 生命周期结束时释放。
- GL 资源释放必须在 GL 线程中执行。
- CameraX 解绑和 GL 释放要有顺序，避免黑屏或 native crash。

## 8.4 Vertex Shader

```glsl
attribute vec4 aPosition;
attribute vec4 aTexCoord;

uniform mat4 uTexMatrix;

varying vec2 vTexCoord;

void main() {
    gl_Position = aPosition;
    vTexCoord = (uTexMatrix * aTexCoord).xy;
}
```

## 8.5 Fragment Shader

```glsl
#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES uCameraTexture;
uniform int uInvertEnabled;

varying vec2 vTexCoord;

void main() {
    vec4 color = texture2D(uCameraTexture, vTexCoord);

    if (uInvertEnabled == 1) {
        gl_FragColor = vec4(1.0 - color.rgb, color.a);
    } else {
        gl_FragColor = color;
    }
}
```

## 8.6 后续黑白模式预留

后续可加：

```glsl
float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
float inverted = 1.0 - gray;
gl_FragColor = vec4(vec3(inverted), color.a);
```

但 MVP 先不加，避免 UI 和状态复杂化。

---

## 9. 拍照与图片处理规格

## 9.1 为什么拍照不走预览帧

预览帧：

- 可能分辨率较低。
- 可能经过裁切和缩放。
- 可能不等于拍照质量。
- 从 GPU 读回像素需要 `glReadPixels`，会阻塞渲染管线。

MVP 使用 ImageCapture 拍摄原始 JPEG，然后后台处理。

## 9.2 临时文件策略

建议流程：

```text
ImageCapture -> cacheDir/raw/RAW_timestamp.jpg
处理后 -> MediaStore/Pictures/NegativeViewer/NEG_timestamp_INVERT.jpg
删除 cacheDir/raw/RAW_timestamp.jpg
```

优点：

- ImageCapture 输出路径清晰。
- 不污染系统相册。
- 处理失败时不会生成半成品。
- 便于调试时临时保留 raw 文件。

MVP 默认处理后删除原始缓存文件。

## 9.3 Bitmap 处理策略

基础实现：

```kotlin
fun invertBitmap(src: Bitmap): Bitmap {
    val bitmap = src.copy(Bitmap.Config.ARGB_8888, true)
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)

    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    for (i in pixels.indices) {
        val c = pixels[i]
        val a = c ushr 24 and 0xff
        val r = c ushr 16 and 0xff
        val g = c ushr 8 and 0xff
        val b = c and 0xff

        pixels[i] =
            (a shl 24) or
            ((255 - r) shl 16) or
            ((255 - g) shl 8) or
            (255 - b)
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}
```

## 9.4 内存风险

高分辨率 JPEG 解码为 Bitmap 可能占用较大内存。MVP 处理：

- 先接受。
- 处理过程放到后台线程。
- 捕获 `OutOfMemoryError` 或解码失败。
- 如果低端机失败，降低 ImageCapture 分辨率或做采样解码。

后续优化：

- 分块处理。
- GPU offscreen FBO 处理。
- 限制最大输出边长，例如 3000px。
- 保存质量可配置。

## 9.5 EXIF 方向

MVP 至少要验证方向。如果保存结果方向错误，加入以下处理：

```text
读取临时 JPEG EXIF orientation
  -> 解码 Bitmap
  -> 按 orientation 旋转/翻转
  -> 反色
  -> 保存新 JPEG
```

是否保留完整 EXIF：

- MVP 可暂不完整保留。
- 但至少应保证相册显示方向正确。
- 后续再复制时间、设备、镜头等 EXIF 字段。

---

## 10. MediaStore 保存规格

## 10.1 保存位置

```text
MediaStore.Images.Media.EXTERNAL_CONTENT_URI
RELATIVE_PATH = Pictures/NegativeViewer
DISPLAY_NAME = NEG_yyyyMMdd_HHmmss_MODE.jpg
MIME_TYPE = image/jpeg
```

Android 10+ 推荐使用：

```text
IS_PENDING = 1
写入完成
IS_PENDING = 0
```

原因：

- 避免相册或其他 App 读取到尚未写完的文件。
- 写入完成后再让系统索引可见。

## 10.2 失败处理

如果保存失败：

- 关闭 OutputStream。
- 如果已经创建 MediaStore Uri，尝试删除。
- UI 显示错误。
- logcat 输出异常栈。

## 10.3 权限策略

MVP 设 `minSdk=29` 时：

- 保存自己创建的图片到 MediaStore 不需要存储权限。
- 不读其他 App 图片，不申请 `READ_MEDIA_IMAGES`。
- 不使用 `MANAGE_EXTERNAL_STORAGE`。

---

## 11. GitHub Actions 策略

## 11.1 Workflow 目标

至少两个 workflow：

```text
android-debug.yml      push 到 main 或手动触发，生成 APK artifact
android-pr-check.yml   PR 时执行编译和 lint，不上传 APK 或短期保留 artifact
```

如果项目早期不使用 PR，可先只建 `android-debug.yml`。

## 11.2 推荐 android-debug.yml

```yaml
name: Android Debug Build

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build-debug:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17

      - name: Setup Android SDK
        uses: android-actions/setup-android@v4

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Make Gradle executable
        run: chmod +x ./gradlew

      - name: Build debug APK
        run: ./gradlew :app:assembleDebug --stacktrace

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: negative-viewer-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          retention-days: 14
```

说明：

- `setup-android` 用于降低 runner Android SDK 差异导致的问题。
- `setup-gradle` 用于 Gradle 缓存和构建摘要。
- debug APK 不需要 release keystore。
- artifact 保留 14 天足够日常测试。

## 11.3 推荐 android-pr-check.yml

```yaml
name: Android PR Check

on:
  pull_request:
    branches: [ main ]

jobs:
  check:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17

      - name: Setup Android SDK
        uses: android-actions/setup-android@v4

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Make Gradle executable
        run: chmod +x ./gradlew

      - name: Compile debug
        run: ./gradlew :app:assembleDebug --stacktrace

      - name: Run lint
        run: ./gradlew :app:lintDebug --stacktrace
```

MVP 早期如果 lint 噪声过大，可以先只跑 `assembleDebug`，等架构稳定后再开启 lint gate。

---

## 12. 本地开发工作流

## 12.1 标准循环

```text
1. 在 VS Code 中打开仓库
2. 用 Codex 完成一个小任务
3. 人工 review diff
4. git add / commit
5. git push
6. 等 GitHub Actions 构建
7. 下载 APK artifact
8. adb install -r app-debug.apk
9. 手机上测试
10. adb logcat 看错误
11. 记录问题到 issue 或 TODO
```

## 12.2 命令示例

```bash
git status
git add .
git commit -m "Add CameraX preview skeleton"
git push origin main
```

安装 APK：

```bash
adb devices
adb install -r app-debug.apk
```

查看日志：

```bash
adb logcat | grep NegativeViewer
```

更稳的日志方式：

```bash
adb logcat -c
adb logcat | grep -E "NegativeViewer|CameraX|AndroidRuntime|GLRenderer"
```

卸载 App：

```bash
adb uninstall com.yangjim.negativeviewer
```

授予权限安装：

```bash
adb install -r -g app-debug.apk
```

---

## 13. 分阶段开发计划

# 阶段 0：项目初始化与 CI 骨架

## 目标

创建一个可以在 GitHub Actions 上编译出 debug APK 的最小 Android 项目。

## 任务

- 创建 GitHub 仓库。
- 初始化 Kotlin Android 项目。
- 设置包名 `com.yangjim.negativeviewer`。
- 设置 minSdk 29。
- 添加 Compose 基础配置。
- 添加 Gradle Wrapper。
- 添加 `.gitignore`。
- 添加 `android-debug.yml`。
- 添加最小 README。
- 推送到 main。
- 确认 Actions 通过。

## Codex Prompt

```text
在当前仓库创建一个最小 Android Kotlin Compose 项目，包名 com.yangjim.negativeviewer，minSdk 29。添加 Gradle Wrapper、README、.gitignore 和 GitHub Actions workflow。workflow 在 main 分支 push 后运行 ./gradlew :app:assembleDebug 并上传 debug APK artifact。不要实现相机功能，先保证项目可以在 GitHub Actions 构建。
```

## 验收

- [ ] GitHub Actions 成功。
- [ ] artifact 中有 APK。
- [ ] APK 可下载。
- [ ] APK 可安装。
- [ ] App 启动显示占位界面。
- [ ] README 说明当前只是 skeleton。

## 风险与处理

| 风险 | 处理 |
|---|---|
| Gradle 配置错误 | 只修构建，不加功能 |
| SDK 缺失 | workflow 中加入 android-actions/setup-android |
| AGP/JDK 不匹配 | 固定 JDK 17，使用匹配 AGP 版本 |

---

# 阶段 1：权限与基础 UI

## 目标

实现相机权限请求和基础页面结构。

## 任务

- Manifest 添加 CAMERA 权限。
- Compose 实现 PermissionScreen。
- Compose 实现 CameraScreen 占位。
- 使用 `rememberLauncherForActivityResult` 请求权限。
- 拒绝权限时显示说明。
- 授权后进入 CameraScreen。
- 添加 `PreviewMode` 和 `CameraUiState`。
- 添加拍照按钮和模式按钮，但暂时无功能。

## Codex Prompt

```text
在现有项目中添加 CAMERA 权限和 Compose 权限请求流程。未授权时显示 PermissionScreen，包含简短说明和授权按钮；授权后显示 CameraScreen。CameraScreen 暂时只显示占位预览区、右上角 NORMAL/INVERT 切换按钮和底部拍照按钮。添加 PreviewMode、CameraUiState 和 CameraViewModel。保持项目可编译。
```

## 验收

- [ ] 首次启动请求相机权限。
- [ ] 拒绝权限不崩溃。
- [ ] 授权后显示 CameraScreen。
- [ ] 模式按钮能改变 UI 状态。
- [ ] 拍照按钮暂时可点击但不执行相机动作。
- [ ] 前后台切换不崩溃。

---

# 阶段 2：CameraX + PreviewView 普通预览

## 目标

用官方简单路径跑通普通相机预览。

## 任务

- 添加 CameraX 依赖。
- 创建 `CameraXController`。
- CameraScreen 使用 AndroidView 承载 PreviewView。
- 获取 `ProcessCameraProvider`。
- 创建 `Preview`。
- 选择 `CameraSelector.DEFAULT_BACK_CAMERA`。
- bind lifecycle。
- 只显示普通相机画面。
- 不做 OpenGL。
- 不做反色。

## Codex Prompt

```text
在 CameraScreen 中用 AndroidView 接入 CameraX PreviewView，显示后置摄像头普通预览。创建 CameraXController 封装 ProcessCameraProvider、Preview 和 lifecycle bind/unbind。不要添加拍照，不要添加 OpenGL，不要做反色。请保持项目可编译。
```

## 验收

- [ ] 授权后能看到后置摄像头预览。
- [ ] 没有持续黑屏。
- [ ] 退后台再回来能恢复。
- [ ] 屏幕旋转或竖屏锁定行为明确。
- [ ] 无重复 bind 导致崩溃。
- [ ] Actions 构建成功。

## 失败排查

- 检查 CAMERA 权限是否已授予。
- 检查 lifecycle owner 是否正确。
- 检查是否在主线程绑定 CameraX。
- 检查设备是否有后置摄像头。
- 检查 logcat 中 CameraX 错误。

---

# 阶段 3：ImageCapture 拍摄临时原图

## 目标

接入 ImageCapture，点击按钮能拍原始 JPEG 到 app cache。

## 任务

- 在 CameraX bind 中加入 ImageCapture。
- 创建 `ImageCaptureController`。
- 拍照按钮调用 `captureToTempFile()`。
- 临时文件目录：

```text
cacheDir/captures/
```

- 临时文件名：

```text
RAW_yyyyMMdd_HHmmss.jpg
```

- 拍照期间禁用按钮。
- 成功后 Toast 显示临时路径或简短成功提示。
- 失败后显示错误。

## Codex Prompt

```text
在现有 CameraX 绑定中加入 ImageCapture。点击拍照按钮后，将原始 JPEG 保存到 app cacheDir/captures/RAW_timestamp.jpg。拍照期间禁用按钮，成功和失败都更新 UI 状态并写 log。暂时不要保存到 MediaStore，也不要反色。保持项目可编译。
```

## 验收

- [ ] 点击拍照不会崩溃。
- [ ] cache 目录生成 JPEG。
- [ ] 连续拍照 5 次不崩溃。
- [ ] 拍照期间按钮禁用。
- [ ] 失败时有错误提示。
- [ ] Actions 构建成功。

---

# 阶段 4：MediaStore 保存原图

## 目标

将阶段 3 的临时 JPEG 复制保存到系统相册。

## 任务

- 创建 `MediaStoreImageSaver`。
- 使用 `ContentResolver.insert()` 创建 MediaStore Uri。
- 设置 `DISPLAY_NAME`、`MIME_TYPE`、`RELATIVE_PATH`。
- Android 10+ 使用 `IS_PENDING`。
- 将临时 JPEG bytes 写入 OutputStream。
- 写入成功后 `IS_PENDING=0`。
- 失败时删除 Uri。
- 保存后删除临时 JPEG。

## Codex Prompt

```text
添加 MediaStoreImageSaver，将 cacheDir/captures 中的 JPEG 保存到系统相册 Pictures/NegativeViewer/。使用 DISPLAY_NAME、MIME_TYPE、RELATIVE_PATH；Android 10+ 使用 IS_PENDING 写入流程。保存成功后删除临时文件，失败时尝试删除已创建的 MediaStore Uri。当前仍保存原图，不做反色。
```

## 验收

- [ ] 拍照后相册可见图片。
- [ ] 图片位于 NegativeViewer 相册或 Pictures/NegativeViewer 目录。
- [ ] 文件名不重复。
- [ ] 多次拍照不覆盖。
- [ ] 保存失败不留下明显损坏文件。
- [ ] 不申请存储权限也能在 Android 10+ 保存。
- [ ] Actions 构建成功。

---

# 阶段 5：拍照后反色保存

## 目标

INVERT 模式下保存反色照片。

## 任务

- 创建 `NegativeBitmapProcessor`。
- 读取临时 JPEG。
- 解码 Bitmap。
- 根据当前 PreviewMode：
  - NORMAL：保存原图。
  - INVERT：反色后保存。
- 处理放到后台线程。
- 处理完成后删除临时文件。
- 文件名包含模式。
- 捕获解码失败和 OOM。

## Codex Prompt

```text
添加 NegativeBitmapProcessor。拍照后读取临时 JPEG，如果当前模式是 INVERT，则解码 Bitmap 并对 RGB 执行 255-channel 反色，再通过 MediaStore 保存；如果是 NORMAL，则直接保存原图。处理必须在后台线程执行，不能阻塞主线程。文件名包含 NORMAL 或 INVERT。保持项目可编译。
```

## 验收

- [ ] NORMAL 模式保存原图。
- [ ] INVERT 模式保存反色图。
- [ ] 普通场景拍摄时反色明显。
- [ ] 对准黑白负片时保存图呈正片明暗。
- [ ] 对准彩色负片时保存图呈粗略正片。
- [ ] 连续拍 5 张不崩溃。
- [ ] 处理失败能恢复按钮状态。
- [ ] Actions 构建成功。

---

# 阶段 6：EXIF 方向最低修复

## 目标

避免保存图在相册中方向明显错误。

## 任务

- 添加 `androidx.exifinterface:exifinterface` 依赖。
- 读取临时 JPEG EXIF orientation。
- 解码后按 orientation 旋转 Bitmap。
- 反色处理应用在方向修正后的 Bitmap 上。
- 保存 JPEG 时默认使用已旋转像素，不依赖 EXIF orientation。
- 记录是否保留其他 EXIF 字段为后续任务。

## Codex Prompt

```text
添加 ExifOrientationUtils，读取临时 JPEG 的 EXIF orientation，并在解码后把 Bitmap 旋转/翻转到正确方向。INVERT 模式在方向修正后的 Bitmap 上执行反色。保存的新 JPEG 可以不保留完整 EXIF，但相册显示方向必须正确。保持项目可编译。
```

## 验收

- [ ] 竖屏拍照在相册中方向正确。
- [ ] 横屏测试如果支持，也应方向正确或明确记录限制。
- [ ] 反色结果方向和预览方向基本一致。
- [ ] Actions 构建成功。

---

# 阶段 7：OpenGL 基础渲染

## 目标

先不接相机，只验证 GLSurfaceView 和 shader 管线。

## 任务

- 创建 `CameraGlView`。
- 创建 `CameraRenderer`。
- 创建 `ShaderProgram`。
- 创建 `FullscreenQuad`。
- 绘制全屏矩形。
- fragment shader 输出固定色或渐变。
- UI 用 GLSurfaceView 替代 PreviewView 区域。
- 保留 CameraX + PreviewView 代码分支或 git commit，便于回退。

## Codex Prompt

```text
新增 CameraGlView、CameraRenderer、ShaderProgram、FullscreenQuad。使用 GLSurfaceView 渲染一个全屏矩形，fragment shader 输出固定颜色或简单渐变。暂时不要接 CameraX，也不要显示相机画面。把 CameraScreen 的预览区域替换为 CameraGlView。保持项目可编译。
```

## 验收

- [ ] App 显示 GLSurfaceView。
- [ ] 屏幕不是黑屏。
- [ ] shader 编译错误会输出 log。
- [ ] 退后台再回来不崩溃。
- [ ] Actions 构建成功。

---

# 阶段 8：CameraX Preview 输出到 OpenGL

## 目标

将 CameraX Preview 帧输出到 SurfaceTexture，再由 GLES 绘制。

## 任务

- OpenGL 创建 external OES texture。
- 用 textureId 创建 SurfaceTexture。
- `SurfaceTexture.setOnFrameAvailableListener`。
- 创建自定义 SurfaceProvider。
- SurfaceProvider 接收 SurfaceRequest。
- 用 SurfaceTexture 创建 Surface。
- 调用 `surfaceRequest.provideSurface()`。
- onFrameAvailable 时 requestRender。
- onDrawFrame 时 updateTexImage。
- shader 使用 samplerExternalOES 采样。
- 应用 transform matrix。
- 暂时先显示 NORMAL 原图。

## Codex Prompt

```text
把 CameraX Preview 输出接入 OpenGL。CameraRenderer 创建 external OES texture 和 SurfaceTexture，CameraXController 使用自定义 SurfaceProvider 将 SurfaceRequest 提供给 renderer，由 renderer 基于 SurfaceTexture 创建 Surface 并 provideSurface。onFrameAvailable 时 requestRender，onDrawFrame 中 updateTexImage 并绘制 samplerExternalOES 相机纹理。先只显示 NORMAL 原图。请重点处理生命周期和资源释放，保持项目可编译。
```

## 验收

- [ ] App 显示真实相机画面。
- [ ] 画面实时更新。
- [ ] 不再使用 PreviewView 作为主预览。
- [ ] 预览方向基本正确。
- [ ] 画面比例不严重拉伸。
- [ ] 前后台切换不崩溃。
- [ ] Actions 构建成功。

## 失败排查

| 现象 | 排查点 |
|---|---|
| 黑屏 | SurfaceTexture 是否创建；provideSurface 是否调用；updateTexImage 是否执行 |
| 只有一帧 | onFrameAvailable 是否触发；requestRender 是否调用 |
| shader 编译失败 | 检查 OES extension 和 samplerExternalOES |
| 画面倒置 | transform matrix 或纹理坐标 |
| 拉伸 | SurfaceRequest resolution、viewport、quad aspect ratio |
| 退后台崩溃 | CameraX unbind 和 GL release 顺序 |

---

# 阶段 9：实时反色 shader

## 目标

在 OpenGL fragment shader 中实现实时 NORMAL / INVERT 切换。

## 任务

- fragment shader 添加 `uInvertEnabled`。
- NORMAL 输出原图。
- INVERT 输出 `1.0 - color.rgb`。
- CameraViewModel 中的 PreviewMode 通知 renderer。
- 默认模式 INVERT。
- 模式切换时不重启 CameraX，只更新 uniform。
- 拍照时继续使用阶段 5 的模式保存逻辑。

## Codex Prompt

```text
在 OpenGL fragment shader 中添加 NORMAL/INVERT 两种模式。NORMAL 直接输出相机纹理颜色，INVERT 输出 vec4(1.0 - color.rgb, color.a)。CameraScreen 的模式按钮更新 ViewModel 中的 PreviewMode，并同步到 CameraRenderer 的 uniform。默认模式为 INVERT。切换模式时不要重启 CameraX。
```

## 验收

- [ ] 启动默认反色预览。
- [ ] NORMAL/INVERT 切换即时生效。
- [ ] 切换时无明显卡顿。
- [ ] 拍照保存结果与当前模式一致。
- [ ] 普通场景下反色效果明显。
- [ ] 对准胶片负片时可直接观看粗略正片。
- [ ] Actions 构建成功。

---

# 阶段 10：稳定性收尾

## 目标

修复生命周期、资源释放、错误提示、日志和 README。

## 任务

- 检查 CameraX bind/unbind。
- 检查 GLSurfaceView onPause/onResume。
- 检查 SurfaceTexture release。
- 检查 Surface release。
- 检查 executor shutdown。
- 检查拍照按钮状态恢复。
- 检查 MediaStore OutputStream 关闭。
- 检查临时文件清理。
- 增加关键日志 tag。
- README 增加安装和测试说明。
- PLAN.md 标记 MVP 完成项。

## Codex Prompt

```text
对项目做稳定性收尾，不增加新功能。检查 CameraX lifecycle、GLSurfaceView lifecycle、SurfaceTexture/Surface 释放、executor 关闭、ImageCapture 拍照状态恢复、MediaStore 输出流关闭和临时文件清理。增加必要日志。更新 README 的安装、测试和已知限制说明。保持项目可编译。
```

## 验收

- [ ] 连续打开/关闭 App 5 次不崩溃。
- [ ] 前后台切换 5 次不崩溃。
- [ ] 连续拍照 10 张不崩溃。
- [ ] 保存失败时按钮能恢复。
- [ ] App 卸载后用户保存到相册的图片仍存在。
- [ ] README 可指导下载 APK 和安装。
- [ ] Actions 构建成功。

## 阶段 10 执行记录

代码侧已完成：

- [x] 相机页面去除非必要状态文字，只保留错误提示。
- [x] CameraX 主路径收敛为 OpenGL SurfaceProvider + ImageCapture。
- [x] GLSurfaceView detach 时释放 SurfaceTexture/OES texture/SurfaceProvider。
- [x] MediaStore 写入使用 OutputStream `use` 自动关闭，失败时删除 Uri。
- [x] 拍照失败、处理失败、保存失败均恢复按钮状态。
- [x] 临时拍照文件在成功/失败路径清理，并清理超过 24 小时的旧 cache 文件。
- [x] README 增加安装、日志和手动测试说明。

仍需外部验证：

- [ ] GitHub Actions 构建成功。
- [ ] 真机连续打开/关闭 App 5 次不崩溃。
- [ ] 真机前后台切换 5 次不崩溃。
- [ ] 真机连续拍照 10 张不崩溃。
- [ ] 卸载 App 后相册中的已保存图片仍存在。

---

## 14. 手动测试计划

## 14.1 测试设备记录模板

```text
设备型号：
Android 版本：
CPU/GPU：
App 版本/commit：
测试日期：
测试人：
```

## 14.2 基础启动测试

- [ ] 首次安装后启动。
- [ ] 权限弹窗出现。
- [ ] 拒绝权限。
- [ ] 再次点击授权。
- [ ] 授权后进入预览。
- [ ] 杀掉进程后重开。

## 14.3 预览测试

- [ ] 默认 INVERT。
- [ ] 切换 NORMAL。
- [ ] 切换回 INVERT。
- [ ] 对准亮处。
- [ ] 对准暗处。
- [ ] 对准普通彩色物体。
- [ ] 对准黑白负片。
- [ ] 对准彩色负片。
- [ ] 前后台切换。
- [ ] 锁屏再解锁。

## 14.4 拍照测试

- [ ] NORMAL 模式拍 1 张。
- [ ] INVERT 模式拍 1 张。
- [ ] INVERT 连拍 5 张。
- [ ] 拍照过程中快速点击按钮。
- [ ] 拍照时切后台。
- [ ] 存储空间较低时尝试。
- [ ] 相册检查结果。

## 14.5 负片测试

测试材料：

- 黑白负片。
- 彩色负片。
- 手机/平板白屏背光。
- 简易灯箱。
- 普通白纸反光环境。

验收：

- [ ] 黑白负片明暗正确。
- [ ] 彩色负片能粗略辨认内容。
- [ ] 保存结果与预览模式一致。
- [ ] 色偏记录为后续彩色校正问题，不阻塞 MVP。

## 14.6 性能主观测试

- [ ] 预览延迟可接受。
- [ ] 模式切换不卡顿。
- [ ] 连续使用 3 分钟不明显过热。
- [ ] 拍照后 3 秒内完成保存，若更慢需记录设备和照片尺寸。
- [ ] App 不出现明显内存崩溃。

---

## 15. 问题记录模板

```markdown
## 问题标题

### 环境
- 设备：
- Android 版本：
- commit：
- APK artifact：
- 复现概率：

### 复现步骤
1.
2.
3.

### 预期结果

### 实际结果

### logcat 摘要

### 初步判断

### 处理状态
- [ ] 未开始
- [ ] 排查中
- [ ] 已修复
- [ ] 暂不处理
```

---

## 16. 分支和提交策略

## 16.1 分支

简单策略：

```text
main
feature/permissions-ui
feature/camerax-preview
feature/image-capture
feature/mediastore
feature/invert-processing
feature/gl-basic
feature/gl-camerax
feature/invert-shader
feature/stability
```

## 16.2 提交原则

- 一个 commit 只解决一个问题。
- 不在同一 commit 同时改 CameraX、OpenGL、MediaStore。
- 不在构建失败状态继续叠加功能。
- 每个阶段完成后打一个轻量 tag。

示例：

```bash
git tag mvp-stage-2-preview
git push origin mvp-stage-2-preview
```

## 16.3 提交信息示例

```text
Add camera permission screen
Add CameraX PreviewView prototype
Add ImageCapture temp file output
Add MediaStore image saver
Add RGB inversion processor
Add GLSurfaceView renderer skeleton
Connect CameraX preview to OES texture
Add invert shader mode
Fix camera and GL lifecycle cleanup
```

---

## 17. Codex 使用规则

## 17.1 总规则

给 Codex 的任务必须小。每次要求：

- 保持项目可编译。
- 列出修改文件。
- 不改无关模块。
- 不引入复杂架构。
- 不一次性生成大量未测试代码。
- 对不确定点加 TODO，而不是臆造。
- 如果需要新增依赖，说明原因。

## 17.2 禁止式 Prompt

不要这样要求：

```text
帮我做完整 Android 负片 App，包含 CameraX、OpenGL、拍照、保存和所有功能。
```

原因：

- 很容易生成不可编译代码。
- 很难定位错误。
- 可能混用 PreviewView、ImageAnalysis 和 OpenGL。
- 生命周期容易错。

## 17.3 推荐式 Prompt 模板

```text
你现在只做【一个目标】。请在现有项目基础上修改，不要重构无关代码。完成后列出修改文件、关键实现点和可能的风险。必须保持项目可编译。如果你不确定某个 API，请写 TODO 并说明，不要编造。
```

## 17.4 Codex 输出后人工检查清单

- [ ] 是否改了过多文件。
- [ ] 是否新增了不必要依赖。
- [ ] 是否破坏包名。
- [ ] 是否修改 workflow 导致 CI 不稳定。
- [ ] 是否把密钥、绝对路径、个人信息写入仓库。
- [ ] 是否使用 deprecated API。
- [ ] 是否阻塞主线程。
- [ ] 是否忘记关闭 stream。
- [ ] 是否忘记释放 Surface/SurfaceTexture。
- [ ] 是否和当前 milestone 目标一致。

---

## 18. 风险清单

## 18.1 OpenGL + CameraX 黑屏

风险等级：高

可能原因：

- SurfaceTexture 未绑定 GL texture。
- CameraX SurfaceRequest 没有 provideSurface。
- SurfaceTexture buffer size 未设置。
- `updateTexImage()` 没调用。
- GL 线程和相机线程竞争。
- Surface 被提前 release。

处理：

- 保留 PreviewView 阶段作为对照。
- 先让 OpenGL 画固定色。
- 再接 external OES texture。
- 每一步都加日志。
- 出现黑屏时先确认 onFrameAvailable 是否触发。

## 18.2 方向和比例错误

风险等级：中高

可能原因：

- CameraX 输出分辨率和屏幕比例不一致。
- SurfaceTexture transform matrix 未应用。
- 顶点坐标和纹理坐标方向不匹配。
- 设备旋转没处理。

处理：

- MVP 只支持竖屏。
- Activity 固定 portrait。
- 使用 transform matrix。
- 验收中记录设备差异。
- 后续再做完整旋转支持。

## 18.3 拍照反色内存溢出

风险等级：中

可能原因：

- 高分辨率 JPEG 解码为 ARGB_8888 Bitmap 占用内存大。
- 连续拍照导致 Bitmap 未及时回收。
- 后台处理队列堆积。

处理：

- 拍照期间禁用按钮。
- 处理完成后释放 Bitmap 引用。
- 捕获 OOM。
- 后续限制最大输出边长。
- 必要时 ImageCapture 设置较低 capture mode 或 resolution。

## 18.4 MediaStore 半成品文件

风险等级：中

可能原因：

- 写入失败后没有删除 Uri。
- 没设置 IS_PENDING。
- OutputStream 未关闭。

处理：

- 使用 try/finally。
- Android 10+ 使用 IS_PENDING。
- 异常时 delete Uri。
- 保存成功再显示 Toast。

## 18.5 GitHub Actions 版本变化

风险等级：中

可能原因：

- actions 版本升级。
- Ubuntu runner 预装 SDK 变化。
- AGP 和 JDK 不匹配。

处理：

- 使用 Gradle Wrapper。
- workflow 显式 setup-java。
- workflow 显式 setup-android。
- workflow 显式 setup-gradle。
- 构建失败时优先看 action 版本和 SDK license。

---

## 19. README 最小内容

README 应至少包含：

```markdown
# Negative Viewer Android

A minimal Android app for viewing film negatives through the camera.

## Features

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

Debug APK is built by GitHub Actions.

## Install

1. Open GitHub Actions.
2. Select latest successful Android Debug Build.
3. Download `negative-viewer-debug-apk`.
4. Unzip artifact.
5. Install APK on Android device.

```bash
adb install -r app-debug.apk
```

## Known limitations

- Pure RGB inversion only.
- Color negative correction is not implemented.
- MVP is portrait-first.
- Some devices may have preview rotation/aspect-ratio issues.


---

## 20. Definition of Done

MVP 完成标准：

- [ ] GitHub Actions 能稳定生成 debug APK。
- [ ] APK 可安装到至少一台 Android 10+ 真机。
- [ ] App 首次启动能请求相机权限。
- [ ] 授权后进入相机预览。
- [ ] 预览使用 OpenGL ES 渲染。
- [ ] 默认显示反色预览。
- [ ] NORMAL / INVERT 切换可用。
- [ ] 点击拍照后使用 ImageCapture 拍摄。
- [ ] INVERT 模式保存 RGB 反色 JPEG。
- [ ] NORMAL 模式保存原图 JPEG。
- [ ] 图片通过 MediaStore 出现在系统相册。
- [ ] 连续拍照 10 张不崩溃。
- [ ] 前后台切换 5 次不崩溃。
- [ ] README 写明安装方式和已知限制。
- [ ] PLAN.md 中未实现功能明确标记为 Post-MVP。

---

## 21. 官方资料核对

以下资料用于核对本计划的关键技术路线。

1. Android CameraX Preview 文档  
   https://developer.android.com/media/camera/camerax/preview

2. Android CameraX Jetpack release/docs  
   https://developer.android.com/jetpack/androidx/releases/camera

3. Android SurfaceTexture 图形架构说明  
   https://source.android.com/docs/core/graphics/arch-st

4. Android MediaStore / shared storage 文档  
   https://developer.android.com/training/data-storage/shared/media

5. Android 权限请求文档  
   https://developer.android.com/training/permissions/requesting

6. Android 权限声明文档  
   https://developer.android.com/training/permissions/declaring

7. GitHub Actions artifacts 文档  
   https://docs.github.com/en/actions/tutorials/store-and-share-data

8. GitHub Actions Gradle 文档  
   https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle

9. Gradle setup-gradle action  
   https://github.com/gradle/actions

10. android-actions/setup-android  
   https://github.com/android-actions/setup-android

---

## 22. 下一步建议

按以下顺序执行：

```text
第 1 天：阶段 0，项目初始化 + CI 生成空 APK
第 2 天：阶段 1，权限和基础 UI
第 3 天：阶段 2，PreviewView 普通预览
第 4 天：阶段 3-4，ImageCapture + MediaStore 保存原图
第 5 天：阶段 5-6，拍照反色 + 方向修复
第 6 天：阶段 7，OpenGL 基础渲染
第 7-9 天：阶段 8，CameraX 接入 OpenGL
第 10 天：阶段 9-10，反色 shader + 稳定性收尾
```

如果 OpenGL 接入卡住超过两轮迭代：

```text
保留 PreviewView + 拍照反色版本作为 fallback MVP
另开 feature/gl-camerax 分支继续攻关
不要让主分支长期不可用
```
