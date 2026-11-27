package com.vpr.screenlate.core.media

import java.util.ArrayDeque

internal enum class PcmQueueFailure { OVERFLOW, DISCONTINUITY }

/**
 * Fixed PCM buffer pool for one capture producer and one writer. A failed offer is terminal: the capture
 * owner must stop reading, then drain the accepted prefix. Neither overflow nor a gap silently drops data.
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal class PcmBlockQueue(blockBytes: Int, capacity: Int, maxBytes: Int) : AutoCloseable {
    private val free = ArrayDeque<ByteArray>()
    private val ready = ArrayDeque<Pending>()
    private val blockBytes = blockBytes
    private var nextFrame: Long? = null
    private var sealed = false
    private var closed = false
    private var borrowed = 0
    private var fault: PcmQueueFailure? = null

    init {
        require(blockBytes > 0 && blockBytes % 2 == 0 && capacity > 0 && maxBytes > 0)
        require(Math.multiplyExact(blockBytes.toLong(), capacity.toLong()) <= maxBytes)
        repeat(capacity) { free.add(ByteArray(blockBytes)) }
    }

    val failure: PcmQueueFailure? @Synchronized get() = fault
    val hasPending: Boolean @Synchronized get() = ready.isNotEmpty()
    val isDrained: Boolean @Synchronized get() = sealed && ready.isEmpty() && borrowed == 0
    val retainedBytes: Long
        @Synchronized get() = (free.size + ready.size + borrowed).toLong() * blockBytes

    @Synchronized
    fun offer(firstFrame: Long, pcm: ByteArray, count: Int = pcm.size, gapEndNanos: Long? = null): Boolean {
        require(firstFrame >= 0 && count > 0 && count <= pcm.size && count <= blockBytes && count % 2 == 0)
        if (sealed || closed) return false
        if (nextFrame != null && firstFrame != nextFrame) {
            fault = PcmQueueFailure.DISCONTINUITY
            sealed = true
            return false
        }
        val end = Math.addExact(firstFrame, count / 2L)
        val bytes = free.pollFirst() ?: run {
            fault = PcmQueueFailure.OVERFLOW
            sealed = true
            return false
        }
        pcm.copyInto(bytes, endIndex = count)
        ready.addLast(Pending(bytes, firstFrame, count, gapEndNanos))
        nextFrame = end
        (this as java.lang.Object).notifyAll()
        return true
    }

    @Synchronized
    fun poll(): Block? {
        if (closed) return null
        val pending = ready.pollFirst() ?: return null
        borrowed++
        return Block(pending)
    }

    @Synchronized
    fun poll(timeoutMillis: Long): Block? {
        if (closed) return null
        if (ready.isEmpty() && !sealed && timeoutMillis > 0) {
            runCatching { (this as java.lang.Object).wait(timeoutMillis) }
        }
        if (closed) return null
        val pending = ready.pollFirst() ?: return null
        borrowed++
        return Block(pending)
    }

    /** Normal stop disallows new blocks but leaves all accepted data drainable. */
    @Synchronized
    fun seal() {
        sealed = true
        (this as java.lang.Object).notifyAll()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        sealed = true
        free.clear()
        ready.clear()
        (this as java.lang.Object).notifyAll()
    }

    inner class Block internal constructor(pending: Pending) : AutoCloseable {
        val firstFrame: Long = pending.firstFrame
        val count: Int = pending.count
        val gapEndNanos: Long? = pending.gapEndNanos
        private var storage: ByteArray? = pending.bytes

        val bytes: ByteArray get() = synchronized(this@PcmBlockQueue) {
            checkNotNull(storage) { "PCM block has been returned" }
        }

        override fun close() {
            synchronized(this@PcmBlockQueue) {
                storage?.let {
                    storage = null
                    borrowed--
                    if (!closed) free.addLast(it)
                }
            }
        }
    }

    internal data class Pending(val bytes: ByteArray, val firstFrame: Long, val count: Int, val gapEndNanos: Long? = null)
}
