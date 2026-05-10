package com.livespeaker.app

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.livespeaker.app.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 录音启动崩溃测试。
 *
 * 模拟用户点击「开始录音」按钮，验证 App 不会闪退。
 * 这是 CI 中最关键的回归测试 — 如果将来有人改了 Service 初始化逻辑导致崩溃，
 * 这个测试会直接失败。
 *
 * 注意：模拟器无麦克风/模型文件，Service 会因模型未下载而优雅停止。
 * 测试只验证「不崩溃」——即点击后进程仍存活。
 */
@RunWith(AndroidJUnit4::class)
class RecordingCrashTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** 自动授予录音权限（模拟器无权限弹窗） */
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    @Test
    fun clickStartRecording_doesNotCrash() {
        // 步骤 1: 验证按钮存在
        composeTestRule
            .onNodeWithContentDescription("开始录音")
            .assertExists()

        // 步骤 2: 点击「开始录音」
        composeTestRule
            .onNodeWithContentDescription("开始录音")
            .performClick()

        // 步骤 3: 等待 Service 完整生命周期
        // （异步模型加载 → startForeground → 模型缺失 → stopSelf）
        Thread.sleep(5000)

        // 步骤 4: 如果 App 崩溃了，以下断言会因 Compose 连接断开而失败
        // isRecording=true 后 contentDescription 变为「停止录音」
        composeTestRule
            .onNodeWithContentDescription("停止录音")
            .assertExists()

        // ✅ 测试通过 = 点击录音按钮不会导致闪退
    }

    @Test
    fun clickStartRecordingTwice_doesNotCrash() {
        // 连续快速点击测试（模拟用户双击）
        composeTestRule
            .onNodeWithContentDescription("开始录音")
            .performClick()

        Thread.sleep(1000)

        composeTestRule
            .onNodeWithContentDescription("停止录音")
            .performClick()

        Thread.sleep(1000)

        composeTestRule
            .onNodeWithContentDescription("开始录音")
            .performClick()

        Thread.sleep(3000)

        // 进程仍存活
        composeTestRule
            .onNodeWithContentDescription("停止录音")
            .assertExists()
    }
}
