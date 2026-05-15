package com.livespeaker.app

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.livespeaker.app.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 录音启动崩溃测试 + STT 导航测试。
 *
 * 验证：
 * 1. 点击录音按钮不会闪退
 * 2. 底部导航栏两个 Tab 可以切换
 * 3. "转写" Tab 默认显示空状态
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

        // 步骤 3: 等待录音启动
        Thread.sleep(5000)

        // 步骤 4: 如果 App 崩溃了，以下断言会因 Compose 连接断开而失败
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

    @Test
    fun navigateToTranscriptionsTab_showsEmptyState() {
        // 切换到「转写」Tab
        composeTestRule
            .onNodeWithText("转写")
            .performClick()

        // 验证空状态显示
        composeTestRule
            .onNodeWithText("暂无转写记录")
            .assertExists()
    }

    @Test
    fun navigateBackToRecordTab_showsFab() {
        // 先切到转写
        composeTestRule
            .onNodeWithText("转写")
            .performClick()

        // 再切回录音
        composeTestRule
            .onNodeWithText("录音")
            .performClick()

        // FAB 应该存在
        composeTestRule
            .onNodeWithContentDescription("开始录音")
            .assertExists()
    }
}
