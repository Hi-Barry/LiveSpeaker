# LiveSpeaker v2 — 纯录音切割重构计划

> **For Hermes:** 按此计划逐步执行，每步完成验证后提交。

**Goal:** 推翻旧架构，重建为：录音 → 1分钟自动切割 → 文件列表 + 播放回放

**Architecture:** 单 Activity + Compose UI + ViewModel + MediaRecorder 录音 + MediaPlayer 播放。无需 sherpa-onnx、前台服务、Room、EventBus。

**Tech Stack:** Kotlin, Jetpack Compose, Material3, AndroidX Lifecycle ViewModel, MediaRecorder, MediaPlayer

---

## 📊 架构对比

| 维度 | v1（旧） | v2（新） |
|------|---------|---------|
| 录音方式 | AudioRecord + RingBuffer（PCM直读） | MediaRecorder（直接输出文件） |
| 切割 | 无（实时流） | setMaxDuration(60s) 自动切割 |
| AI 模型 | sherpa-onnx 228MB | **零依赖** |
| 状态管理 | Service + EventBus（跨进程） | ViewModel + StateFlow |
| 数据库 | Room（说话人） | 无（暂时，文件系统管理） |
| 权限 | 录音+通知+悬浮窗+网络 | 仅 `RECORD_AUDIO` |
| 前台服务 | 是 | **否**（纯 Activity 内操作） |
| 线程模型 | 4+ 线程 pipeline | 主线程 + IO 协程 |
| APK 体积 | ~300MB（含模型） | ~5MB |
| UI 复杂度 | 4 页面 + 悬浮球 | 1 页面 |

---

## 🗑️ 删除清单（14 文件）

| # | 文件路径 | 原因 |
|---|---------|------|
| 1 | `service/TranscriptionService.kt` | 不再需要前台服务 |
| 2 | `service/TranscriptionNotification.kt` | 不再需要通知栏 |
| 3 | `service/RecordingState.kt` | 替换为 ViewModel 状态 |
| 4 | `pipeline/ModelManager.kt` | 不需要模型管理 |
| 5 | `pipeline/SherpaEngine.kt` | 不需要 ASR 引擎 |
| 6 | `pipeline/VadProcessor.kt` | 不需要 VAD |
| 7 | `audio/RingBuffer.kt` | 不需要环形缓冲 |
| 8 | `diarization/ClusterEngine.kt` | 不需要说话人聚类 |
| 9 | `diarization/VectorUtils.kt` | 不需要向量计算 |
| 10 | `data/SpeakerProfile.kt` | 不需要说话人 |
| 11 | `data/SpeakerRepository.kt` | 同上 |
| 12 | `data/SpeakerDao.kt` | 同上 |
| 13 | `data/AppDatabase.kt` | 同上 |
| 14 | `ui/screen/SettingsScreen.kt` | 不需要设置页 |
| 15 | `ui/screen/SpeakerScreen.kt` | 不需要说话人页 |
| 16 | `ui/bubble/BubbleService.kt` | 不需要悬浮球 |

## ✏️ 修改清单（4 文件 + 3 新增）

| # | 文件路径 | 操作 | 内容 |
|---|---------|------|------|
| 1 | `app/build.gradle.kts` | 修改 | 删除 sherpa-onnx、Room 依赖；添加 Media3 ExoPlayer |
| 2 | `AndroidManifest.xml` | 修改 | 删除 Service 声明和多余权限 |
| 3 | `LiveSpeakerApp.kt` | 修改 | 简化为空壳 Application |
| 4 | `ui/MainActivity.kt` | 修改 | 简化为权限请求 + 加载 Compose |
| 5 | `audio/AudioRecorder.kt` | **重写** | 用 MediaRecorder 重写 |
| 6 | `ui/screen/MainScreen.kt` | **新增** | 主界面：录音控制 + 文件列表 |
| 7 | `viewmodel/RecordingViewModel.kt` | **新增** | 状态管理 |

---

## 📋 详细任务列表

### 第一阶段：清理旧代码

---

### Task 1: 删除旧 Service/pipeline/数据库文件 🟢

**Risk:** 🟢 Low（纯删除）

**Objective:** 移除所有不需要的旧文件，确保项目仍可编译（后续 task 会修复引用）

**Files:**
- Delete: `app/src/main/java/com/livespeaker/app/service/TranscriptionService.kt`
- Delete: `app/src/main/java/com/livespeaker/app/service/TranscriptionNotification.kt`
- Delete: `app/src/main/java/com/livespeaker/app/service/RecordingState.kt`
- Delete: `app/src/main/java/com/livespeaker/app/pipeline/ModelManager.kt`
- Delete: `app/src/main/java/com/livespeaker/app/pipeline/SherpaEngine.kt`
- Delete: `app/src/main/java/com/livespeaker/app/pipeline/VadProcessor.kt`
- Delete: `app/src/main/java/com/livespeaker/app/audio/RingBuffer.kt`
- Delete: `app/src/main/java/com/livespeaker/app/diarization/ClusterEngine.kt`
- Delete: `app/src/main/java/com/livespeaker/app/diarization/VectorUtils.kt`
- Delete: `app/src/main/java/com/livespeaker/app/data/SpeakerProfile.kt`
- Delete: `app/src/main/java/com/livespeaker/app/data/SpeakerRepository.kt`
- Delete: `app/src/main/java/com/livespeaker/app/data/SpeakerDao.kt`
- Delete: `app/src/main/java/com/livespeaker/app/data/AppDatabase.kt`
- Delete: `app/src/main/java/com/livespeaker/app/ui/screen/SettingsScreen.kt`
- Delete: `app/src/main/java/com/livespeaker/app/ui/screen/SpeakerScreen.kt`
- Delete: `app/src/main/java/com/livespeaker/app/ui/bubble/BubbleService.kt`

**Step 1:** 用 `rm` 删除上述所有文件

**Step 2:** 验证目录结构只剩必要文件

**Step 3:** 提交

```bash
git add -A
git commit -m "chore: 删除旧版转写/说话人识别/悬浮球代码"
```

---

### Task 2: 精简 build.gradle.kts 🟢

**Risk:** 🟢 Low

**Objective:** 移除 sherpa-onnx、Room 依赖，添加 Media3 ExoPlayer 用于播放

**Files:**
- Modify: `app/build.gradle.kts`

**具体修改：**

```diff
-    // Room
-    implementation("androidx.room:room-runtime:2.6.1")
-    implementation("androidx.room:room-ktx:2.6.1")
-    ksp("androidx.room:room-compiler:2.6.1")

-    // sherpa-onnx
-    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.1")
```

添加：
```kotlin
    // Media3 ExoPlayer (音频回放)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    
    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
```

同时删除 KSP 插件、JitPack 仓库、`aaptOptions.noCompress`

**Step 1:** 编辑 `build.gradle.kts`

**Step 2:** 编辑 `settings.gradle.kts` 删除 jitpack maven

**Step 3:** 提交

```bash
git add app/build.gradle.kts settings.gradle.kts
git commit -m "chore: 精简依赖 - 移除 sherpa-onnx/Room，添加 Media3/ViewModel"
```

---

### Task 3: 精简 AndroidManifest.xml 🟢

**Risk:** 🟢 Low

**Objective:** 移除不再需要的权限和 Service 声明

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**具体操作：**

```diff
-    <!-- 代码中请求 -->
-    <!-- 前台服务权限 -->
-    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
-    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
-    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
-    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
-    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
-    <uses-permission android:name="android.permission.INTERNET" />
-    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
-    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
-    <uses-permission android:name="android.permission.WAKE_LOCK" />
```

删除所有 `<service>` 声明。

**保留的权限：**
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

**Step 1:** 编辑 `AndroidManifest.xml`

**Step 2:** 验证只有 `RECORD_AUDIO` 权限和 `MainActivity`

**Step 3:** 提交

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "chore: 精简 Manifest - 只保留录音权限和 MainActivity"
```

---

### Task 4: 简化 LiveSpeakerApp.kt 🟢

**Risk:** 🟢 Low

**Objective:** 移除 ModelManager、数据库初始化、通知渠道

**Files:**
- Modify: `app/src/main/java/com/livespeaker/app/LiveSpeakerApp.kt`

**新代码：**

```kotlin
package com.livespeaker.app

import android.app.Application

class LiveSpeakerApp : Application() {
    companion object {
        lateinit var instance: LiveSpeakerApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
```

**Step 1:** 重写文件

**Step 2:** 提交

```bash
git add app/src/main/java/com/livespeaker/app/LiveSpeakerApp.kt
git commit -m "refactor: 简化 Application 为空壳"
```

---

### 第二阶段：构建核心功能

---

### Task 5: 重写 AudioRecorder（MediaRecorder 版）🟡

**Risk:** 🟡 Medium（核心录音逻辑，需正确处理 MediaRecorder 生命周期）

**Objective:** 用 MediaRecorder 替换旧的 AudioRecord，支持自动 60 秒切割

**Files:**
- Create: `app/src/main/java/com/livespeaker/app/audio/AudioRecorder.kt`（覆盖重写）

**设计要点：**

```kotlin
class AudioRecorder(private val outputDir: File) {
    
    enum class State { IDLE, RECORDING, PAUSED }
    
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    
    val state = MutableStateFlow(State.IDLE)
    val completedFiles = MutableStateFlow<List<File>>(emptyList())
    val currentDuration = MutableStateFlow(0L) // 当前片段已录制毫秒数
    
    private val handler = Handler(Looper.getMainLooper())
    private var durationRunnable: Runnable? = null
    
    /**
     * 开始录音。自动创建新片段，60 秒后自动切割。
     */
    fun start(): Boolean
    
    /**
     * 暂停录音（回放时调用）。
     * MediaRecorder API 26+ 支持 pause()/resume()
     */
    fun pause()
    fun resume()
    
    /**
     * 停止录音，释放当前 MediaRecorder
     */
    fun stop()
    
    /**
     * 释放所有资源
     */
    fun release()
}
```

**关键实现逻辑：**

1. **start()**: 
   - 生成文件名 `recording_20260511_132045.m4a`
   - 创建 MediaRecorder，设置 AudioSource=MIC, OutputFormat=MPEG_4, AudioEncoder=AAC, MaxDuration=60000
   - `setOnInfoListener` 监听 `MEDIA_RECORDER_INFO_MAX_DURATION_REACHED` → 自动调用 `onSegmentComplete()`
   - 启动时长计数器（100ms 轮询 Handler）

2. **onSegmentComplete()**:
   - 停止当前 MediaRecorder，release
   - 文件加入 `completedFiles`
   - 如果录音未停止，立即创建新 MediaRecorder 开始下一段

3. **pause()/resume()**: 
   - API 26+ 使用 `MediaRecorder.pause()`/`resume()`
   - API < 26 不支持，但 minSdk 已经是 26，直接使用

4. **文件命名**：`recording_{yyyyMMdd_HHmmss}_{seq}.m4a`，其中 seq 是同一录音会话中的序号

**Step 1:** 编写完整代码

**Step 2:** 提交

```bash
git add app/src/main/java/com/livespeaker/app/audio/AudioRecorder.kt
git commit -m "feat: 重写 AudioRecorder 为 MediaRecorder 实现，支持 60s 自动切割"
```

---

### Task 6: 创建 RecordingViewModel 🟡

**Risk:** 🟡 Medium（协调录音和回放的状态机）

**Objective:** 管理录音的完整生命周期和回放状态

**Files:**
- Create: `app/src/main/java/com/livespeaker/app/viewmodel/RecordingViewModel.kt`

**设计要点：**

```kotlin
class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    
    // 录音状态
    private val recorder: AudioRecorder
    val isRecording: StateFlow<Boolean>
    val recordingDuration: StateFlow<Long>  // 当前片段已录秒数
    val segments: StateFlow<List<RecordingSegment>>
    
    // 回放状态
    private var player: MediaPlayer? = null
    val isPlaying: StateFlow<Boolean>
    val playingSegment: StateFlow<RecordingSegment?>
    val playbackPosition: StateFlow<Long>
    
    // 权限
    val hasPermission: StateFlow<Boolean>
    
    data class RecordingSegment(
        val file: File,
        val index: Int,          // 序号
        val durationMs: Long,    // 时长
        val timestamp: Long,     // 开始时间
        val fileName: String     // 显示名
    )
    
    fun requestPermission()
    fun startRecording()
    fun stopRecording()
    fun pauseRecording()
    fun resumeRecording()
    fun playSegment(segment: RecordingSegment)
    fun stopPlayback()
}
```

**状态机逻辑：**

```
         ┌─────────┐  startRecording()   ┌───────────┐
         │  IDLE   │ ──────────────────→ │ RECORDING │
         └─────────┘                     └─────┬─────┘
              ↑                                 │
              │ stopPlayback()          pauseRecording()
              │                                 ↓
         ┌────┴──────┐                   ┌──────────┐
         │  PLAYING  │ ← playSegment()   │  PAUSED   │
         └───────────┘                   └─────┬─────┘
                                               │
                                        resumeRecording()
                                               │
                                               ↓
                                          ┌───────────┐
                                          │ RECORDING │
                                          └───────────┘
```

**关键规则：**
- `playSegment()` → 如果正在录音，先 `pauseRecording()` → 再 `startPlaying()`
- `stopPlayback()` → 如果之前因为播放暂停了录音，恢复录音
- 录制中不能同时播放

**Step 1:** 编写完整 ViewModel

**Step 2:** 提交

```bash
git add app/src/main/java/com/livespeaker/app/viewmodel/RecordingViewModel.kt
git commit -m "feat: 创建 RecordingViewModel - 录音/回放状态管理"
```

---

### Task 7: 简化 MainActivity.kt 🟢

**Risk:** 🟢 Low

**Objective:** 移除旧权限逻辑、广播接收器、Service 调用，只保留 RECORD_AUDIO 请求

**Files:**
- Modify: `app/src/main/java/com/livespeaker/app/ui/MainActivity.kt`

**新代码核心结构：**

```kotlin
class MainActivity : ComponentActivity() {
    
    private val viewModel: RecordingViewModel by viewModels()
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onPermissionGranted()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    // 保留现有主题色
                )
            ) {
                Surface(...) {
                    MainScreen(viewModel = viewModel) {
                        if (viewModel.hasPermission.value) {
                            viewModel.startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            }
        }
    }
}
```

**Step 1:** 重写 MainActivity

**Step 2:** 提交

```bash
git add app/src/main/java/com/livespeaker/app/ui/MainActivity.kt
git commit -m "refactor: 简化 MainActivity - 单权限请求 + ViewModel"
```

---

### Task 8: 创建新 MainScreen（核心 UI）🔴

**Risk:** 🔴 High（最核心的 UI 任务，涉及 Compose 状态订阅、列表交互、播放控制）

**Objective:** 实现录音控制 + 文件列表 + 播放按钮的完整 UI

**Files:**
- Create: `app/src/main/java/com/livespeaker/app/ui/screen/MainScreen.kt`（覆盖 TranscriptionScreen.kt）
- Delete: `app/src/main/java/com/livespeaker/app/ui/screen/TranscriptionScreen.kt`

**UI 布局：**

```
┌─────────────────────────────┐
│  LiveSpeaker  v2            │  ← TopAppBar
├─────────────────────────────┤
│  🔴 录音中  00:47 / 片段 #3 │  ← 状态栏（录音时显示）
│  ⚫ 未录音                  │  ← 状态栏（空闲时显示）
│  ⏸ 已暂停                  │  ← 状态栏（暂停时显示）
├─────────────────────────────┤
│  📁 录音文件 (3)            │  ← 列表标题
│                             │
│  ┌───────────────────────┐  │
│  │ #1  00:59  ▶          │  │  ← 列表项（文件名、时长、播放按钮）
│  │     2026-05-11 13:20  │  │
│  ├───────────────────────┤  │
│  │ #2  01:00  ⏸         │  │  ← 正在播放的显示暂停图标
│  │     2026-05-11 13:21  │  │      并高亮背景
│  ├───────────────────────┤  │
│  │ #3  00:47  ▶          │  │  ← 最新片段，可能还在录制中
│  │     2026-05-11 13:22  │  │      显示录制中动画
│  └───────────────────────┘  │
│                             │
├─────────────────────────────┤
│         [ ⏺ 开始录音 ]      │  ← FAB（录音中显示 ■ 停止）
│         [ ▶ 开始录音 ]      │  ← FAB（空闲状态）
└─────────────────────────────┘
```

**交互行为：**

| 状态 | FAB 图标 | 点击行为 |
|------|---------|---------|
| IDLE | ▶ PlayArrow | 请求权限 → 开始录音 |
| RECORDING | ■ Stop | 停止录音 |
| PAUSED | ▶ PlayArrow | 恢复录音 |

| 列表项播放按钮 | 当前状态 | 点击行为 |
|-------------|---------|---------|
| ▶ | 未播放 | 开始播放该文件（暂停录音） |
| ⏸ | 正在播放 | 停止播放（恢复录音） |

**实现细节：**

```kotlin
@Composable
fun MainScreen(
    viewModel: RecordingViewModel,
    onFabClick: () -> Unit
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val segments by viewModel.segments.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("LiveSpeaker v2") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onFabClick) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop 
                                  else Icons.Default.PlayArrow,
                    contentDescription = if (isRecording) "停止录音" else "开始录音"
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 状态栏
            RecordingStatusBar(
                isRecording = isRecording,
                isPlaying = isPlaying,
                duration = viewModel.recordingDuration,
                segmentCount = segments.size
            )
            
            // 文件列表
            if (segments.isEmpty()) {
                EmptyState()
            } else {
                SegmentList(
                    segments = segments,
                    playingSegment = viewModel.playingSegment,
                    onPlay = { segment -> viewModel.playSegment(segment) },
                    onStopPlayback = { viewModel.stopPlayback() }
                )
            }
        }
    }
}

@Composable
private fun SegmentItem(
    segment: Segment,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("#${segment.index}  ${formatDuration(segment.durationMs)}")
            Text(formatTimestamp(segment.timestamp), style = caption)
        }
        IconButton(onClick = if (isPlaying) onStop else onPlay) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause 
                              else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "停止播放" else "播放"
            )
        }
    }
}
```

**Step 1:** 删除 TranscriptionScreen.kt

**Step 2:** 创建 MainScreen.kt

**Step 3:** 验证编译通过

**Step 4:** 提交

```bash
git add app/src/main/java/com/livespeaker/app/ui/screen/MainScreen.kt
git rm app/src/main/java/com/livespeaker/app/ui/screen/TranscriptionScreen.kt
git commit -m "feat: 新 MainScreen - 录音控制 + 文件列表 + 播放回放"
```

---

### Task 9: 更新 AudioPermission.kt（简化）🟢

**Risk:** 🟢 Low

**Objective:** 简化为只处理 RECORD_AUDIO

**Files:**
- Modify: `app/src/main/java/com/livespeaker/app/audio/AudioPermission.kt`

**简化为：**

```kotlin
object AudioPermission {
    fun isGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
```

**Step 1:** 编辑文件

**Step 2:** 提交

```bash
git add app/src/main/java/com/livespeaker/app/audio/AudioPermission.kt
git commit -m "refactor: 简化 AudioPermission 为仅检查录音权限"
```

---

### Task 10: 整体验证编译 🟡

**Risk:** 🟡 Medium

**Objective:** 确保整个项目能编译通过

**验证步骤：**

```bash
cd ~/Projects/LiveSpeaker
./gradlew assembleDebug
```

**预期：** BUILD SUCCESSFUL

**如果失败：** 根据错误逐个修复，更新对应文件

**Step 1:** 运行编译

**Step 2:** 修复编译错误（如果有）

**Step 3:** 提交

```bash
git add -A
git commit -m "fix: 编译错误修复"
```

---

### Task 11: 更新 DEVELOPMENT.md 🟢

**Risk:** 🟢 Low

**Objective:** 记录 v2 重构的架构决策

**Files:**
- Modify: `DEVELOPMENT.md`

**Step 1:** 在 DEVELOPMENT.md 开头添加新的会话记录

**Step 2:** 提交

```bash
git add DEVELOPMENT.md
git commit -m "docs: 记录 v2 重构到 DEVELOPMENT.md"
```

---

## 📊 任务优先级排序

| 顺序 | Task | 说明 | 风险 |
|------|------|------|------|
| 1 | Task 1 | 删除旧文件 | 🟢 |
| 2 | Task 2 | 精简 build.gradle | 🟢 |
| 3 | Task 3 | 精简 Manifest | 🟢 |
| 4 | Task 4 | 简化 Application | 🟢 |
| 5 | Task 5 | 重写 AudioRecorder | 🟡 |
| 6 | Task 6 | 创建 ViewModel | 🟡 |
| 7 | Task 7 | 简化 MainActivity | 🟢 |
| 8 | Task 9 | 简化 AudioPermission | 🟢 |
| 9 | Task 8 | 创建新 MainScreen | 🔴 |
| 10 | Task 10 | 整体验证编译 | 🟡 |
| 11 | Task 11 | 更新 DEVELOPMENT.md | 🟢 |

---

## ⚠️ 风险和注意事项

### 1. MediaRecorder pause() 可靠性
- `pause()`/`resume()` API 26+ 可用，我们的 minSdk 正是 26
- 但部分厂商（小米、OPPO）的 ROM 可能行为不一致
- **缓解**：如果 pause 失败，回退到 stop + start 新建片段

### 2. 60 秒切割的间隙
- MediaRecorder stop → 新 start 之间有 ~200-500ms 间隙
- 对于录音笔记场景可接受，但对于会议记录可能丢失词语
- **后续可优化**：用双 MediaRecorder 交替录制零间隙

### 3. 回放时恢复录音的逻辑
- 需要记住"用户是手动暂停的还是因为播放自动暂停的"
- 播放结束/停止后，只有"自动暂停"才恢复录音
- **实现**：ViewModel 维护 `wasRecordingBeforePlayback` 标志

### 4. 文件管理
- 录音文件存储在 `context.filesDir/recordings/`
- 目前不提供删除功能（后续迭代加）
- 长期录音会占用存储空间，后续需加存储管理

### 5. 测试
- CI 模拟器无麦克风，需要真机测试
- 编译通过即视为可交付，真机验证由用户自行完成

---

## ❌ 不在本次范围

- 音频波形显示
- 文件删除/重命名
- 音频格式选择
- 降噪处理
- 后台录音（不依赖 Activity）
- 通知栏控制
- 文件分享/导出

---

## 🎯 验收标准

1. App 启动 → 显示 "点击开始录音"
2. 点击 FAB → 弹出录音权限请求
3. 授权后 → 自动开始录音，状态栏显示 "录音中"
4. 每 60 秒 → 自动切割，列表新增一条
5. 点击 ■ 停止 → 录音停止，列表显示所有片段
6. 点击某条 ▶ → 暂停录音 → 播放该片段 → 列表项高亮 + 图标变 ⏸
7. 点击 ⏸ → 停止播放 → 恢复录音
8. APK 体积从 ~300MB 降至 ~5MB
9. `./gradlew assembleDebug` BUILD SUCCESSFUL
