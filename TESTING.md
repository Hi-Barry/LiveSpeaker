# LiveSpeaker 测试方案

> 2026-05-09 | Plan mode — 评估后可实施

---

## 一、免费在线测试平台对比

| 平台 | 免费额度 | 物理设备 | 信用卡 | 集成 | 适合 |
|------|:------:|:------:|:------:|:--:|------|
| **Firebase Test Lab** | 15次/天 (10虚拟+5真机) | ✅ 5次/天 | ❌ 不需要 | GitHub Actions | 🥇 首选 |
| BrowserStack App Live | 30分钟试用 | ✅ | ❌ 不需要 | 手动上传 | 🥈 手动验证 |
| LambdaTest | 100分钟试用 | ✅ | 需要 | 手动上传 | 🥉 备选 |
| Sauce Labs | 28天试用 | ✅ | 需要 | 手动 | 一次性 |

> **结论：Firebase Test Lab 是唯一真正免费+CI 集成的方案。**

---

## 二、Firebase Test Lab 详解

### 免费额度 (Spark 计划)

- **15 次测试/天** (项目级，非用户级)
  - 10 次虚拟设备 (Android 虚拟镜像)
  - 5 次物理设备 (真实手机)
- **不绑定信用卡**
- 每天 00:00 PST (北京时间 15:00) 重置

### 支持的测试类型

| 类型 | 说明 | 适合 LiveSpeaker？ |
|------|------|:--:|
| **Robo Test** | 自动点击/滑动探索 App，截图+日志 | ✅ 验证安装、UI渲染 |
| **Instrumentation** | 运行 `androidTest` 测试代码 | ✅ 编写语音模拟测试 |
| **Game Loop** | 运行游戏循环测试 | ❌ |

### ⚠️ LiveSpeaker 测试限制

LiveSpeaker 依赖麦克风，而云设备农场存在以下限制：

1. **虚拟设备无真实麦克风** — 无法测试语音识别
2. **物理设备麦克风受限** — Firebase 真机可能不传音频或仅提供静音
3. **Robo Test 无法触发录音权限** — 权限对话框会被跳过

**务实目标**：先测 **安装 → 启动 → UI 渲染 → 权限流程**。语音识别功能需真机测试。

---

## 三、实施计划

### 阶段 A: 集成 Firebase Test Lab 到 CI (2 小时)

**影响文件**:
- `.github/workflows/android-ci.yml` — 新增 test job
- `app/build.gradle.kts` — 添加 `google-services.json` 引用 (可选)
- `app/src/androidTest/` — 新目录，添加基础测试

**任务**:

| # | 任务 | 风险 |
|---|------|:--:|
| A1 | 创建 Firebase 项目 (免费 Spark 计划) | 低 |
| A2 | 获取 Service Account JSON | 低 |
| A3 | 将 Service Account 设为 GitHub Secret | 低 |
| A4 | 编写 `app/src/androidTest/` 基础 instrumentation 测试 | 中 |
| A5 | CI workflow 中添加 Robo Test step | 低 |
| A6 | 验证构建 → 测试全链路 | 中 |

### 阶段 B: 编写仪表化测试 (1 天)

**测试内容**:

```kotlin
// app/src/androidTest/java/com/livespeaker/app/
class BasicInstrumentedTest {
    @Test
    fun appLaunchesSuccessfully()          // 启动→主界面显示
    @Test  
    fun permissionDialogAppears()          // 权限请求弹窗
    @Test
    fun notificationChannelExists()        // 通知渠道创建
}
```

### 阶段 C: 手动 BrowserStack 验证 (可选)

1. 注册 BrowserStack 免费试用
2. 上传 APK → 选 Google Pixel 8
3. 手动操作：安装 → 授权 → 开始录音 → 查看转写

---

## 四、CI 集成预览

```yaml
# 在 android-ci.yml 中添加
test:
  needs: build
  runs-on: ubuntu-latest
  steps:
    - uses: actions/download-artifact@v4
      with:
        name: LiveSpeaker-debug-arm64-v8a
    
    - name: Run on Firebase Test Lab
      uses: asadmansr/firebase-test-lab-action@v1
      with:
        arg-spec: 'test-lab/gcloud.yml'
      env:
        SERVICE_ACCOUNT: ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}
```

---

## 五、成本预估

| 项目 | 免费额度 | 是否够用 |
|------|:------:|:--:|
| CI 每次 Push 跑测试 | 15次/天 | ✅ 够用 (每天不会 Push 15 次) |
| 虚拟设备测试 | 10次/天 | ✅ |
| 物理真机测试 | 5次/天 | ⚠️ 如需频繁测麦克风可能不够 |

---

## 六、决策点

1. **要不要把 Firebase Test Lab 集成到 CI？**
   - ✅ 集成了每次 Push 自动验证安装+启动，零成本
2. **要不要写 instrumentation 测试？**
   - ⚠️ 纯验证型测试有价值，但语音识别测试在云端有限制
3. **要不要用 BrowserStack 手动验证？**
   - 可选，用于真机手动测试的一次性验证

> **推荐: 先做阶段 A (Firebase 集成)，2 小时内即可上线。**
