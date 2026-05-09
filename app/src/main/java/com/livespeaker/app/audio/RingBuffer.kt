package com.livespeaker.app.audio

import java.util.concurrent.atomic.AtomicLong

/**
 * 单生产者-单消费者 无锁环形缓冲区。
 * 专为 16kHz/Mono/PCM16 音频流设计。
 *
 * 写入线程: 录音线程
 * 读取线程: 处理线程
 */
class RingBuffer(capacityMs: Int = 3000, sampleRate: Int = 16000) {

    private val capacity = (sampleRate * capacityMs / 1000)
    private val buffer = ShortArray(capacity)
    private val writePos = AtomicLong(0)
    private val readPos = AtomicLong(0)

    /** 可读取的样本数 */
    val available: Int
        get() {
            val w = writePos.get()
            val r = readPos.get()
            return if (w >= r) (w - r).toInt() else (capacity - (r - w)).toInt()
        }

    /** 可写入的空间 */
    val free: Int
        get() = capacity - available

    /** 写入音频样本 (生产者) */
    fun write(src: ShortArray, offset: Int = 0, length: Int = src.size): Int {
        val n = minOf(length, free)
        if (n <= 0) return 0

        val w = (writePos.get() % capacity).toInt()
        val firstPart = minOf(n, capacity - w)

        System.arraycopy(src, offset, buffer, w, firstPart)
        if (firstPart < n) {
            System.arraycopy(src, offset + firstPart, buffer, 0, n - firstPart)
        }
        writePos.addAndGet(n.toLong())
        return n
    }

    /** 读取音频样本 (消费者) */
    fun read(dst: ShortArray, offset: Int = 0, length: Int = dst.size): Int {
        val n = minOf(length, available)
        if (n <= 0) return 0

        val r = (readPos.get() % capacity).toInt()
        val firstPart = minOf(n, capacity - r)

        System.arraycopy(buffer, r, dst, offset, firstPart)
        if (firstPart < n) {
            System.arraycopy(buffer, 0, dst, offset + firstPart, n - firstPart)
        }
        readPos.addAndGet(n.toLong())
        return n
    }

    /** 清空缓冲区 */
    fun clear() {
        readPos.set(writePos.get())
    }

    /** 缓冲区是否有足够数据 */
    fun hasMinSamples(minSamples: Int): Boolean = available >= minSamples
}
