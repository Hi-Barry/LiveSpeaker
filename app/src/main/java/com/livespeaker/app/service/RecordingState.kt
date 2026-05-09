package com.livespeaker.app.service

/**
 * 录音状态机。
 *
 * IDLE → RECORDING → PAUSED → RECORDING → STOPPED → IDLE
 */
enum class RecordingState {
    /** 空闲，未录音 */
    IDLE,
    /** 正在录音和转写 */
    RECORDING,
    /** 暂停 (录音停止，可恢复) */
    PAUSED,
    /** 已停止，清理资源 */
    STOPPED;

    fun canStart(): Boolean = this == IDLE || this == STOPPED
    fun canPause(): Boolean = this == RECORDING
    fun canResume(): Boolean = this == PAUSED
    fun canStop(): Boolean = this == RECORDING || this == PAUSED
}
