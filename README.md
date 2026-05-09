# LiveSpeaker

Android 离线实时语音识别 + 说话人识别

## 功能

- 🎙️ **实时语音转写**: 中英文混合，离线运行
- 👤 **说话人识别**: 自动区分不同说话人
- 🔇 **智能降噪**: GTCRN 模型去除背景噪音（机房/工厂环境）
- 📝 **标点恢复**: 自动添加逗号、句号
- 📱 **悬浮球控制**: 快速开始/停止录音
- 🔋 **前台服务**: 后台持续运行

## 技术栈

| 组件 | 技术 |
|------|------|
| ASR | SenseVoice (int8, 228MB) via sherpa-onnx |
| 说话人嵌入 | 3D-Speaker ERes2Net (~40MB) via sherpa-onnx |
| 降噪 | GTCRN (~500KB) via sherpa-onnx |
| VAD | Silero VAD (内置) |
| 数据库 | Room (说话人档案) |
| UI | Jetpack Compose + Material3 |

## 构建

1. 用 Android Studio 打开项目
2. 下载模型文件 (首次启动时自动下载):
   - SenseVoice: https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models
   - 3D-Speaker: https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models
   - GTCRN: https://github.com/k2-fsa/sherpa-onnx/releases/tag/speech-enhancement-models
3. 运行到 Android 8.0+ 设备

## 架构

```
麦克风 → GTCRN降噪 → VAD分段 → SenseVoice ASR → 文本+标点
                              → 3D-Speaker嵌入 → 说话人聚类 → 说话人标签
```

## 权限

- `RECORD_AUDIO` - 录音
- `FOREGROUND_SERVICE` - 后台运行
- `POST_NOTIFICATIONS` - 通知栏 (Android 13+)
- `SYSTEM_ALERT_WINDOW` - 悬浮球
- `INTERNET` - 模型下载
