
# PLAN2.md — Android 负片实时查看器扩展计划：片基采样按钮、彩色片基校正、黑白模式、调色、拼接保存、边缘校正、导入处理

版本：v3.1 / PLAN2  
日期：2026-07-06  
基础文件：PLAN_negative_viewer_android_v2.md  
项目暂定名：Negative Viewer Android  
建议包名：`com.yangjim.negativeviewer`  
目标环境：Ubuntu + VS Code + Codex + GitHub Actions + Android 真机  
本地原则：本地尽量少装东西；构建继续交给 GitHub Actions；本地主要做编辑、提交、APK 安装与 logcat 调试。

---

## 0. 本版新增范围

在 PLAN v2 的基础上，PLAN2 新增以下功能：

1. 黑白负片专用模式。
2. 亮度 / 对比度 / Gamma 调整。
3. RGB 通道增益调整。
4. 反转类模式支持彩色橙色片基校正。
5. 片基采样按钮：
   - 点击按钮进入采样状态。
   - 画面显示可视化十字 marker。
   - 用户点击或拖动十字到未曝光片基区域。
   - App 重新采样该区域 RGB。
   - 采样值固化保存到当前处理状态，直到下一次采样、手动重置或退出当前会话。
6. 保存策略增强：
   - NORMAL 模式只保存当前拍摄图。
   - 反转类模式提供一个选择按钮：只保存处理图，或保存“原图 + 处理图”的拼接图。
   - 竖拍：原图在左，处理图在右。
   - 横拍：原图在上，处理图在下。
   - 横拍、竖拍都按长边对齐拼接。
7. 胶片边缘识别并校正为长方形：
   - 自动识别四边形。
   - 自动失败时支持手动拖动四个顶点。
8. 反转模式支持导入已有负片照片：
   - 使用 Android Photo Picker。
   - 导入后执行同一套处理管线。
   - 可只保存处理图，也可保存原图 + 处理图拼接图。

---

## 1. 本版定位

PLAN2 不建议一次性替代原 MVP。推荐分为两个层级：

```text
MVP1：实时反色预览 + 拍照反色保存
MVP2：胶片处理增强，包括黑白模式、调色、片基采样、彩色片基校正、拼接保存、边缘校正、导入处理
```

推荐顺序：

```text
先完成 MVP1
再做 MVP2-A：模式扩展、黑白模式、亮度/对比度/Gamma、RGB gain
再做 MVP2-B：片基采样按钮 + COLOR+ 手动采样校正
再做 MVP2-C：原图 + 处理图拼接保存
再做 MVP2-D：导入已有照片处理
再做 MVP2-E：手动四点边缘校正
再做 MVP2-F：自动边缘识别
再做 MVP2-G：实时 COLOR+ 预览稳定化
```

---

## 2. 技术决策

## 2.1 相机与实时预览

CameraX 继续负责：

- `Preview`：实时相机预览。
- `ImageCapture`：高质量拍照。
- lifecycle 绑定。
- 后置主摄选择。

实时预览链路：

```text
CameraX Preview
  -> SurfaceTexture / Surface
  -> OpenGL ES external OES texture
  -> shader 反色、黑白、调色、可选片基校正
  -> GLSurfaceView 显示
```

## 2.2 导入已有照片

导入已有负片照片使用 Android Photo Picker：

```text
用户点击“导入”
  -> 系统 Photo Picker
  -> 用户选择一张图片
  -> App 获得该图片 URI
  -> 解码、方向校正、边缘校正、反色、片基校正、调色、保存
```

不申请全相册读取权限。

## 2.3 边缘识别与透视校正

推荐先做手动四点校正，再做自动边缘识别。

自动识别候选链路：

```text
输入 Bitmap
  -> 缩小到检测尺寸
  -> 灰度化
  -> 高斯模糊
  -> Canny 边缘检测
  -> findContours
  -> approxPolyDP
  -> 选择接近矩形的四边形
  -> 映射回原图坐标
  -> getPerspectiveTransform
  -> warpPerspective
  -> 输出校正后的长方形图像
```

如果不想一开始引入 OpenCV，可先实现手动四点 + Android Matrix 方案。自动识别作为后续功能。

---

## 3. 新增模式与状态模型

## 3.1 PreviewMode

```kotlin
enum class PreviewMode {
    NORMAL,
    COLOR_BASIC_INVERT,
    BW_NEGATIVE,
    COLOR_NEGATIVE_CORRECTED
}
```

UI 简写：

```text
NORMAL
COLOR
B&W
COLOR+
```

说明：

| 模式 | 作用 |
|---|---|
| NORMAL | 原图预览/保存 |
| COLOR_BASIC_INVERT | 彩色纯反色 |
| BW_NEGATIVE | 黑白负片专用灰度反色 |
| COLOR_NEGATIVE_CORRECTED | 彩色负片片基校正模式 |

---

## 3.2 SaveOutputMode

```kotlin
enum class SaveOutputMode {
    PROCESSED_ONLY,
    ORIGINAL_AND_PROCESSED_STITCH
}
```

规则：

- `NORMAL` 模式强制 `PROCESSED_ONLY`，实际保存当前拍摄原图。
- 所有反转类模式允许选择：
  - 只保存处理图。
  - 保存原图 + 处理图拼接。

反转类模式：

```text
COLOR_BASIC_INVERT
BW_NEGATIVE
COLOR_NEGATIVE_CORRECTED
```

---

## 3.3 CropCorrectionMode

```kotlin
enum class CropCorrectionMode {
    OFF,
    AUTO,
    MANUAL
}
```

默认：

```text
OFF
```

---

## 3.4 OrangeMaskSamplingState

片基采样需要独立状态，不要混在 `PreviewMode` 里。

```kotlin
enum class OrangeMaskSamplingState {
    IDLE,
    ARMING,
    SAMPLING,
    LOCKED
}
```

含义：

| 状态 | 含义 |
|---|---|
| IDLE | 没有启用采样，也没有固化采样值 |
| ARMING | 用户点击“片基采样”按钮后，等待用户点击/拖动十字 |
| SAMPLING | 正在从 marker 所在区域重新取样 |
| LOCKED | 已采样，采样值固化，直到下一次采样或重置 |

推荐交互：

```text
点击“片基采样”
  -> 状态变为 ARMING
  -> 画面中心显示十字 marker
  -> 用户点击/拖动 marker 到未曝光橙色片基
  -> 松手或点击“确定采样”
  -> 状态变为 SAMPLING
  -> 计算 marker 周围区域平均 RGB
  -> 状态变为 LOCKED
  -> COLOR+ 使用该 baseColor 持续校正
```

再次点击“片基采样”：

```text
保留上次 marker 位置
  -> 状态变为 ARMING
  -> 用户重新定位
  -> 重新采样
  -> 覆盖旧 baseColor
```

---

## 3.5 OrangeMaskSample

```kotlin
data class OrangeMaskSample(
    val red: Float,
    val green: Float,
    val blue: Float,
    val source: OrangeMaskSampleSource,
    val markerX: Float,
    val markerY: Float,
    val sampleRadiusPx: Int,
    val createdAtMillis: Long
)
```

坐标约定：

```text
markerX / markerY 使用图像坐标归一化值，范围 0.0 到 1.0
左上角 = (0.0, 0.0)
右下角 = (1.0, 1.0)
```

这样同一 marker 位置可以映射到：

- 实时预览纹理。
- 拍照后的高分辨率 Bitmap。
- 导入图片 Bitmap。
- 编辑页预览图。

---

## 3.6 OrangeMaskSampleSource

```kotlin
enum class OrangeMaskSampleSource {
    CAMERA_PREVIEW,
    CAPTURED_IMAGE,
    IMPORTED_IMAGE,
    MANUAL_RGB,
    AUTO_BORDER
}
```

---

## 3.7 ProcessingParams

```kotlin
data class ProcessingParams(
    val previewMode: PreviewMode,
    val saveOutputMode: SaveOutputMode,
    val cropCorrectionMode: CropCorrectionMode,

    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val gamma: Float = 1.0f,

    val redGain: Float = 1.0f,
    val greenGain: Float = 1.0f,
    val blueGain: Float = 1.0f,

    val orangeMaskSamplingState: OrangeMaskSamplingState = OrangeMaskSamplingState.IDLE,
    val orangeMaskSample: OrangeMaskSample? = null,

    val manualCorners: List<PointF>? = null
)
```

---

## 4. 片基采样按钮规格

## 4.1 按钮显示规则

按钮名称：

```text
片基采样
```

英文内部名：

```text
Sample Base
```

只在以下模式显示：

```text
COLOR_NEGATIVE_CORRECTED
```

也可以在 COLOR_BASIC_INVERT 中显示灰置提示：

```text
切换到 COLOR+ 后可采样片基
```

不在以下模式显示：

```text
NORMAL
BW_NEGATIVE
```

原因：

- NORMAL 不需要片基校正。
- B&W 模式通常不依赖橙色片基 RGB 校正。

---

## 4.2 按钮状态

| 当前状态 | 按钮文字 | 行为 |
|---|---|---|
| IDLE | 片基采样 | 进入 ARMING |
| ARMING | 确定采样 | 对 marker 位置采样 |
| SAMPLING | 采样中… | 禁用 |
| LOCKED | 重新采样 | 进入 ARMING，保留旧 marker 位置 |

另加一个小按钮：

```text
重置片基
```

只在 `LOCKED` 状态显示。点击后：

```text
orangeMaskSample = null
orangeMaskSamplingState = IDLE
COLOR+ 回到无片基或默认片基状态
```

---

## 4.3 十字 Marker 视觉规格

marker 样式：

```text
- 十字线
- 中心小圆点
- 半透明采样圆
- 可拖动
- 在边界处自动限制不越界
```

建议视觉：

```text
        │
        │
   ─────●─────
        │
        │
```

采样半径：

```text
实时预览默认：15px 到 25px
高分辨率图片：短边的 1% 到 3%
最小半径：8px
最大半径：80px
```

实际采样区域显示为半透明圆或方框：

```text
marker 中心 = 采样中心
半透明圆/方框 = 实际采样范围
```

---

## 4.4 Marker 交互

支持两种操作：

### 操作 A：点击定位

```text
用户点击画面某一点
  -> marker 移动到该点
  -> 不立即采样，等待用户点击“确定采样”
```

### 操作 B：拖动定位

```text
用户拖动 marker
  -> marker 跟随手指
  -> 松手后停留
  -> 用户点击“确定采样”
```

不建议“点击后立即采样”，因为用户容易点错。MVP2.1 可以允许点击后 marker 移动，用户再确认采样。

---

## 4.5 Marker 坐标映射

UI 显示坐标和图像坐标必须分离。

```text
屏幕坐标
  -> 预览区域坐标
  -> 考虑 letterbox / crop / scale
  -> 归一化图像坐标 markerX / markerY
  -> 映射到实际 Bitmap 像素坐标
```

如果预览使用 centerCrop，要特别处理被裁掉的区域。否则采样点可能与用户看到的位置不一致。

MVP2.1 简化策略：

```text
编辑页处理图片时，使用 fitCenter 显示图片
marker 坐标映射简单可靠
先在编辑页实现片基采样
实时预览中的片基采样作为后续
```

推荐路线：

```text
先做导入/拍照后编辑页采样
再做实时预览采样
```

---

## 4.6 采样区域像素筛选

采样时不要无脑取所有像素平均。建议过滤：

```text
排除接近纯黑的像素：亮度 < 0.03
排除接近纯白或过曝的像素：亮度 > 0.98
排除 alpha < 1 的像素
```

初版可用简单平均：

```text
baseR = avg(r)
baseG = avg(g)
baseB = avg(b)
```

更稳版本用中位数：

```text
baseR = median(r values)
baseG = median(g values)
baseB = median(b values)
```

推荐：

```text
MVP2.1 用平均值
MVP2.2 改为中位数或 trimmed mean
```

---

## 4.7 采样固化规则

采样完成后：

```text
orangeMaskSample 固化到当前 ProcessingParams
COLOR+ 持续使用该 sample
用户调整亮度/对比度/Gamma/RGB gain 不会清除 sample
用户切换到 NORMAL/B&W/COLOR 再切回 COLOR+，sample 仍保留
用户点击“重新采样”后覆盖 sample
用户点击“重置片基”后清除 sample
用户退出当前编辑会话时可丢弃 sample
```

是否跨 App 重启保存：

```text
MVP2.1：不跨 App 重启保存，只在当前会话固化
Post-MVP：用 DataStore 保存最近一次 sample 和 marker 位置
```

原因：

- 不同胶卷、不同光源、不同拍摄环境片基不同。
- 默认跨会话复用可能造成错误颜色。
- 但可以后续提供“保存为默认片基”的高级选项。

---

## 4.8 采样失败处理

采样失败场景：

```text
marker 不在有效图像区域
采样区域全部过暗
采样区域全部过曝
图片尚未解码完成
OpenGL 预览当前没有可读帧
```

UI 提示：

```text
无法采样：请选择未曝光的橙色片基区域
```

失败后状态：

```text
保留 ARMING
marker 不消失
允许用户重新定位
```

---

## 5. 彩色片基校正算法

## 5.1 初版算法

输入 RGB 使用 0.0 到 1.0 float。

```text
base = orangeMaskSample.rgb

scale = max(base.r, base.g, base.b)

n.r = input.r / max(base.r, epsilon) * scale
n.g = input.g / max(base.g, epsilon) * scale
n.b = input.b / max(base.b, epsilon) * scale

n = clamp(n, 0.0, 1.0)

out = 1.0 - n
```

然后进入：

```text
RGB gain
contrast
brightness
gamma
clamp
```

epsilon：

```text
0.01
```

---

## 5.2 处理顺序

统一规定：

```text
Decode
  -> EXIF orientation correction
  -> Optional perspective correction
  -> If COLOR+:
       orange mask normalization
       invert
     Else if COLOR:
       pure invert
     Else if B&W:
       grayscale invert
     Else NORMAL:
       no negative transform
  -> RGB gain
  -> contrast
  -> brightness
  -> gamma
  -> optional stitch
  -> MediaStore save
```

注意：

- `orangeMaskSample` 必须从“方向校正后、透视校正后”的图像坐标采样。
- 拼接图中的原图也应使用同样的方向校正和透视校正，但不做反色/调色。
- 这样原图与处理图几何范围一致，便于对比。

---

## 5.3 Shader 公式

实时预览 COLOR+ 可使用：

```glsl
vec3 applyOrangeMaskCorrection(vec3 c) {
    vec3 base = max(uOrangeMaskBase, vec3(0.01));
    float scale = max(max(base.r, base.g), base.b);

    vec3 normalizedColor;
    normalizedColor.r = c.r / base.r * scale;
    normalizedColor.g = c.g / base.g * scale;
    normalizedColor.b = c.b / base.b * scale;

    normalizedColor = clamp(normalizedColor, 0.0, 1.0);
    return vec3(1.0) - normalizedColor;
}
```

如果没有 sample：

```text
COLOR+ 可以退化为 COLOR_BASIC_INVERT
或者提示“请先采样片基”
```

推荐 UX：

```text
进入 COLOR+ 时若没有 sample：
  -> 显示轻提示：请点击“片基采样”并选取未曝光橙色区域
  -> 预览暂时使用 COLOR_BASIC_INVERT
```

---

## 6. 亮度 / 对比度 / Gamma / RGB gain

## 6.1 参数范围

```text
brightness: -0.5 到 +0.5
contrast: 0.5 到 2.0
gamma: 0.3 到 3.0
redGain: 0.2 到 3.0
greenGain: 0.2 到 3.0
blueGain: 0.2 到 3.0
```

默认值：

```text
brightness = 0.0
contrast = 1.0
gamma = 1.0
redGain = 1.0
greenGain = 1.0
blueGain = 1.0
```

## 6.2 Tone 公式

```text
c.r *= redGain
c.g *= greenGain
c.b *= blueGain

c = (c - 0.5) * contrast + 0.5
c = c + brightness
c = clamp(c, 0.0, 1.0)

c = pow(c, 1.0 / gamma)
c = clamp(c, 0.0, 1.0)
```

## 6.3 UI

推荐两个面板：

```text
基础：
- Brightness
- Contrast
- Gamma
- Reset Tone

高级：
- Red Gain
- Green Gain
- Blue Gain
- Reset RGB
```

---

## 7. 黑白负片专用模式

## 7.1 处理公式

```text
gray = 0.299R + 0.587G + 0.114B
out = 255 - gray
R' = out
G' = out
B' = out
```

Shader：

```glsl
float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
float inverted = 1.0 - gray;
gl_FragColor = vec4(vec3(inverted), color.a);
```

## 7.2 验收标准

- 黑白负片预览不是彩色反色，而是灰度正片。
- 保存结果为灰度 JPEG。
- 导入照片也可使用 B&W 模式。
- tone 参数对 B&W 生效。
- RGB gain 默认不显示或标为高级。

---

## 8. 原图 + 处理图拼接保存

## 8.1 规则

NORMAL 模式：

```text
只保存当前拍摄原图
不显示拼接保存按钮
```

反转类模式：

```text
显示保存模式按钮：
- 只保存处理图
- 保存原图 + 处理图拼接
```

## 8.2 拼接方向

判断方向：

```text
如果 height >= width：竖图
如果 width > height：横图
```

竖图：

```text
原图在左，处理图在右
```

横图：

```text
原图在上，处理图在下
```

尺寸对齐：

```text
竖图按高度对齐
横图按宽度对齐
```

## 8.3 StitchComposer

```kotlin
object StitchComposer {
    fun compose(
        original: Bitmap,
        processed: Bitmap
    ): Bitmap
}
```

---

## 9. 边缘识别与长方形校正

## 9.1 手动四点优先

先实现手动四点：

```text
显示图片
叠加四个可拖动顶点
用户拖动到胶片四角
点击应用
输出长方形图像
```

自动识别作为后续：

```text
Canny -> contours -> approxPolyDP -> quadrilateral -> warpPerspective
```

## 9.2 验收标准

- 手动四点可拖动。
- 应用后输出长方形。
- 原图与处理图都基于同一校正结果。
- 拼接保存使用校正后的图。
- 自动失败不强制裁切，回退手动。

---

## 10. 导入已有负片照片

## 10.1 入口

相机页面增加：

```text
导入
```

点击：

```text
Android Photo Picker
  -> 选择单张图片
  -> ProcessingEditorScreen
```

## 10.2 导入处理

```text
Photo Picker URI
  -> Decode bitmap
  -> EXIF orientation correction
  -> Optional perspective correction
  -> Mode transform
  -> Optional orange base sample
  -> Tone / RGB gain
  -> Optional stitch
  -> MediaStore save
```

## 10.3 片基采样

导入图中的 COLOR+ 也使用同一个采样按钮和 marker：

```text
点击片基采样
  -> 十字 marker 出现
  -> 用户拖到未曝光片基区域
  -> 确定采样
  -> sample 固化
  -> COLOR+ 应用直到下一次采样
```

---

## 11. UI 信息架构

## 11.1 CameraScreen

```text
- OpenGL 实时预览
- 模式切换：NORMAL / COLOR / B&W / COLOR+
- COLOR+ 模式下显示：片基采样 / 重置片基
- 片基采样 ARMING 时显示十字 marker
- 保存模式按钮：仅反转类模式显示
- 拍照按钮
- 导入按钮
```

## 11.2 ProcessingEditorScreen

用于拍照后和导入后的精细处理：

```text
- 图片预览
- 模式选择
- COLOR+ 片基采样按钮
- 十字 marker
- 亮度 / 对比度 / Gamma
- RGB gain
- 边缘校正
- 保存处理图
- 保存拼接图
```

## 11.3 推荐优先级

```text
先在 ProcessingEditorScreen 实现片基采样
再在 CameraScreen 实现实时预览片基采样
```

原因：

- 编辑页坐标映射更稳定。
- 采样基于静态图片更可控。
- 实时预览受曝光、白平衡和画面变化影响更大。

---

## 12. 分阶段开发计划

# 阶段 11：模式和参数模型扩展

## 目标

扩展数据结构和 UI 状态，不改核心算法。

## 任务

- 扩展 `PreviewMode`。
- 新增 `SaveOutputMode`。
- 新增 `CropCorrectionMode`。
- 新增 `OrangeMaskSamplingState`。
- 新增 `OrangeMaskSample`。
- 新增 `ProcessingParams`。
- UI 增加模式切换。
- COLOR+ 模式显示“片基采样”按钮。

## Codex Prompt

```text
在现有项目基础上扩展模式和参数模型，不实现新的图像算法。将 PreviewMode 扩展为 NORMAL、COLOR_BASIC_INVERT、BW_NEGATIVE、COLOR_NEGATIVE_CORRECTED；新增 SaveOutputMode、CropCorrectionMode、OrangeMaskSamplingState、OrangeMaskSample 和 ProcessingParams。CameraScreen 在 COLOR_NEGATIVE_CORRECTED 模式下显示“片基采样”按钮。保持项目可编译，现有 NORMAL/INVERT 功能不能坏。
```

## 验收

- [ ] 项目可编译。
- [ ] UI 能切换四种模式。
- [ ] COLOR+ 模式显示“片基采样”按钮。
- [ ] NORMAL/B&W 不显示片基采样按钮。
- [ ] 原有拍照保存仍可用。

---

# 阶段 12：黑白负片模式

## 目标

实现 B&W 模式预览和保存。

## Codex Prompt

```text
实现 BW_NEGATIVE 模式。OpenGL shader 中用亮度公式 dot(rgb, vec3(0.299, 0.587, 0.114)) 转灰度后反色；Bitmap 保存管线中实现同样逻辑。保存文件名包含 BW。保持 NORMAL 和 COLOR_BASIC_INVERT 现有行为不变。
```

## 验收

- [ ] B&W 预览为灰度反色。
- [ ] B&W 保存图为灰度正片。
- [ ] NORMAL 不受影响。
- [ ] COLOR_BASIC_INVERT 不受影响。

---

# 阶段 13：亮度 / 对比度 / Gamma

## Codex Prompt

```text
新增 brightness、contrast、gamma 参数和 UI sliders。反转类模式在 shader 预览和 Bitmap 保存中都应用同样 tone 公式：c = (c - 0.5) * contrast + 0.5；c += brightness；c = pow(clamp(c), 1/gamma)。gamma 必须避免为 0。添加 Reset。NORMAL 模式暂不应用 tone。
```

## 验收

- [ ] 三个滑杆实时影响预览。
- [ ] 拍照保存结果应用同样参数。
- [ ] Reset 可恢复默认值。
- [ ] gamma 为安全范围，不能崩溃。

---

# 阶段 14：RGB 增益

## Codex Prompt

```text
新增 RGB gain 参数：redGain、greenGain、blueGain，默认 1.0，范围 0.2 到 3.0。添加 UI sliders 和 Reset RGB。反转类模式的 shader 和 Bitmap 保存管线都在 tone 之前应用 RGB gain。NORMAL 模式暂不应用 RGB gain。
```

## 验收

- [ ] RGB 滑杆实时影响 COLOR/COLOR+ 预览。
- [ ] 保存图应用同样 RGB gain。
- [ ] Reset RGB 恢复 1.0。
- [ ] 不影响 NORMAL 模式。

---

# 阶段 15：片基采样按钮与十字 Marker

## 目标

实现 COLOR+ 模式下的片基采样交互，但先只在编辑页或静态图片上完成，实时预览可后续接入。

## 任务

- COLOR+ 模式显示“片基采样”按钮。
- 点击后进入 `ARMING`。
- 画面显示十字 marker。
- marker 可点击定位和拖动。
- 用户点击“确定采样”后，对 marker 周围区域取样。
- 采样结果写入 `OrangeMaskSample`。
- 状态变为 `LOCKED`。
- 采样值固化，直到重新采样或重置。
- 显示采样 RGB 值或色块预览。
- 提供“重置片基”。

## Codex Prompt

```text
实现 COLOR_NEGATIVE_CORRECTED 模式下的片基采样交互。点击“片基采样”后进入 ARMING 状态，在图片/预览上显示可拖动十字 marker；用户点击或拖动 marker 定位到未曝光橙色片基区域，再点击“确定采样”。程序采样 marker 周围区域的平均 RGB，生成 OrangeMaskSample，并将状态设为 LOCKED。采样值必须固化，直到用户点击“重新采样”或“重置片基”。先优先在 ProcessingEditorScreen 的静态图片上实现，保持项目可编译。
```

## 验收

- [ ] COLOR+ 显示片基采样按钮。
- [ ] 点击后出现十字 marker。
- [ ] marker 可拖动。
- [ ] 点击确定后能生成 sample。
- [ ] sample 有 RGB 值或色块反馈。
- [ ] sample 固化，调整 tone/RGB gain 不会清除。
- [ ] 重新采样会覆盖旧 sample。
- [ ] 重置片基会清除 sample。
- [ ] 采样失败有提示，不崩溃。

---

# 阶段 16：COLOR+ 手动片基校正

## 目标

把阶段 15 的 sample 接入图像处理管线。

## Codex Prompt

```text
实现 COLOR_NEGATIVE_CORRECTED 的手动片基校正。若 OrangeMaskSample 存在，使用 sample RGB 作为 baseColor，对输入执行通道归一化：scale=max(base.r,base.g,base.b)，normalized.rgb = input.rgb / max(base.rgb, epsilon) * scale，clamp 后执行 1.0 - normalized，再应用 RGB gain、contrast、brightness、gamma。若 sample 不存在，COLOR+ 暂时退化为 COLOR_BASIC_INVERT，并提示用户先采样片基。保持 COLOR_BASIC_INVERT 和 BW_NEGATIVE 不变。
```

## 验收

- [ ] COLOR+ 无 sample 时退化为纯反色或提示采样。
- [ ] COLOR+ 有 sample 后颜色明显改变。
- [ ] 对蓝/青偏色有明显改善。
- [ ] sample 固化期间，每次保存都使用同一 sample。
- [ ] 重新采样后保存结果改变。
- [ ] 导入图和拍照图都可使用。

---

# 阶段 17：拼接保存

## Codex Prompt

```text
实现 SaveOutputMode.PROCESSED_ONLY 和 ORIGINAL_AND_PROCESSED_STITCH。NORMAL 模式强制只保存当前拍摄原图，不显示拼接选项。反转类模式可选择只保存处理图或保存原图+处理图拼接。竖图按高度对齐左右拼接，原图在左、处理图在右；横图按宽度对齐上下拼接，原图在上、处理图在下。新增 StitchComposer，并确保导出文件名包含 STITCH。
```

## 验收

- [ ] NORMAL 只保存原图。
- [ ] 反转类模式可选择拼接保存。
- [ ] 竖拍原图在左。
- [ ] 横拍原图在上。
- [ ] 拼接图保存到相册。
- [ ] 图像无错误拉伸。

---

# 阶段 18：导入已有照片

## Codex Prompt

```text
新增导入已有照片功能。使用 Android Photo Picker 的 PickVisualMedia 选择单张图片，不申请 READ_MEDIA_IMAGES。选择图片后进入 ProcessingEditorScreen，使用与拍照相同的 ProcessingPipeline。导入图可以按当前模式处理，并支持片基采样、只保存处理图或保存原图+处理图拼接。取消选择时不崩溃。
```

## 验收

- [ ] 点击导入能打开系统 Photo Picker。
- [ ] 取消选择不崩溃。
- [ ] 选择图片后进入编辑页。
- [ ] 导入图可 COLOR/B&W/COLOR+ 处理。
- [ ] 导入图可片基采样。
- [ ] 可保存处理图。
- [ ] 可保存拼接图。
- [ ] 不申请读取整个相册权限。

---

# 阶段 19：手动四点边缘校正

## Codex Prompt

```text
新增手动四点边缘校正功能。ProcessingEditorScreen 中添加“边缘校正”入口，进入 CropCorrectionScreen。界面显示图片并叠加四个可拖动顶点，用户点击应用后将四点区域校正为长方形 Bitmap，并返回处理管线。自动识别暂不实现。校正后的 original 用于后续反色、片基采样、调色和拼接。
```

## 验收

- [ ] 四个顶点可拖动。
- [ ] 应用后得到长方形图。
- [ ] 取消后返回原图。
- [ ] 校正结果可继续片基采样和反色。
- [ ] 拼接图使用校正后的原图和处理图。

---

# 阶段 20：自动边缘识别

## Codex Prompt

```text
新增自动边缘识别。使用 OpenCV 或当前项目选定的图像处理库，在缩小图上执行灰度、模糊、Canny、findContours、approxPolyDP，寻找最合适四边形。识别结果进入手动四点界面供用户确认和微调，不要自动强制裁切。识别失败时提示用户手动拖动四点。保持项目可编译。
```

## 验收

- [ ] 对高对比边缘胶片能自动识别四边形。
- [ ] 识别结果可手动调整。
- [ ] 识别失败不崩溃。
- [ ] 不会错误裁切后直接覆盖原图。
- [ ] 应用后输出长方形。

---

# 阶段 21：实时 COLOR+ 预览采样

## 目标

在静态编辑页片基采样稳定后，将同样交互扩展到相机实时预览。

## 任务

- CameraScreen 中 COLOR+ 显示片基采样按钮。
- 点击后在 OpenGL 预览上叠加 marker。
- 用户定位 marker。
- 从当前预览帧或下一次拍照帧采样。
- sample 固化到 shader uniform。
- 不每帧自动估计，避免颜色闪烁。

## Codex Prompt

```text
将片基采样扩展到 CameraScreen 实时预览。COLOR+ 模式下点击“片基采样”显示可拖动十字 marker，用户确认后从当前相机预览帧对应区域采样 base RGB，生成 OrangeMaskSample，并将 sample 作为 shader uniform 固化使用，直到重新采样或重置。不要每帧自动估计片基，避免颜色闪烁。
```

## 验收

- [ ] COLOR+ 实时预览可采样片基。
- [ ] 采样后颜色稳定。
- [ ] 重新采样可覆盖旧 sample。
- [ ] 重置片基可回到纯反色。
- [ ] 切换模式不重启 CameraX。
- [ ] 保存结果与预览使用同一 sample。

---

## 13. 新增总验收清单

## 13.1 片基采样

- [ ] COLOR+ 模式显示片基采样按钮。
- [ ] 点击后出现十字 marker。
- [ ] marker 可拖动和点击定位。
- [ ] 采样区域可视化。
- [ ] 确认后重新采样。
- [ ] 采样值固化到下一次采样。
- [ ] 重新采样覆盖旧值。
- [ ] 重置片基清除采样。
- [ ] sample 对预览/保存/导入处理一致生效。
- [ ] 采样失败不崩溃。

## 13.2 彩色片基校正

- [ ] 未采样时 COLOR+ 有明确提示或退化为纯反色。
- [ ] 已采样时 COLOR+ 色偏明显改善。
- [ ] RGB gain 可继续微调。
- [ ] tone 可继续微调。
- [ ] 拍照和导入处理都支持 COLOR+。

## 13.3 黑白模式

- [ ] 实时预览为灰度反色。
- [ ] 拍照保存为灰度正片。
- [ ] 导入处理为灰度正片。
- [ ] tone 参数对 B&W 生效。

## 13.4 拼接保存

- [ ] NORMAL 只保存当前拍摄图。
- [ ] 反转类模式显示拼接选择。
- [ ] 竖图原图左、处理图右。
- [ ] 横图原图上、处理图下。
- [ ] 拍照和导入都支持拼接。
- [ ] 拼接保存到相册。

## 13.5 边缘校正

- [ ] 手动四点可拖动。
- [ ] 自动识别成功后可调整。
- [ ] 自动失败可手动。
- [ ] 输出为长方形。
- [ ] 校正结果参与后续片基采样、反色和拼接。

---

## 14. 新增风险

## 14.1 片基采样点选错误

风险：

- 用户点到画面内容而不是未曝光片基。
- 采样区域过暗或过曝。
- 采样点在裁切区域外。
- 预览裁切导致坐标映射错误。

处理：

- 显示十字 marker 和采样范围。
- 不点击即采样，而是需要确认。
- 采样后显示 RGB 色块。
- 提供重置和重新采样。
- 先在静态编辑页实现，再扩展实时预览。

## 14.2 COLOR+ 颜色仍不准确

风险：

- 胶片种类和冲洗差异大。
- 手机白平衡和背光光谱影响大。
- 简单 base normalization 不是完整胶片色彩科学。

处理：

- 明确 COLOR+ 是粗略片基校正。
- 保留 RGB gain、brightness、contrast、gamma。
- 后续考虑 RAW、对数密度、自动白平衡锁定。

## 14.3 大图导入和拼接 OOM

处理：

- 导入时限制最大处理边长，例如 3000 或 4096 px。
- 拼接前计算输出 Bitmap 内存。
- 低内存时提示降低输出尺寸。
- 后续做分块处理。

---

## 15. MVP2 Definition of Done

MVP2 完成标准：

- [ ] MVP1 全部完成。
- [ ] 支持 `NORMAL / COLOR / B&W / COLOR+`。
- [ ] B&W 模式实时预览和保存均可用。
- [ ] 亮度/对比度/Gamma 对反转类模式生效。
- [ ] RGB 增益对彩色反转类模式生效。
- [ ] COLOR+ 支持片基采样按钮。
- [ ] 片基采样有可视化十字 marker。
- [ ] 采样值固化到下一次采样。
- [ ] 重新采样和重置片基可用。
- [ ] NORMAL 模式只保存当前拍摄图。
- [ ] 反转类模式可保存处理图或拼接图。
- [ ] 竖图拼接原图在左，横图拼接原图在上。
- [ ] 支持导入已有图片并处理。
- [ ] 导入使用 Photo Picker，不申请全相册权限。
- [ ] 支持手动四点边缘校正。
- [ ] 自动边缘识别可用或明确标为实验功能。
- [ ] 所有输出通过 MediaStore 保存到相册。
- [ ] README 更新新功能、限制和测试设备。

---

## 16. 官方与技术资料核对

1. CameraX architecture  
   https://developer.android.com/media/camera/camerax/architecture

2. CameraX Preview  
   https://developer.android.com/media/camera/camerax/preview

3. Android SurfaceTexture  
   https://source.android.com/docs/core/graphics/arch-st

4. Android Photo Picker  
   https://developer.android.com/training/data-storage/shared/photo-picker

5. Android MediaStore shared media  
   https://developer.android.com/training/data-storage/shared/media

6. OpenCV geometric image transformations / warpPerspective  
   https://docs.opencv.org/4.x/da/d54/group__imgproc__transform.html

7. OpenCV project  
   https://github.com/opencv/opencv

8. Color negative orange mask reference  
   https://www.scantips.com/colornegs.html

9. Film base white balance workflow reference  
   https://125px.com/articles/photography/digital/color-film-scan-processing/

---

## 17. 建议执行顺序

如果当前还没有完成 MVP1：

```text
先完成 PLAN v2 的阶段 0-10
不要立即做 PLAN2 的阶段 11-21
```

如果 MVP1 已完成：

```text
第 1 步：阶段 11，扩展模式和参数
第 2 步：阶段 12，B&W
第 3 步：阶段 13-14，Tone + RGB gain
第 4 步：阶段 15，片基采样按钮和十字 marker
第 5 步：阶段 16，COLOR+ 手动片基校正
第 6 步：阶段 17，拼接保存
第 7 步：阶段 18，导入处理
第 8 步：阶段 19，手动四点边缘校正
第 9 步：阶段 20，自动边缘识别
第 10 步：阶段 21，实时 COLOR+ 采样预览
```

最稳的交付策略：

```text
MVP2.1 = B&W + Tone + RGB gain
MVP2.2 = 片基采样按钮 + COLOR+ 手动片基校正
MVP2.3 = 拼接保存 + 导入处理
MVP2.4 = 手动四点校正
MVP2.5 = 自动边缘识别
MVP2.6 = 实时 COLOR+ 采样预览
```
