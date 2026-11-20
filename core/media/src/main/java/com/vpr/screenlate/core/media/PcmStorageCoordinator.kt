package com.vpr.screenlate.core.media

import java.io.File
import java.io.IOException
import java.util.UUID

enum class PcmCoordinatorFailure {
    SOURCE_START_FAILED,
    SOURCE_READ_FAILED,
    QUEUE_OVERFLOW,
    QUEUE_DISCONTINUITY,
    STORAGE_QUOTA_EXCEEDED,
    STORAGE_IO_ERROR,
}

enum class PcmCoordinatorState {
    IDLE,
    RUNNING,
    STOPPED,
    FAILED,
    CLOSED,
}

internal class PcmCoordinatorConfig(
    val sampleRate: Int = 48000,
    val chunkFrames: Int = 48000, // 1 second of frames per file chunk
    val blockFrames: Int = 4800,  // 100 ms per memory block
    val queueCapacity: Int = 30,  // 3 seconds in memory queue
    val maxStorageBytes: Long = 134_217_728L, // 128 MiB
    val maxStorageFiles: Int = 1500,
    val minFreeSpaceBytes: Long = 52_428_800L, // 50 MiB safety reserve
) {
    init {
        require(sampleRate > 0 && chunkFrames > 0 && blockFrames > 0 && queueCapacity > 0)
        require(maxStorageBytes > 0 && maxStorageFiles > 0 && minFreeSpaceBytes >= 0)
    }

    val frameBytes: Int get() = 2 // Mono PCM16
    val blockBytes: Int get() = blockFrames * frameBytes
    val maxQueueBytes: Int get() = blockBytes * queueCapacity
}

/**
 * Coordinates live PCM audio capture from [AudioSource], passing frames through
 * a bounded preallocated [PcmBlockQueue] to [PcmHistory], governed by [ScanAudioTimeline].
 *
 * All time parameters default to system monotonic time (elapsedRealtime).
 */
internal class PcmStorageCoordinator(
    private val directory: File,
    private val limits: RecordingLimits,
    private val config: PcmCoordinatorConfig = PcmCoordinatorConfig(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val nowNanos: () -> Long = { System.nanoTime() },
) : AutoCloseable {

    private var source: AudioSource? = null
    private var queue: PcmBlockQueue? = null
    private var history: PcmHistory? = null
    private var timeline: ScanAudioTimeline? = null
    private var clock: PcmClock? = null

    private var captureThread: Thread? = null
    private var writerThread: Thread? = null

    @Volatile
    private var state = PcmCoordinatorState.IDLE

    @Volatile
    private var failure: PcmCoordinatorFailure? = null

    val currentState: PcmCoordinatorState get() = state
    val failureReason: PcmCoordinatorFailure? get() = failure
    val committedEndFrame: Long get() = history?.committedEnd ?: 0L
    val retainedBytes: Long get() = history?.bytes ?: 0L
    val retainedFiles: Int get() = history?.files ?: 0

    @Synchronized
    fun start(audioSource: AudioSource): Boolean {
        check(state == PcmCoordinatorState.IDLE) { "Coordinator cannot be started from state $state" }
        cleanAbandonedFiles(directory)

        if (!audioSource.start()) {
            failure = PcmCoordinatorFailure.SOURCE_START_FAILED
            state = pcmCoordinatorFailureState(failure!!)
            return false
        }

        val origin = audioSource.timestampNanos() ?: nowNanos()
        val pcmClock = PcmClock(config.sampleRate, origin)
        val pcmHistory = PcmHistory(directory, config.chunkFrames, config.maxStorageBytes, config.maxStorageFiles)
        val pcmQueue = PcmBlockQueue(config.blockBytes, config.queueCapacity, config.maxQueueBytes)
        val audioTimeline = ScanAudioTimeline(pcmClock, limits)

        source = audioSource
        clock = pcmClock
        history = pcmHistory
        queue = pcmQueue
        timeline = audioTimeline
        state = PcmCoordinatorState.RUNNING

        val capture = Thread({ runCapture(audioSource, pcmQueue) }, "pcm-capture-worker")
        val writer = Thread({ runWriter(pcmQueue, pcmHistory, audioTimeline) }, "pcm-writer-worker")

        captureThread = capture
        writerThread = writer

        capture.start()
        writer.start()
        return true
    }

    @Synchronized
    fun acquireScan(scanMillis: Long = nowMillis()): AudioContextId? {
        if (state != PcmCoordinatorState.RUNNING) return null
        val t = timeline ?: return null
        val h = history ?: return null
        val earliestAvailable = earliestAvailableFrame(h)
        return t.acquireScan(scanMillis, earliestAvailable)
    }

    @Synchronized
    fun openEditor(id: AudioContextId, openMillis: Long = nowMillis()): AudioTimelineView? {
        val t = timeline ?: return null
        val h = history ?: return null
        val capacityOk = directory.usableSpace >= config.minFreeSpaceBytes
        return t.openEditor(id, h.committedEnd, openMillis, capacityOk)
    }

    @Synchronized
    fun refresh(id: AudioContextId, refreshMillis: Long = nowMillis()): AudioTimelineView? {
        val t = timeline ?: return null
        val h = history ?: return null
        return t.refresh(id, h.committedEnd, refreshMillis)
    }

    @Synchronized
    fun snapshot(range: AudioRange): PcmHistory.Snapshot {
        val h = checkNotNull(history) { "History is not initialized" }
        return h.snapshot(range)
    }

    @Synchronized
    fun releaseContext(id: AudioContextId) {
        val t = timeline ?: return
        val h = history ?: return
        t.releaseContext(id)
        if (state == PcmCoordinatorState.STOPPED) {
            h.retain(t.retainedRanges(h.committedEnd, nowMillis()))
        }
    }

    @Synchronized
    fun stop(stopMillis: Long? = null) {
        if (state != PcmCoordinatorState.RUNNING && state != PcmCoordinatorState.FAILED) return
        val targetState = if (state == PcmCoordinatorState.FAILED) PcmCoordinatorState.FAILED else PcmCoordinatorState.STOPPED
        state = targetState

        // Stop source and seal queue so writer drains accepted prefix
        runCatching { source?.stop() }
        queue?.seal()

        // Wait for writer to finish writing accepted prefix
        writerThread?.let {
            if (Thread.currentThread() != it) {
                runCatching { it.join(5000) }
            }
        }
        captureThread?.let {
            if (Thread.currentThread() != it) {
                runCatching { it.join(2000) }
            }
        }

        val effectiveStopMillis = if (stopMillis != null) maxOf(stopMillis, nowMillis()) else nowMillis()
        timeline?.stop(effectiveStopMillis)
        history?.let { h ->
            timeline?.let { t ->
                h.retain(t.retainedRanges(h.committedEnd, effectiveStopMillis))
            }
        }
    }

    @Synchronized
    override fun close() {
        if (state == PcmCoordinatorState.CLOSED) return
        stop()
        state = PcmCoordinatorState.CLOSED
        runCatching { queue?.close() }
        runCatching { history?.close() }
        runCatching { source?.close() }
        queue = null
        history = null
        source = null
        timeline = null
        clock = null
    }

    private fun runCapture(audioSource: AudioSource, pcmQueue: PcmBlockQueue) {
        val buffer = ByteArray(config.blockBytes)
        var currentFrame = 0L

        while (state == PcmCoordinatorState.RUNNING) {
            val bytesRead = audioSource.read(buffer, 0, buffer.size)
            if (bytesRead < 0) {
                if (state == PcmCoordinatorState.RUNNING) {
                    fail(PcmCoordinatorFailure.SOURCE_READ_FAILED)
                }
                break
            }
            if (bytesRead == 0) continue

            // Ensure frame-aligned bytes
            val validBytes = bytesRead - (bytesRead % config.frameBytes)
            if (validBytes <= 0) continue

            val frames = validBytes / config.frameBytes
            val offered = pcmQueue.offer(currentFrame, buffer, validBytes)
            if (!offered) {
                val qFailure = pcmQueue.failure
                val reason = when (qFailure) {
                    PcmQueueFailure.DISCONTINUITY -> PcmCoordinatorFailure.QUEUE_DISCONTINUITY
                    PcmQueueFailure.OVERFLOW, null -> PcmCoordinatorFailure.QUEUE_OVERFLOW
                }
                fail(reason)
                break
            }
            currentFrame = Math.addExact(currentFrame, frames.toLong())
        }
        pcmQueue.seal()
    }

    private fun runWriter(
        pcmQueue: PcmBlockQueue,
        pcmHistory: PcmHistory,
        audioTimeline: ScanAudioTimeline,
    ) {
        while (!pcmQueue.isDrained) {
            val block = pcmQueue.poll(50L)
            if (block == null) {
                if (pcmQueue.isDrained) break
                continue
            }

            block.use { b ->
                val upcomingEnd = Math.addExact(pcmHistory.committedEnd, (b.count / config.frameBytes).toLong())
                val now = nowMillis()

                // Check disk space safety reserve before appending
                if (directory.usableSpace < config.minFreeSpaceBytes) {
                    fail(PcmCoordinatorFailure.STORAGE_QUOTA_EXCEEDED)
                    pcmQueue.seal()
                    return
                }

                try {
                    pcmHistory.retain(audioTimeline.retainedRanges(upcomingEnd, now))
                    pcmHistory.append(b.bytes, b.count)
                } catch (quota: HistoryQuotaException) {
                    fail(PcmCoordinatorFailure.STORAGE_QUOTA_EXCEEDED)
                    pcmQueue.seal()
                    return
                } catch (io: IOException) {
                    fail(PcmCoordinatorFailure.STORAGE_IO_ERROR)
                    pcmQueue.seal()
                    return
                }
            }
        }
    }

    private fun fail(reason: PcmCoordinatorFailure) {
        synchronized(this) {
            if (state == PcmCoordinatorState.RUNNING) {
                failure = reason
                state = PcmCoordinatorState.FAILED
                runCatching { source?.stop() }
                queue?.seal()
                val endReason = if (reason == PcmCoordinatorFailure.STORAGE_QUOTA_EXCEEDED) {
                    RetentionEnd.RESOURCE_LIMIT
                } else {
                    RetentionEnd.CAPTURE_STOPPED
                }
                timeline?.stop(nowMillis(), endReason)
            }
        }
    }

    private fun earliestAvailableFrame(h: PcmHistory): Long {
        val range = AudioRange(0, h.committedEnd)
        return try {
            h.snapshot(AudioRange(h.committedEnd - 1, h.committedEnd)).use {
                maxOf(0L, h.committedEnd - (h.bytes / config.frameBytes))
            }
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        fun cleanAbandonedFiles(directory: File): Int {
            if (!directory.exists() || !directory.isDirectory) return 0
            var count = 0
            directory.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".pcm") || file.name.endsWith(".tmp"))) {
                    if (file.delete()) count++
                }
            }
            return count
        }

        private fun pcmCoordinatorFailureState(reason: PcmCoordinatorFailure): PcmCoordinatorState =
            PcmCoordinatorState.FAILED
    }
}
