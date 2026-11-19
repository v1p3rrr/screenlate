package com.vpr.screenlate.core.media

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class FakeAudioSource(
    override val sampleRate: Int = 1000,
    val blockFrames: Int = 50,
    val failOnStart: Boolean = false,
    val failOnRead: Boolean = false,
) : AudioSource {
    override val channelCount: Int = 1
    private val running = AtomicBoolean(false)
    private val totalEmittedFrames = AtomicLong(0)
    private val targetFrames = AtomicLong(0)

    override fun start(): Boolean {
        if (failOnStart) return false
        running.set(true)
        return true
    }

    fun emitFrames(count: Int) {
        targetFrames.addAndGet(count.toLong())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!running.get()) return -1
        if (failOnRead) return -1
        val needed = minOf(blockFrames.toLong(), targetFrames.get() - totalEmittedFrames.get())
        if (needed <= 0) {
            Thread.sleep(10)
            return 0
        }
        val framesToRead = minOf(needed, (length / 2).toLong()).toInt()
        val start = totalEmittedFrames.get()
        for (i in 0 until framesToRead) {
            val frame = start + i
            buffer[offset + i * 2] = frame.toByte()
            buffer[offset + i * 2 + 1] = (frame shr 8).toByte()
        }
        totalEmittedFrames.addAndGet(framesToRead.toLong())
        return framesToRead * 2
    }

    fun expectedPcm(startFrame: Long, endFrame: Long): ByteArray {
        val count = (endFrame - startFrame).toInt()
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val frame = startFrame + i
            bytes[i * 2] = frame.toByte()
            bytes[i * 2 + 1] = (frame shr 8).toByte()
        }
        return bytes
    }

    override fun timestampNanos(): Long? = 10_000_000_000L

    override fun stop() {
        running.set(false)
    }

    override fun close() {
        stop()
    }
}
