# LiveSpeaker

Android 录音 + 云端语音转文字

## 功能

- 🎙️ **录音**: MediaRecorder 录音，每 1 分钟自动切割为独立 M4A 片段
- ▶️ **回放**: ExoPlayer 播放，支持任意片段切换
- ☁️ **语音转文字**: 支持 SiliconFlow / OpenAI 兼容 STT API，录音片段自动转录
- 📝 **转写查看**: 转写列表带时间戳，可点击播放对应音频，支持复制文本到剪贴板
- 📤 **录音管理**: 录音条目长按 / 更多按钮弹出菜单，支持导出到 Downloads、分享、删除
  - 删除录音会同步删除关联的转录文本
- 🛡️ **片段保护**: 手动停止录音时，未达切割时长的当前片段会被保留（不再丢失）
- ⚙️ **可配置 STT**: provider、base URL、API Key、模型 全部可配
- 🌓 **自动主题**: 跟随系统暗色/亮色模式自动切换

## 技术栈

| 组件 | 技术 |
|------|------|
| 录音 | MediaRecorder (AAC/M4A) |
| 播放 | Media3 ExoPlayer |
| STT API | OkHttp + multipart/form-data |
| 设置存储 | DataStore Preferences |
| 导航 | Navigation Compose (底部 Tab) |
| 序列化 | kotlinx.serialization |
| UI | Jetpack Compose + Material3 |

## 架构

```
┌─────────────────────────────────────────┐
│  MainActivity                            │
│  ┌─────────────────────────────────┐     │
│  │  AppNavigation                   │     │
│  │  ┌──────────┐ ┌──────────────┐  │     │
│  │  │ 录音 Tab  │ │ 转写 Tab     │  │     │
│  │  │RecordScrn│ │TranScriptScrn│  │     │
│  │  └──────────┘ └──────────────┘  │     │
│  │         RecordingViewModel       │     │
│  │  ┌──────────┐ ┌──────────────┐  │     │
│  │  │AudioRecrd│ │ SttEngine    │  │     │
│  │  │          │ │ (OkHttp API) │  │     │
│  │  └──────────┘ └──────┬───────┘  │     │
│  │                      │          │     │
│  └──────────────────────┼──────────┘     │
└─────────────────────────┼────────────────┘
                          │ multipart/form-data
                          ▼
                  ┌─────────────────┐
                  │  SiliconFlow    │
                  │  SenseVoiceSmall│
                  │  /v1/audio/     │
                  │  transcriptions │
                  └─────────────────┘
```

## 版本历程

> 详细开发日志见 [DEVELOPMENT.md](./DEVELOPMENT.md)

| 版本 | 日期 | 里程碑 |
|------|------|--------|
| v0.4.1 | 2026-05-17 | 🛡️ 停止录音保留当前片段 |
| v0.4.0 | 2026-05-17 | 📤 长按菜单（导出/分享/删除）+ 转录复制 |
| v0.3.9 | 2026-05-16 | 🌓 状态栏图标跟随系统主题 |
| v0.3.5 | 2026-05-16 | 🌓 系统亮暗主题自动切换 |
| v0.3.1 | 2026-05-15 | 🔄 STT 自动重试 + 设置集成 |
| v0.3.0 | 2026-05-15 | ☁️ 云端语音转文字 (STT) |
| v0.2.6 | 2026-05-11 | ⚙️ 可配置切片时间 |
| v0.2.5 | 2026-05-11 | 🎙️ 前台服务后台录音 |
| v0.2.3 | 2026-05-11 | 🐛 修复时长显示错误 |
| v0.2.0 | 2026-05-11 | 🔄 v2 纯录音切割架构 |
| v0.1.0 | 2026-05-09 | 🏗️ v1 离线语音识别（已废弃） |

## 构建

1. 用 Android Studio 打开项目
2. 运行到 Android 8.0+ 设备
3. 在设置页配置 STT API（获取 SiliconFlow API Key: https://siliconflow.cn）

## 权限

- `RECORD_AUDIO` - 录音
- `INTERNET` - STT API

## STT API 兼容性

支持所有 OpenAI 兼容的 `/v1/audio/transcriptions` 端点：

| 提供商 | Base URL | 默认模型 |
|--------|----------|----------|
| SiliconFlow | `https://api.siliconflow.cn` | `FunAudioLLM/SenseVoiceSmall` |
| OpenAI | `https://api.openai.com` | `whisper-1` |
| 自定义 | 任意 | 自定义 |
