package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertThrows
import org.junit.Test

class PcmBlockQueueTest {
    @Test fun overflowPreservesAcceptedPrefixAndCannotResumeAcrossDroppedSource() {
        val queue = PcmBlockQueue(4, 2, 8)
        val input = byteArrayOf(1, 2, 3, 4)
        assertThat(queue.offer(0, input)).isTrue()
        input.fill(9)
        assertThat(queue.offer(2, input)).isTrue()
        assertThat(queue.offer(4, input)).isFalse()
        assertThat(queue.failure).isEqualTo(PcmQueueFailure.OVERFLOW)
        queue.poll()!!.use { assertThat(it.bytes.copyOf(it.count)).isEqualTo(byteArrayOf(1, 2, 3, 4)) }
        queue.poll()!!.use { assertThat(it.firstFrame).isEqualTo(2) }
        assertThat(queue.isDrained).isTrue()
        assertThat(queue.offer(4, input)).isFalse()
        queue.close()
    }

    @Test fun borrowedBufferCannotBeOverwrittenAndReturnedLeaseIsIdempotent() {
        val queue = PcmBlockQueue(4, 2, 8)
        queue.offer(0, byteArrayOf(1, 2, 3, 4))
        val first = queue.poll()!!
        queue.offer(2, byteArrayOf(5, 6, 7, 8))
        val second = queue.poll()!!
        second.close()
        assertThat(queue.offer(4, byteArrayOf(9, 10, 11, 12))).isTrue()
        second.close()
        assertThat(first.bytes).isEqualTo(byteArrayOf(1, 2, 3, 4))
        queue.poll()!!.use { assertThat(it.bytes).isEqualTo(byteArrayOf(9, 10, 11, 12)) }
        first.close()
        assertThrows(IllegalStateException::class.java) { first.bytes }
        queue.close()
    }

    @Test fun gapIsTerminalButEarlierQueuedAudioStaysReadable() {
        val queue = PcmBlockQueue(4, 2, 8)
        queue.offer(100, byteArrayOf(1, 2, 3, 4))
        assertThat(queue.offer(103, byteArrayOf(5, 6, 7, 8))).isFalse()
        assertThat(queue.failure).isEqualTo(PcmQueueFailure.DISCONTINUITY)
        queue.poll()!!.use { assertThat(it.firstFrame).isEqualTo(100) }
        assertThat(queue.isDrained).isTrue()
        queue.close()
    }

    @Test fun normalStopDrainsAndCloseDropsOnlyUnborrowedWork() {
        val queue = PcmBlockQueue(4, 2, 8)
        queue.offer(0, byteArrayOf(1, 2, 3, 4))
        queue.offer(2, byteArrayOf(5, 6, 7, 8))
        val borrowed = queue.poll()!!
        queue.seal()
        assertThat(queue.offer(4, byteArrayOf(1, 2, 3, 4))).isFalse()
        assertThat(queue.failure).isNull()
        assertThat(queue.isDrained).isFalse()
        queue.close()
        assertThat(queue.poll()).isNull()
        assertThat(borrowed.bytes).isEqualTo(byteArrayOf(1, 2, 3, 4))
        borrowed.close()
        assertThat(queue.isDrained).isTrue()
    }

    @Test fun validatesBudgetAndOnlyPublishesCompleteFrames() {
        assertThrows(IllegalArgumentException::class.java) { PcmBlockQueue(4, 2, 7) }
        assertThrows(IllegalArgumentException::class.java) { PcmBlockQueue(3, 2, 8) }
        val queue = PcmBlockQueue(8, 2, 16)
        assertThrows(IllegalArgumentException::class.java) { queue.offer(0, byteArrayOf(1, 2, 3)) }
        assertThat(queue.offer(0, byteArrayOf(1, 2, 3, 4, 5, 6), count = 4)).isTrue()
        queue.poll()!!.use { assertThat(it.count).isEqualTo(4) }
        queue.close()
    }

    @Test fun concurrentProducerAndWriterPreserveEveryFrameWithReusedInput() {
        val queue = PcmBlockQueue(4, 8, 32)
        val permits = Semaphore(8)
        val failure = AtomicReference<Throwable?>()
        val producer = thread(name = "pcm-test-producer") {
            try {
                val input = ByteArray(4)
                repeat(1000) { index ->
                    permits.acquire()
                    input[0] = index.toByte()
                    input[1] = (index shr 8).toByte()
                    input[2] = 0
                    input[3] = 0
                    check(queue.offer(index * 2L, input))
                    input.fill(127)
                }
                queue.seal()
            } catch (error: Throwable) { failure.set(error) }
        }
        val writer = thread(name = "pcm-test-writer") {
            try {
                repeat(1000) { index ->
                    var block = queue.poll()
                    while (block == null) {
                        if (Thread.currentThread().isInterrupted) return@thread
                        Thread.yield()
                        block = queue.poll()
                    }
                    block.use {
                        check(it.firstFrame == index * 2L)
                        check((it.bytes[0].toInt() and 255) or ((it.bytes[1].toInt() and 255) shl 8) == index)
                    }
                    permits.release()
                }
            } catch (error: Throwable) { failure.set(error) }
        }
        try {
            producer.join(5000)
            writer.join(5000)
            assertThat(producer.isAlive || writer.isAlive).isFalse()
            assertThat(failure.get()).isNull()
            assertThat(queue.isDrained).isTrue()
        } finally {
            producer.interrupt()
            writer.interrupt()
            queue.close()
        }
    }
}
