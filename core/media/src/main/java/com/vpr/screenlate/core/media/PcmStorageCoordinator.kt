package com.vpr.screenlate.core.media

import java.io.File
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

enum class PcmCoordinatorFailure {
    SOURCE_START_FAILED, SOURCE_READ_FAILED, SOURCE_STOP_FAILED, QUEUE_OVERFLOW, QUEUE_DISCONTINUITY,
    STORAGE_QUOTA_EXCEEDED, STORAGE_IO_ERROR, PLAYBACK_STOP_FAILED,
}

enum class PcmCoordinatorState { IDLE, STARTING, RUNNING, STOPPING, STOPPED, FAILED, CLOSED }

internal class PcmCoordinatorConfig(
    val sampleRate: Int = 48000,
    val chunkFrames: Int = 48000,
    val blockFrames: Int = 4800,
    val queueCapacity: Int = 30,
    val maxStorageBytes: Long = 134_217_728L,
    val maxStorageFiles: Int = 1500,
    val minFreeSpaceBytes: Long = 52_428_800L,
    val stopTimeoutMillis: Long = 2000L,
) {
    init {
        require(sampleRate > 0 && chunkFrames > 0 && blockFrames > 0 && queueCapacity > 0)
        require(maxStorageBytes > 0 && maxStorageFiles > 0 && minFreeSpaceBytes >= 0)
        require(stopTimeoutMillis > 0)
    }

    val frameBytes: Int get() = 2
    val blockBytes: Int get() = Math.multiplyExact(blockFrames, frameBytes)
    val maxQueueBytes: Int get() = Math.multiplyExact(blockBytes, queueCapacity)
}

/**
 * One worker owns timeline transitions, PCM publication and retention. Capture only fills a bounded pool.
 * Blocking commands are internal: the public recorder dispatches them off Main. Snapshots outlive this owner.
 */
internal class PcmStorageCoordinator(
    private val directory: File,
    private val limits: RecordingLimits,
    private val config: PcmCoordinatorConfig = PcmCoordinatorConfig(),
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val nowNanos: () -> Long = { System.nanoTime() },
    private val retireWhenUnused: Boolean = false,
    private val onInputReleased: () -> Unit = {},
    private val onStateChanged: (PcmCoordinatorState, PcmCoordinatorFailure?) -> Unit = { _, _ -> },
) : AutoCloseable {
    private val commands = ArrayBlockingQueue<FutureTask<*>>(32)
    private val wake = java.lang.Object()
    private val pendingFailure = AtomicReference<PcmCoordinatorFailure?>(null)
    private var source: AudioSource? = null
    @Volatile private var queue: PcmBlockQueue? = null
    @Volatile private var history: PcmHistory? = null
    private var timeline: ScanAudioTimeline? = null
    private var captureThread: Thread? = null
    @Volatile private var captureFinished = true
    @Volatile private var stopFinished = true
    private var stoppingSource = false
    private val stopFailed = AtomicBoolean(false)
    private var closeRequested = false
    private var lastProcessingMillis = 0L
    @Volatile private var terminated = false
    @Volatile private var status = Status(PcmCoordinatorState.IDLE)
    private val owner = Thread(::runOwner, "pcm-session-owner").apply { isDaemon = true; start() }

    val currentState: PcmCoordinatorState get() = status.state
    val failureReason: PcmCoordinatorFailure? get() = status.failure
    val committedEndFrame: Long get() = history?.committedEnd ?: 0L
    val retainedBytes: Long get() = history?.bytes ?: 0L
    val retainedFiles: Int get() = history?.files ?: 0
    val isReleased: Boolean get() = terminated
    val hasActiveInput: Boolean get() = !captureFinished || !stopFinished

    fun start(audioSource: AudioSource): Boolean = command {
        check(currentState == PcmCoordinatorState.IDLE)
        source = audioSource
        publish(PcmCoordinatorState.STARTING)
        try {
            if (!audioSource.start()) {
                failStartup()
                return@command false
            }
            val clock = PcmClock(config.sampleRate, audioSource.timestampNanos() ?: nowNanos())
            history = PcmHistory(directory, config.chunkFrames, config.maxStorageBytes, config.maxStorageFiles,
                onReleased = { directory.delete(); Unit })
            queue = PcmBlockQueue(config.blockBytes, config.queueCapacity, config.maxQueueBytes)
            timeline = ScanAudioTimeline(clock, limits)
            publish(PcmCoordinatorState.RUNNING)
            captureFinished = false
            captureThread = Thread({ runCapture(audioSource, checkNotNull(queue)) }, "pcm-capture-worker")
                .apply { isDaemon = true; start() }
            true
        } catch (_: Exception) {
            failStartup()
            false
        }
    }

    fun acquireScan(scanMillis: Long = nowMillis(), limits: RecordingLimits = this.limits): AudioContextId? = command {
        if (currentState != PcmCoordinatorState.RUNNING) return@command null
        val h = checkNotNull(history)
        val t = checkNotNull(timeline)
        t.updateLimits(limits)
        t.acquireScan(scanMillis, h.contiguousTailStart)
    }

    fun openEditor(id: AudioContextId, openMillis: Long = nowMillis()): AudioTimelineView? = command {
        timeline?.openEditor(id, committedEndFrame, processingTime(openMillis), directory.usableSpace >= config.minFreeSpaceBytes)
    }

    fun refresh(id: AudioContextId, refreshMillis: Long = nowMillis()): AudioTimelineView? = command {
        timeline?.refresh(id, committedEndFrame, processingTime(refreshMillis))
    }

    fun snapshot(range: AudioRange): PcmHistory.Snapshot = command {
        checkNotNull(history) { "History is not initialized" }.snapshot(range)
    }

    fun releaseContext(id: AudioContextId) = command {
        timeline?.releaseContext(id)
        retain(committedEndFrame)
    }

    fun stop(stopMillis: Long? = null) = command {
        if (closeRequested) return@command
        if (currentState != PcmCoordinatorState.RUNNING && currentState != PcmCoordinatorState.FAILED) return@command
        if (currentState == PcmCoordinatorState.RUNNING) publish(PcmCoordinatorState.STOPPING)
        stopInput()
        drain()
        timeline?.stop(processingTime(stopMillis), endReason())
        retain(committedEndFrame)
        if (failureReason == null) publish(PcmCoordinatorState.STOPPED)
    }

    override fun close() {
        if (terminated) return
        command {
            if (terminated) return@command
            if (!closeRequested) {
                stop()
                queue?.close()
                history?.close()
                timeline = null
                closeRequested = true
            }
            finishCloseIfReady()
        }
    }

    private fun runCapture(audioSource: AudioSource, pcmQueue: PcmBlockQueue) {
        val buffer = ByteArray(config.blockBytes)
        var frame = 0L
        try {
            while (currentState == PcmCoordinatorState.RUNNING && pendingFailure.get() == null) {
                val count = audioSource.read(buffer, 0, buffer.size)
                if (currentState != PcmCoordinatorState.RUNNING) break
                if (count < 0) {
                    requestFailure(PcmCoordinatorFailure.SOURCE_READ_FAILED)
                    break
                }
                if (count == 0) continue
                if (count > buffer.size || count % config.frameBytes != 0) {
                    requestFailure(PcmCoordinatorFailure.QUEUE_DISCONTINUITY)
                    break
                }
                if (!pcmQueue.offer(frame, buffer, count)) {
                    if (currentState == PcmCoordinatorState.RUNNING) {
                        requestFailure(if (pcmQueue.failure == PcmQueueFailure.DISCONTINUITY)
                            PcmCoordinatorFailure.QUEUE_DISCONTINUITY else PcmCoordinatorFailure.QUEUE_OVERFLOW)
                    }
                    break
                }
                frame = Math.addExact(frame, count / config.frameBytes.toLong())
                signal()
            }
        } catch (_: Exception) {
            if (currentState == PcmCoordinatorState.RUNNING) requestFailure(PcmCoordinatorFailure.SOURCE_READ_FAILED)
        } finally {
            pcmQueue.seal()
            captureFinished = true
            signal()
        }
    }

    private fun runOwner() {
        while (!terminated) {
            pendingFailure.getAndSet(null)?.let(::fail)
            val task = commands.poll()
            task?.run()
            releaseInputIfReady()
            retireIfUnused()
            finishCloseIfReady()
            if (terminated) break
            val wrote = writeNext()
            synchronized(wake) {
                if (task == null && !wrote && commands.isEmpty() && pendingFailure.get() == null &&
                    queue?.hasPending != true && !inputReadyToRelease() && !terminated) wake.wait()
            }
        }
        while (true) (commands.poll() ?: break).run()
    }

    private fun writeNext(): Boolean {
        val block = queue?.poll() ?: return false
        block.use {
            val h = checkNotNull(history)
            try {
                if (directory.usableSpace < config.minFreeSpaceBytes) throw HistoryQuotaException()
                check(block.firstFrame == h.committedEnd) { "PCM publication is not contiguous" }
                retain(Math.addExact(h.committedEnd, block.count / config.frameBytes.toLong()))
                h.append(block.bytes, block.count)
            } catch (_: HistoryQuotaException) {
                fail(PcmCoordinatorFailure.STORAGE_QUOTA_EXCEEDED)
                queue?.close()
            } catch (_: IOException) {
                fail(PcmCoordinatorFailure.STORAGE_IO_ERROR)
                queue?.close()
            }
        }
        return true
    }

    private fun drain() { while (writeNext()) { /* Drain the accepted bounded queue. */ } }

    private fun retain(end: Long) {
        val t = timeline ?: return
        history?.retain(t.retainedRanges(end, processingTime()))
    }

    private fun stopInput() {
        queue?.seal()
        val input = source
        if (input != null && !stoppingSource) {
            stoppingSource = true
            stopFinished = false
            Thread({
                try { input.stop() }
                catch (_: Exception) { stopFailed.set(true) }
                finally { stopFinished = true; signal() }
            }, "pcm-source-stop").apply { isDaemon = true; start() }
        }
        val deadline = System.nanoTime() + config.stopTimeoutMillis * 1_000_000L
        synchronized(wake) {
            while (!captureFinished || !stopFinished) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) break
                wake.wait(remaining / 1_000_000L, (remaining % 1_000_000L).toInt())
            }
        }
        if (!captureFinished || !stopFinished || stopFailed.get()) {
            publish(PcmCoordinatorState.FAILED, PcmCoordinatorFailure.SOURCE_STOP_FAILED)
        }
        releaseInputIfReady()
        pendingFailure.getAndSet(null)?.let { reason ->
            if (failureReason == null) publish(PcmCoordinatorState.FAILED, reason)
        }
    }

    private fun inputReadyToRelease() = stoppingSource && source != null && captureFinished && stopFinished

    private fun releaseInputIfReady() {
        if (!inputReadyToRelease()) return
        runCatching { source?.close() }
        source = null
        onInputReleased()
    }

    private fun finishCloseIfReady() {
        if (!closeRequested || !captureFinished || !stopFinished || terminated) return
        releaseInputIfReady()
        publish(PcmCoordinatorState.CLOSED)
        synchronized(wake) { terminated = true; wake.notifyAll() }
    }

    /** Facade-owned terminal sessions need no ring once their final scan is released; leases remain independent. */
    private fun retireIfUnused() {
        if (!retireWhenUnused || closeRequested || timeline?.hasContext == true) return
        if (currentState != PcmCoordinatorState.STOPPED && currentState != PcmCoordinatorState.FAILED) return
        queue?.close()
        queue = null
        history?.close()
        history = null
        timeline = null
        closeRequested = true
    }

    private fun requestFailure(reason: PcmCoordinatorFailure) {
        pendingFailure.compareAndSet(null, reason)
        queue?.seal()
        signal()
    }

    private fun fail(reason: PcmCoordinatorFailure) {
        if (currentState != PcmCoordinatorState.RUNNING && currentState != PcmCoordinatorState.STOPPING) return
        publish(PcmCoordinatorState.FAILED, reason)
        stopInput()
        timeline?.stop(processingTime(), endReason())
    }

    private fun failStartup() {
        captureFinished = true
        queue?.close()
        history?.close()
        runCatching { source?.close() }
        source = null
        publish(PcmCoordinatorState.FAILED, PcmCoordinatorFailure.SOURCE_START_FAILED)
    }

    private fun endReason() = if (failureReason == PcmCoordinatorFailure.STORAGE_QUOTA_EXCEEDED)
        RetentionEnd.RESOURCE_LIMIT else RetentionEnd.CAPTURE_STOPPED

    // Event timestamps remain source data; only the owner advances processing time.
    private fun processingTime(eventMillis: Long? = null): Long {
        lastProcessingMillis = maxOf(lastProcessingMillis, nowMillis(), eventMillis ?: 0L)
        return lastProcessingMillis
    }

    private fun publish(state: PcmCoordinatorState, failure: PcmCoordinatorFailure? = status.failure) {
        status = Status(state, failure)
        onStateChanged(state, failure)
    }

    private fun signal() { synchronized(wake) { wake.notifyAll() } }

    private fun <T> command(action: () -> T): T {
        if (Thread.currentThread() === owner) return action()
        val task = FutureTask(action)
        synchronized(wake) {
            // Retired state is immutable: commands can only return their closed result or reject it.
            if (terminated) return action()
            check(commands.offer(task)) { "Audio session command limit reached" }
            wake.notifyAll()
        }
        return try { task.get() } catch (error: ExecutionException) { throw checkNotNull(error.cause) }
    }

    private data class Status(val state: PcmCoordinatorState, val failure: PcmCoordinatorFailure? = null)

    companion object {
        fun cleanAbandonedFiles(directory: File): Int = directory.listFiles()?.count { file ->
            file.isFile && (file.name.endsWith(".pcm") || file.name.endsWith(".tmp")) && file.delete()
        } ?: 0
    }
}
