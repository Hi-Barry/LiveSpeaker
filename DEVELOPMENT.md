# LiveSpeaker 开发日志

> **原则**: 每次开发会话结束后更新本文档。记录决策、踩坑、心得。
> 不追求格式完美，追求"半年后回来还能看懂当时为什么这么写"。

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
> **最后更新**: 2026-05-11 | **维护者**: Hermes Agent + Hi-Barry

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
