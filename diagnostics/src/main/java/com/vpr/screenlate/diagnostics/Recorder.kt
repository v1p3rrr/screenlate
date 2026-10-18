package com.vpr.screenlate.diagnostics

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Writes lines to [log] on a thread of its own, so callers on the main thread never touch the disk. Each batch of
 * queued lines is flushed when the queue runs empty, which keeps what a killed process had recorded but a few
 * milliseconds. A full queue drops lines and counts them.
 */
internal class Recorder(private val log: RotatingLog) {
    private val queue = LinkedBlockingQueue<Any>(QUEUE_SIZE)
    private val dropped = AtomicInteger()

    private val thread = Thread(::run, "diagnostics-writer").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
        start()
    }

    fun offer(line: String) {
        if (!queue.offer(line)) dropped.incrementAndGet()
    }

    /** Waits until everything queued so far is on disk, at most [timeoutMs]. */
    fun sync(timeoutMs: Long = 2_000) {
        val done = CountDownLatch(1)
        if (queue.offer(Sync(done))) done.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /** Deletes the files once the lines queued before are written. */
    fun clear() {
        val done = CountDownLatch(1)
        if (queue.offer(Clear(done))) done.await(2_000, TimeUnit.MILLISECONDS)
    }

    fun files() = log.files()

    private fun run() {
        while (true) {
            var item = queue.take()
            try {
                while (true) {
                    when (item) {
                        is String -> log.append(item)
                        is Sync -> {
                            log.flush()
                            item.done.countDown()
                        }
                        is Clear -> {
                            log.clear()
                            item.done.countDown()
                        }
                    }
                    item = queue.poll() ?: break
                }
                val lost = dropped.getAndSet(0)
                if (lost > 0) log.append(DiagJson.line(System.currentTimeMillis(), 0, "dropped", arrayOf("lines" to lost)))
                log.flush()
            } catch (e: Exception) {
                // A full disk or a deleted directory: the next line opens the file again.
                runCatching { log.close() }
            }
        }
    }

    private class Sync(val done: CountDownLatch)

    private class Clear(val done: CountDownLatch)

    private companion object {
        const val QUEUE_SIZE = 20_000
    }
}
