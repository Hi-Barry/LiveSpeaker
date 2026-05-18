# LiveSpeaker 开发日志

> **原则**: 每次开发会话结束后更新本文档。记录决策、踩坑、心得。
> 不追求格式完美，追求"半年后回来还能看懂当时为什么这么写"。

## 📋 版本历程速览

| 版本 | 日期 | 关键变更 |
|------|------|---------|
| **v0.4.1** | 05-17 | 🛡️ 停止录音保留当前片段 |
| **v0.4.0** | 05-17 | 📤 长按菜单：导出/分享/删除 + 转录复制 |
| **v0.3.9** | 05-16 | 🌓 状态栏图标跟随系统主题 |
| **v0.3.5** | 05-16 | 🌓 系统亮暗主题自动切换 |
| **v0.3.1** | 05-15 | 🔄 STT 自动重试 + 设置集成到 BottomSheet |
| **v0.3.0** | 05-15 | ☁️ 云端语音转文字 (STT) 集成 |
| **v0.2.6** | 05-11 | ⚙️ 可配置录音切片时间 |
| **v0.2.5** | 05-11 | 🎙️ 前台 Service 支持后台录音 |
| **v0.2.4** | 05-11 | 🛡️ 切割链异常保护 + 时间戳修正 |
| **v0.2.3** | 05-11 | 🐛 修复录音时长显示 2:02 错误 |
| **v0.2.1** | 05-11 | ▶️ 播放进度条 + 音频源优化 |
| **v0.2.0** | 05-11 | 🔄 v2 全面重构：纯录音切割 |
| **v0.1.x** | 05-09~11 | 🏗️ v1 sherpa-onnx 离线语音识别（已废弃） |

---

## 📅 2026-05-16 — 配色修复：主题统一 + 系统主题跟随

### 事件

修复 App 配色问题：系统弹窗（权限等）与 App 主题不一致、导航栏图标颜色错误、只支持暗色主题。

### 🏗 变更

| 文件 | 变更 | 理由 |
|------|------|------|
| `themes.xml` | 父主题 `Light.NoActionBar` → `NoActionBar` | 消除系统弹窗（权限对话框等）亮色/暗色不一致 |
| `Theme.kt` | 新增 `LightBackground`、`LightSurface`、`LightTextPrimary` 等亮色常量 | 为亮色主题提供配色方案 |
| `MainActivity.kt` | ① 状态栏/导航栏图标颜色跟随系统主题 ② Compose 主题跟随 `isSystemInDarkTheme()` | 系统亮色模式时 App 自动切换亮色主题 |

### 踩了什么坑

- **`55fb450` 回退的根因**：`1913a38` 同时做了程序化设置 `statusBarColor`/`navigationBarColor`（与 themes.xml 的 `transparent` 可能冲突）和 flags 设置。回退时一起删了。本次**只操作 flags**，不碰 barColor，避免崩溃。

### 架构决策

- **系统栏图标**：仅用传统的 `systemUiVisibility` 方式（`and` 位掩码清除 `LIGHT_*` 标志）。**避免 WindowInsetsController API 30+ 路径**，在 CI 模拟器 (API 34) 上该路径会导致 App 启动后进程消失。
- **Compose 主题**：`setContent` 中用 `isSystemInDarkTheme()` 选择 `darkColorScheme` / `lightColorScheme`。
- **共用强调色**：`AccentPrimary`/`AccentSecondary`/`AccentTertiary`/`ErrorRed` 亮暗通用，不随主题变化。

### ⚠️ 诊断过程（二分法排查）

| 版本 | themes.xml | 状态栏代码 | Compose 主题 | 模拟器测试 |
|------|-----------|-----------|-------------|-----------|
| v0.3.3 | `Light.NoActionBar` | 简版 (仅 status bar) | 硬编码暗色 | ✅ 通过 |
| v0.3.5 | `NoActionBar` | 完整版 (双 API) | 系统跟随 | ❌ 崩溃 |
| v0.3.6 | `Light.NoActionBar` | 完整版 (双 API) | 系统跟随 | ❌ 崩溃 |
| v0.3.7 | `Light.NoActionBar` | **无** | 系统跟随 | ✅ 通过 |
| v0.3.8 | `Light.NoActionBar` | 简版 + nav bar | 系统跟随 | ✅ 通过 |

**根因确认**：`WindowInsetsController.setSystemBarsAppearance()` (API 30+) 导致模拟器崩溃。`systemUiVisibility` 位掩码方式完全安全。`isSystemInDarkTheme()` 和 `lightColorScheme` 无问题。

---

## 📅 2026-05-17 — v0.4.1 停止录音时保留当前片段

### 事件

用户反馈手动停止录音后，录制时间不足 1 分钟（自动切片时长）的当前片段会丢失，不出现在列表中。

### 🏗 根因

`AudioRecorder.stop()` 虽然调用了 `MediaRecorder.stop()` 将音频写入磁盘，但**没有把 `currentFile` 加入内部 `_segments` 列表**。

### 修复

提取 `finalizeSegment(file)` 方法，释放 `MediaRecorder` → 读取文件时长 → 加入 `_segments` 列表。现在无论是手动 `stop()` 还是自动 `onSegmentComplete()`，都走同一个 `finalizeSegment()` 路径。

### 改动量

- `AudioRecorder.kt`（重构 ~20 行）— 提取 `finalizeSegment()`，`stop()` 和 `onSegmentComplete()` 统一调用

---

## 📅 2026-05-17 — v0.4.0 录音长按菜单 + 转录文本复制

### 事件

为录音片段添加长按/更多按钮弹出菜单（导出、分享、删除），为转录文本添加复制按钮。

### 🏗 架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 导出 | `MediaStore.Downloads` API | Android 10+ Scoped Storage 兼容，无需权限 |
| 分享 | `FileProvider` + `content://` URI | Android 分享标准方式 |
| 删除 | `AlertDialog` 确认 + 停止播放 | 防止误操作，先停播再删 |
| 删除联动 | 同步删除 `_transcription.json` sidecar | 保持数据一致 |
| UI | `combinedClickable` + `DropdownMenu` | 长按 + ⋮ 按钮双入口 |

### 改动量

| 文件 | 变更 | 理由 |
|------|------|------|
| `AudioRecorder.kt` | +`removeSegment()` | ViewModel 通过此方法操作内部列表 |
| `RecordingViewModel.kt` | +`deleteSegment()`, +`exportToDownloads()` | 业务逻辑封装 |
| `RecordScreen.kt` | 重构 `SegmentItem` + `DropdownMenu` + `AlertDialog` | 菜单 UI + 确认弹窗 |
| `TranscriptionScreen.kt` | +复制按钮 → `ClipboardManager` | 转录文本复制 |
| `AppNavigation.kt` | +3 个回调参数 | 导出/分享/删除 → MainActivity |
| `MainActivity.kt` | 实现 3 个回调 | FileProvider 分享、MediaStore 导出 |
| `res/xml/file_paths.xml` | 新建 | FileProvider 路径配置 |
| `AndroidManifest.xml` | +FileProvider 声明 | 分享功能必需 |

---

## 📅 2026-05-16 — v0.3.9 状态栏图标跟随系统主题

### 事件

亮色主题下状态栏图标（时间、电量等）仍是白色，导致白底白字不可见。

### 修复

在 `onCreate` 中用 `systemUiVisibility` 位掩码设置 `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR`（亮色主题），`SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR` 同理。暗色主题清除这些标志。避免使用 `WindowInsetsController` API 30+ 路径（模拟器上有崩溃风险）。

---

## 📅 2026-05-15 — v3 STT 语音转文字集成

### 事件

在 v2 纯录音架构基础上，添加云端语音转文字功能，支持 SiliconFlow / OpenAI 兼容 API。

### 🏗 架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| STT 方式 | 云端 API (SiliconFlow) | 不增加 APK 体积，利用已有云端算力 |
| HTTP 客户端 | OkHttp | Android 标准，无额外重量级依赖 |
| 存储格式 | JSON sidecar（与音频同名） | 极简，无需 Room 数据库 |
| 设置存储 | DataStore Preferences | 现代异步 API，替代 SharedPreferences |
| 导航 | Navigation Compose + 底部 Tab | 2 个 Tab（录音/转写）+ 设置页 |
| 序列化 | kotlinx.serialization | 轻量，编译期类型安全 |
| 触发时机 | 录音片段完成后自动触发 | 无需用户手动操作 |

### 做了什么

1. **API 验证**: SiliconFlow SenseVoiceSmall 直接支持 M4A 格式，无需转码
2. **新增 4 个依赖**: OkHttp 4.12, DataStore Preferences 1.1.1, Navigation Compose 2.8.5, kotlinx-serialization-json 1.7.3
3. **新建 `stt/` 包**: Transcription 数据模型 + SttConfig 配置管理 + SttEngine API 客户端
4. **新建设置页**: SettingsScreen — 启用开关、provider、base URL、API Key（密码框）、模型
5. **新建转写页**: TranscriptionScreen — 时间戳 + 文本 + 播放按钮，支持空状态
6. **重构导航**: MainScreen → RecordScreen（录音 Tab），AppNavigation 管理底部导航
7. **ViewModel 扩展**: startSttWatcher 监听新片段 → transcribeSegment → 保存 JSON sidecar → 更新列表
8. **新版 MainActivity**: 收集所有 StateFlow 传入 AppNavigation
9. **添加 INTERNET 权限**: AndroidManifest
10. **更新测试**: 4 个测试含导航切换
11. **版本号**: v0.2.3 → v0.3.0

### 踩了什么坑

- **用户提供的 JavaScript 示例有误**: API 使用 `multipart/form-data` 而非 `JSON.stringify()`
- **M4A MIME 类型**: OkHttp 上传时需指定 `audio/mp4`（非 `audio/m4a`）
- **MediaRecorder flush 延迟**: stop() 后需 delay(500ms) 等文件完全写入再上传

### 学到了什么

- SiliconFlow SenseVoiceSmall 对 M4A/AAC 格式支持良好
- DataStore Preferences 是 SharedPreferences 的现代替代
- Navigation Compose bottom bar 需要 `saveState/restoreState` 保持 Tab 状态
- 转录结果作为 JSON sidecar 存储，查询简单且不增加 Room 迁移负担

### ⚠️ 已知不足

1. **无后台转录**: App 退出后新片段不会转录（需后续 WorkManager）
2. **API Key 明文存储**: DataStore 未加密（可后续上 EncryptedSharedPreferences）
3. **无转录进度指示**: 状态栏未显示"转录中..."

---

## 📅 2026-05-15 — v0.3.1 STT 自动重试 + 设置集成

### 事件

用户配置了正确的 STT 设置，但转录页面仍显示"STT 未启用"错误——原因是之前未配置时产生的错误条目被标记为"已处理"，即使后续设置正确也不会自动重试。

### 🏗 修复

1. **自动重试**: 监听 STT 配置变更（`sttConfig.settings`），当从未启用/无Key变为已启用+有Key时，自动清除 `processedSegments` 中的错误条目，删除对应 sidecar 文件，触发重新转录
2. **逐条重试**: 转录页面每条错误条目右侧添加 🔄 重试按钮，支持手动逐条重试
3. **设置集成**: 将 STT 设置（API Key、Model 等）从独立的全屏 `SettingsScreen` 集成到已有的 `SettingsSheet` (BottomSheet) 中

### 改动量

- `RecordingViewModel.kt` — `clearErrorProcessedSegments()` + `retrySegment()` + 配置监听协程
- `TranscriptionScreen.kt` — 错误条目 + 重试按钮
- `SettingsSheet.kt` — STT 设置项（Provider/URL/Key/Model）
- `AppNavigation.kt` — `onRetry` 回调传递

---

## 📅 2026-05-11 (傍晚) — v0.2.6 设置界面 + 可配置切片时间

### 做了什么

添加设置界面，用户可选择录音切片时间（1/2/5/10 分钟）。

### 🏗 架构决策

| 决策 | 实现 | 理由 |
|------|------|------|
| 存储 | SharedPreferences (SettingsManager 单例) | 零额外依赖，够用 |
| UI | Material3 ModalBottomSheet | 原生支持，无需 Navigation 库 |
| 生效时机 | 下次 startNewSegment() 读取新值 | 不中断当前录音 |
| 选项 | 1/2/5/10 分钟 RadioButton | 覆盖常用场景 |

### 改动量

- `SettingsManager.kt` (新建 ~30 行) — SharedPreferences 封装
- `SettingsSheet.kt` (新建 ~150 行) — BottomSheet + RadioButton
- `AudioRecorder.kt` (改 3 行) — 删除硬编码 SEGMENT_DURATION_MS，改用 SettingsManager
- `LiveSpeakerApp.kt` (+1 行) — init SettingsManager
- `MainScreen.kt` (+15 行) — TopAppBar 加 ⚙️ 按钮
- `MainActivity.kt` (重写 ~15 行) — showSettings 状态 + SettingsSheet 条件渲染

---

## 📅 2026-05-11 (下午) — v0.2.5 后台录音支持

### 做了什么

添加前台 Service，解决切后台后录音被系统杀死的问题。

### 🏗 架构决策

| 决策 | 实现 | 理由 |
|------|------|------|
| Service 职责 | 仅 startForeground()，不做录音 | 录音逻辑不动，零风险 |
| 时机 | startRecording() 启动 Service，stopRecording() 停止 | 暂停回放时 Service 保持 |
| 通知 | "LiveSpeaker / 录音中…" + 麦克风图标 | IMPORTANCE_LOW 不打扰 |

### 改动量

- `RecordingService.kt` (新建 ~43 行)
- `AndroidManifest.xml` (+4 权限 + Service 声明)
- `LiveSpeakerApp.kt` (+通知渠道创建)
- `RecordingViewModel.kt` (+10 行 启动/停止 Service)
- `res/drawable/ic_mic.xml` (新建 通知图标)

AudioRecorder.kt 一行未动。

### 坑

- `PendingIntent.getActivity()` 需用 `Class.forName()` 引用 Activity，避免循环依赖（Service 在 audio 包，Activity 在 ui 包）

---

## 📅 2026-05-11 — v0.2.4 切割链异常保护 + 时间戳修正

### 做了什么

在 v0.2.3 时长修复基础上，加固录音切割链路的健壮性。

### 修复

- **切割异常保护**: `onSegmentComplete()` 回调中增加 try-catch，防止单次切割失败导致后续录音中断
- **时间戳修正**: 文件命名中的时间戳改用录音开始时间而非文件创建时间，确保文件名与录音时段一致

---

## 📅 2026-05-11 — v0.2.2 版本号对齐 + 历史列表保持

### 做了什么

- **版本号对齐**: `build.gradle.kts` 中 `versionName` 与 Git tag 同步
- **历史列表保持**: 重新开始录音时不再清空已有的历史片段列表，仅追加新片段

---

## 📅 2026-05-11 — v0.2.1 播放进度条 + 音频源优化

### 做了什么

- **播放进度条**: 播放中的片段底部显示 2dp 高的 `LinearProgressIndicator`，播放结束后自动隐藏
- **音频源优化**: 录音音频源从默认改为 `VOICE_RECOGNITION`，提升嘈杂环境下的录音质量

### 改动量

- `MainScreen.kt` — 播放进度条 UI
- `AudioRecorder.kt` — 音频源配置

---

## 📅 2026-05-11 — v2 全面重构：纯录音切割

### 事件

推翻 v1 的 sherpa-onnx 实时转写架构，重建为纯录音 + 1 分钟自动切割 + 回放。

### 🏗 架构决策

| 决策 | v1（旧） | v2（新） | 理由 |
|------|---------|---------|------|
| 录音引擎 | AudioRecord + RingBuffer | MediaRecorder | 直接输出文件，setMaxDuration 原生切割 |
| AI 模型 | sherpa-onnx 228MB | **移除** | 暂不需要转写 |
| 状态管理 | Service + EventBus | ViewModel + StateFlow | 不需要后台运行 |
| 权限 | 5 个（录音/通知/悬浮窗/网络/WakeLock） | 1 个（RECORD_AUDIO） | 极简 |
| 播放 | 无 | ExoPlayer (Media3) | 原生音频播放 |
| APK 体积 | ~300MB | ~5MB | 移除所有 ONNX 模型 |

### 做了什么

1. **删除 16 个旧文件**：TranscriptionService、SherpaEngine、ModelManager、VadProcessor、RingBuffer、ClusterEngine、VectorUtils、SpeakerProfile、SpeakerRepository、SpeakerDao、AppDatabase、BubbleService、SettingsScreen、SpeakerScreen、TranscriptionScreen、RecordingState
2. **重写 AudioRecorder**：MediaRecorder 替代 AudioRecord，setMaxDuration(60000) 自动切割，文件命名 `recording_20260511_132045_1.m4a`
3. **新建 RecordingViewModel**：状态机管理录音/暂停/回放，ExoPlayer 播放，播放时自动暂停录音，播放结束自动恢复
4. **新建 MainScreen**：状态栏 + 片段列表（右侧播放按钮）+ FAB。播放中高亮当前片段
5. **精简依赖**：移除 sherpa-onnx、Room、KSP 插件、JitPack 仓库，添加 Media3 ExoPlayer + lifecycle-viewmodel-compose
6. **版本号**：versionCode 1→2, versionName 1.0.0→0.2.0

### 踩了什么坑

- **本地无 JDK**：Hermes Agent 环境无 Java，无法本地编译。CI 环境有完整 Android SDK，依赖 GitHub Actions 编译验证

### 学到了什么

- MediaRecorder 的 pause/resume 在 API 26+ 原生支持，minSdk=26 刚好能用
- 60 秒切割通过 setMaxDuration + setOnInfoListener(MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) 实现
- 播放时暂停录音的策略：ViewModel 维护 `wasRecordingBeforePlayback` 标志，播放结束后自动恢复
- Media3 ExoPlayer 比原生 MediaPlayer 更可靠，支持更现代的 API

### ⚠️ 已知不足

1. **切割间隙**：MediaRecorder stop→start 有 ~200-500ms 音频丢失
2. **无后台录音**：Activity 退出后录音停止（后续可考虑前台服务）
3. **无删除功能**：片段只能播放，不能删除
4. **播放进度不可见**：只显示播放/暂停状态，无进度条

### 🔮 下一步计划

- [ ] 删除功能
- [ ] 播放进度条
- [ ] 波形显示
- [ ] 后台录音（前台服务）
- [ ] 文件分享/导出
- [ ] 重新集成 sherpa-onnx ASR（作为后续功能逐步加入）

---

## 📅 2026-05-09 — 项目初始化 + CI 上线

### 事件

从零搭建 Android 离线语音识别 App，含说话人识别，目标场景为嘈杂机房。

### 🏗 架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| ASR 框架 | **sherpa-onnx** | Vosk 太老(2021)，sherpa 2025 年还在迭代，支持 SenseVoice |
| ASR 模型 | **SenseVoice int8 (228MB)** | 阿里达摩院出品，中英文混合 SOTA，内置标点恢复 |
| 说话人识别 | **3D-Speaker ERes2Net** | sherpa-onnx 内置支持，中文优化 |
| 降噪 | **GTCRN (500KB)** | 4.8万参数,专为低资源设备设计,机房风扇噪音场景关键 |
| 音频源 | **麦克风 VOICE_RECOGNITION** | 不干扰其他 App（Android AudioFlinger 混音） |
| 数据库 | **Room + TypeConverter** | 存储说话人声纹质心向量 |
| UI | **Jetpack Compose + Material3** | 现代声明式 UI，深色主题适配机房 |

### ✨ 亮点

1. **三层噪音防御**: 硬件(降噪耳机) → GTCRN 降噪 → 噪声鲁棒 ASR 模型。540KB 模型解决机房痛点。
2. **在线说话人聚类**: 余弦相似度 + 移动平均质心更新。无需预注册，自动发现新说话人。
3. **完整的 CI/CD**: GitHub Actions 自动构建 arm64-v8a + armeabi-v7a，打 tag 自动 Release APK。

### 🐛 踩坑记录

#### 1. sherpa-onnx API 与文档不符

**现象**: CI 编译报 `Unresolved reference: SpeechEnhancer`, `FeatureExtractorConfig` 等。

**根因**: sherpa-onnx Kotlin API 的类名和构造函数与 Python/C API 不同。JitPack AAR 中的具体类目需要通过源码确认。

**解决**:
- `OfflineRecognizer(AssetManager, config)` — 需要 AssetManager 作为第一个参数
- `OfflineSenseVoiceModelConfig.useInverseTextNormalization` — 是 **Boolean** 而非 Int
- `FeatureExtractorConfig` 在 OfflineRecognizer 中不需要单独配置
- `SpeakerEmbeddingExtractor.compute()` 参数签名不是 `(FloatArray, Int)` — **待确认**

**教训**: 使用 JitPack 依赖前，先查看 GitHub 源码中的 Kotlin API 文件确认类名和签名。

#### 2. Room 不支持 FloatArray

**现象**: `Cannot figure out how to save this field into database`

**解决**: 添加 `@TypeConverters` 将 FloatArray ↔ ByteArray 互转。Room 原生支持 ByteArray 存储。

#### 3. JitPack 版本号格式

**现象**: `Could not find com.github.k2-fsa:sherpa-onnx:1.12.11`

**解决**: JitPack 需要带 `v` 前缀的版本号: `com.github.k2-fsa:sherpa-onnx:v1.13.1`

#### 4. GitHub Actions Release 权限

**现象**: `Resource not accessible by integration` 创建 Release 失败

**解决**: 在 workflow 顶层添加 `permissions: contents: write`

#### 5. 本机 git push 需要代理

**现象**: `git push` 直接超时

**解决**: `git -c http.proxy=socks5://10.88.88.3:10808 push`

### ⚠️ 已知不足

1. **说话人嵌入暂不可用**: `SpeakerEmbeddingExtractor.compute()` API 签名待确认，目前 `extractEmbedding()` 返回 null
2. **模型需手动下载**: ~270MB 模型文件不在仓库内，首次启动需网络下载
3. **VAD 为简化版**: 当前用能量阈值 VAD，正式版应切换为 sherpa-onnx 内置的 Silero VAD
4. **悬浮球为 View 实现**: 非 Compose，后续应统一
5. **Firebase Test Lab 待配置**: CI job 已就绪，需用户手动添加 Service Account

### 🔮 下一步计划

- [ ] 适配 `SpeakerEmbeddingExtractor.compute()` 正确签名
- [ ] 切换为 Silero VAD（sherpa-onnx 内置）
- [ ] 模型下载进度 UI
- [ ] 真机机房噪音测试
- [ ] 配置 Firebase Test Lab（免费 15 次/天）

---

## 📅 2026-05-09 — CI 测试集成

### 做了什么
- 集成 Android 模拟器到 GitHub Actions CI
- APK 静态分析 (aapt2 dump badging + permissions)
- 模拟器冒烟测试 (安装 → 启动 → 进程检查 → 截图 → logcat)
- Firebase Test Lab CI job (条件启用，待配置)

### 踩了什么坑
- **aapt2 badging 输出格式变化**：sdkVersion 行格式为 `sdkVersion:'26'`，正则需适配 `grep -oP "sdkVersion:'\K[0-9]+"`
- **shell 多行 if 语句被拆分**：GitHub Actions 的 script 在 `|` 多行模式下，`if...then...fi` 跨行会被拆成多个 `sh -c` 调用导致语法错误。解决：单行 `&& ||` 替代
- **模拟器启动成功但窗口名不可见**：`dumpsys window | grep mCurrentFocus` 返回空是正常的（可能是权限对话框或 app 无窗口焦点），用 `grep -q "com.livespeaker.app"` 检查整个 dumpsys 输出更可靠

### 学到了什么
- Android 模拟器在 CI 中启动约需 40 秒（Boot completed in 38969 ms）
- 模拟器无麦克风，语音识别功能无法在 CI 中测试
- Firebase Test Lab Spark 计划提供免费 15 次/天真机测试，但不需信用卡
- CI 测试的最佳策略：静态分析 + 模拟器冒烟 + Firebase 真机（互补）

---

## 模板: 开发会话记录

```markdown
## 📅 YYYY-MM-DD — 简短标题

### 做了什么
- 要点 1
- 要点 2

### 踩了什么坑
- 问题 → 根因 → 解决方法

### 学到了什么
- 
```

---

## 📅 2026-05-10 — bugfix: 开始录音闪退修复

### 做了什么
- **异步模型加载**: 将 SherpaEngine.initAsr() 从 onCreate 主线程同步加载改为后台协程异步加载，避免阻塞主线程→ANR→5秒 startForeground 超时崩溃
- **全局 try-catch**: TranscriptionService.onCreate() 包裹 try-catch，崩溃时写日志到 filesDir/crash_oncreate.txt
- **SherpaEngine 健壮性**: initAsr 返回 Boolean，增加文件存在性/大小预检（防下载不完整导致 native crash），recognize() release stream 防内存泄漏
- **AudioRecorder 安全化**: start() 不抛 IllegalStateException，改返回 Boolean + 失败时自动释放资源
- **startForeground 防卫**: Android 13+ 检查 POST_NOTIFICATIONS 权限；try-catch 包裹防 SecurityException
- **EventBus 线程安全**: @Synchronized + toList() 快照遍历防 ConcurrentModificationException
- **MainActivity 悬浮窗逻辑修复**: 悬浮窗权限缺失不再阻塞录音启动，改为 Toast 提示；注册模型错误广播接收器
- **崩溃诊断**: 模型加载失败广播到 MainActivity，Toast 显示具体原因（文件缺失/损坏/超时）

### 踩了什么坑
- **5秒 startForeground 超时是大概率根因**: startForegroundService() → onCreate 主线程同步加载 228MB ONNX 模型 → onStartCommand 来不及调 startForeground() → ForegroundServiceDidNotStartInTimeException 闪退
- **模型文件可能下载不完整**: 不检查文件大小的 initAsr → JNI SIGSEGV → 进程瞬间死掉，连 Java try-catch 都抓不住
- **权限检查遗漏**: Android 13+ startForeground 需要 POST_NOTIFICATIONS → 未授权则 SecurityException
- **EventBus 竞态**: mutableListOf 无保护 → IO 线程 emit 遍历同时主线程 remove/clear → crash

### 学到了什么
- Android 前台服务的「5秒法则」是硬约束，startForeground 必须在服务启动后 5 秒内调用
- 大型模型加载（100MB+）绝不能在主线程做，必须在后台线程
- sherpa-onnx 的 OfflineRecognizer 构造函数是同步阻塞的 JNI 调用
- Thread.sleep/wait 在主线程（onStartCommand）阻塞仍会触发 ANR，应全部移入协程
- 崩溃信息写入文件（非 logcat）对真机调试至关重要

---

## 📅 2026-05-10 — CI 仪表化测试集成

### 做了什么
- **RecordingCrashTest**: 编写 Compose UI 仪表化测试，真正点击「开始录音」按钮后验证进程不崩溃
  - `clickStartRecording_doesNotCrash` — 单击 FAB → 等 5 秒 → 断言 UI 仍存在
  - `clickStartRecordingTwice_doesNotCrash` — 连续切换录音状态 → 验证无竞态崩溃
- **CI workflow 升级**: 从纯冒烟测试（安装→启动→截图）升级为功能性回归测试
  - Build 阶段新增 `assembleDebugAndroidTest` + 上传测试 APK
  - Emulator 阶段：安装主 APK → 安装测试 APK → 授权 → `am instrument` 运行测试
- **build.gradle.kts**: 添加 `ui-test-junit4` + `test:rules` 依赖

### 踩了什么坑
- **CI 冒烟测试的盲区**: `adb install → am start → ps check` 只能验证「安装+启动」，完全不会触发录音按钮点击 → 闪退永远抓不到
- **Compose UI 测试需要额外依赖**: 仅有 `espresso-core` 不够，必须添加 `ui-test-junit4` + Compose BOM

### 学到了什么
- 仪表化测试是 CI 中唯一能真实模拟用户操作的机制
- `createAndroidComposeRule` + `onNodeWithContentDescription("开始录音").performClick()` 能精确命中 Compose FAB
- `GrantPermissionRule` 在模拟器上自动跳过权限弹窗
- 测试 APK 和主 APK 必须分开构建（`assembleDebug` vs `assembleDebugAndroidTest`）
- CI 模拟器无麦克风 ≠ 不能测录音启动链路 — 模型缺失时 Service 优雅停止才是我们要验证的「不崩溃」行为

---
> **最后更新**: 2026-05-18 | **维护者**: Hermes Agent + Hi-Barry

## 📅 2026-05-11 — CI emulator script 多行语法错误修复

### 做了什么
- **修复 emulator script 多行 if/then/fi**: android-emulator-runner 将每一行拆成独立 `sh -c` 调用 → 多行 if 块被截断 → `Syntax error: end of file unexpected (expecting "fi")` → exit code 2
- **修复位置**:
  1. ui.xml 存在性检查: `[ -f /tmp/ui.xml ] && ... || ...`
  2. ps 进程存活性检查: `adb shell "ps -A ... | grep -q" && ... || { ...; exit 1; }`

### 踩了什么坑
- **同一错误出现两次**: v0.1.3 时修过一次多行条件 (commit `4b24002`)，v0.1.4 新增 uiautomator dump 调试代码时又写了多行 if，复现**完全相同**的 CI 崩溃
- **教训**: android-emulator-runner 的 script 不能再出现任何多行条件语句，必须全部 `&& ||` 单行

### 学到了什么
- CI 脚本变更后需要检查「有没有多行 if/for/while」——应有自动化检查规则
- 注释掉的代码块保留多行 if 也危险（YAML 缩进敏感）

### 第二轮修复：变量跨行丢失

**现象**: `Screen: x` → `Tapping FAB at: x` → `input tap` 参数为空 → `IllegalArgumentException: Argument expected after "tap"` → exit 255

**根因**: 同上——每行独立 `sh -c`。`SIZE`/`W`/`H`/`FX`/`FY` 五个变量跨了 8 行，每行都是新 shell，前一行设的变量后一行拿不到。

**修复**: 合并整个 SIZE→W→H→FX→FY→tap 为用 `;` 分隔的单行（commit `1972dbd`）

**最终验证**: CI 全绿 → Screen: 320x640, FAB: 281x563, ✅ PASS

---
> **最后更新**: 2026-05-18 | **维护者**: Hermes Agent + Hi-Barry

## 📅 2026-05-11 — 模型自动下载 + 国内镜像

### 做了什么
- **ModelManager 镜像 fallback**: `archiveUrl` 改为 `archivePath` + `mirrorUrls()`，下载时依次尝试：主源 (GitHub) → ghproxy.net → mirror.ghproxy.com
- **TranscriptionService 自动下载**: 模型不存在时 → 调用 `modelManager.downloadModel()` → 下载完再加载；全程异步不阻塞主线程
- **超时提升**: `MODEL_LOAD_TIMEOUT_MS` 30s → 120s（228MB 下载 + 加载需要较长时间）
- **Settings 模型状态**: 每行右侧显示 ✓（已下载）或 ✗（缺失），数据来源 `ModelManager.isModelReady()`

### 踩了什么坑
- **ModelManager 实现了但从未被调用**: `downloadModel()` 和 `areAllModelsReady()` 写了完整的下载/验证逻辑，但 `TranscriptionService` 只是检查文件存在 → 没有 → 报错停止。缺失的链路：模型不存在时应该触发下载
- **GitHub Releases 国内不可达**: 添加 ghproxy 镜像作为 fallback，下载时每位 mirror 单独 try-catch，all fail 才报错

### 学到了什么
- `remember {}` 在 Compose 中只在首次组合时计算。模型状态变化后需要 `mutableStateOf` 才能触发重组
- 下载 + 加载链路一次性打通比逐个排查体验好得多：用户点"开始录音" → 看到通知栏进度 → 下载完成自动开始，无需手动操作

---
> **最后更新**: 2026-05-18 | **维护者**: Hermes Agent + Hi-Barry

## 📅 2026-05-11 — UX 修复：悬浮窗权限去重 + 下载进度可见

### 做了什么
- **悬浮窗权限去重**: `SharedPreferences("permissions")` 记录 `overlay_prompted`，首次未授权时跳设置页，之后不再骚扰
- **通知栏下载进度**: `downloadProgress` volatile 变量，`waitForModel()` 轮询时实时更新通知：`正在下载 ASR 模型... 45%` → `正在加载模型...` → 录音开始

### 踩了什么坑
- **SettingsScreen 编译错误**: `modelManager.MODELS` 通过实例访问 companion object 导致 `Unresolved reference`，改为 `ModelManager.MODELS` 静态访问 + 显式 `List<Boolean>` 类型
- **下载进度 vs 通知时序**: 下载发生在 `onCreate()`，但 `startForeground()` 在 `onStartCommand()` → 不能在下载回调中直接 `updateNotification()`。解决：`waitForModel()` 轮询 `downloadProgress`

---
> **最后更新**: 2026-05-18 | **维护者**: Hermes Agent + Hi-Barry

## 📅 2026-05-11 — 状态机重构：PREPARING + EventBus 驱动 UI

### 做了什么
- **RecordingState 新增 PREPARING**: IDLE → PREPARING → RECORDING，准备期间 FAB 显示 ⌛ 不可点击、状态栏显示"准备中..."
- **EventBus 状态通道**: `emitState(RecordingState)` + `listenState`，`TranscriptionService` 每次状态变更广播，`MainScreen` 订阅后同步 FAB 图标/颜色/文字
- **通知流修复**: `startRecording()` 初始 `PREPARING` → 下载进度 `waitForModel()` → 模型就绪后切 `RECORDING` + 通知"录音中"
- **下载超时提升**: connectTimeout 15s→30s, readTimeout 15s→120s（228MB 慢速网络）

### 踩了什么坑
- **FAB 自己管理 isRecording 变量**: 点 FAB 立即设 true，但 Service 里模型还在下载 → 超时报错 → 状态对不上。改为 Service 广播真实状态驱动 UI
- **通知栏与 UI 状态割裂**: `startForeground("录音中")` 但实际还在下载模型。改初始 `PREPARING` + "准备中..."

### 学到了什么
- Android 前台服务的通知状态应该诚实地反映当前阶段，不能为了好看提前亮"录音中"
