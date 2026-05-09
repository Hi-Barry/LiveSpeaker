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
4. **Release APK 仅一个架构**: arm64-v8a 和 armeabi-v7a 的 APK 在 Release 中可能被覆盖，需修复 artifact 命名
5. **悬浮球为 View 实现**: 非 Compose，后续应统一

### 🔮 下一步计划

- [ ] 适配 `SpeakerEmbeddingExtractor.compute()` 正确签名
- [ ] 切换为 Silero VAD（sherpa-onnx 内置）
- [ ] 模型下载进度 UI
- [ ] 说话人语音片段回放确认
- [ ] 转写历史记录导出
- [ ] 真机机房噪音测试

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

> **最后更新**: 2026-05-09 | **维护者**: Hermes Agent + Hi-Barry
