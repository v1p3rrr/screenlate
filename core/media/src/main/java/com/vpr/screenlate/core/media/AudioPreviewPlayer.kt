package com.vpr.screenlate.core.media

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class PreviewMode { RESULT, ORIGINAL }

enum class PreviewFailure { SOURCE_UNAVAILABLE, OUTPUT_FAILED, OUTPUT_STALLED, OUTPUT_BUSY }

/** A single mono PCM16 output. Cancellation may interrupt writes and position waits from another thread. */
interface AudioSink : AutoCloseable {
    val playedFrames: Long
    fun write(buffer: ByteArray, offset: Int, size: Int): Int
    fun awaitProgress(timeoutMillis: Long)
    /** Queue bounded output-only tail padding if needed to start/drain a short native stream. */
    fun finishInput(): Boolean
    fun play()
    fun pause()
    fun stop()
    fun flush()
}

/**
 * Owns one playback generation and at most one retiring run. Every run owns its sink, cancellation and leases.
 * Commands may wait briefly for retirement and must run off Main. Callbacks are checked again on delivery.
 */
internal class AudioPreviewPlayer(
    private val snapshotProvider: (AudioRange) -> PcmHistory.Snapshot?,
    private val sampleRate: Int = 48000,
    private val sinkFactory: () -> AudioSink = { AndroidAudioTrackSink(sampleRate) },
    private val callbackExecutor: Executor = Executor { it.run() },
    private val stopTimeoutMillis: Long = 500,
    private val stallTimeoutMillis: Long = 2000,
    private val nowNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val commands = ReentrantLock()
    private val state = Any()
    private val generation = AtomicLong()
    private val playing = AtomicBoolean(false)
    private val position = AtomicLong()
    private val latestProgress = AtomicReference<Progress?>()
    private val progressScheduled = AtomicBoolean()
    private var active: Run? = null
    private var retiring: Run? = null
    private var lastRequest: Request? = null

    init { require(stopTimeoutMillis > 0 && stallTimeoutMillis > 0) }

    val isPlaying: Boolean get() = playing.get()
    val currentFrame: Long get() = position.get()
    val isReleased: Boolean get() = commands.withLock {
        synchronized(state) { active == null } && (retiring?.done?.count ?: 0L) == 0L
    }

    fun play(
        edit: AudioEdit,
        mode: PreviewMode = PreviewMode.RESULT,
        startFrame: Long? = null,
        onProgress: ((Long) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((PreviewFailure) -> Unit)? = null,
    ) = commands.withLock {
        val token = generation.incrementAndGet()
        val request = Request(edit, mode, onProgress, onComplete, onError)
        lastRequest = request
        if (!retire()) {
            deliver(token) { onError?.invoke(PreviewFailure.OUTPUT_BUSY) }
            return@withLock
        }
        val requested = startFrame ?: currentFrame.takeIf { it < edit.selection.endExclusive } ?: edit.selection.start
        val start = resolveStart(edit, mode, requested)
        position.set(start)
        if (start >= edit.selection.endExclusive || (mode == PreviewMode.RESULT && edit.outputFrames == 0L)) {
            deliver(token) { onProgress?.invoke(start); onComplete?.invoke() }
            return@withLock
        }
        val run = Run(token, request, start)
        synchronized(state) { active = run; playing.set(true) }
        Thread({ playRun(run) }, "audio-preview-player").apply { isDaemon = true; start() }
    }

    fun pause() = stop()

    fun stop() = commands.withLock {
        generation.incrementAndGet()
        retire()
        Unit
    }

    fun seek(frame: Long, edit: AudioEdit? = null, mode: PreviewMode = PreviewMode.RESULT) = commands.withLock {
        val request = lastRequest
        val effectiveEdit = edit ?: request?.edit
        val effectiveMode = if (edit != null) mode else request?.mode ?: mode
        val target = effectiveEdit?.let { resolveStart(it, effectiveMode, frame) } ?: frame
        if (isPlaying && effectiveEdit != null) {
            play(effectiveEdit, effectiveMode, target, request?.progress, request?.complete, request?.error)
        } else {
            generation.incrementAndGet()
            retire()
            position.set(target)
        }
    }

    override fun close() { stop() }

    private fun retire(): Boolean {
        val previous = synchronized(state) {
            active.also { run ->
                active = null
                playing.set(false)
                run?.cancelled?.set(true)
            }
        }
        val run = previous ?: retiring ?: return true
        retiring = run
        if (run.interruptSent.compareAndSet(false, true)) {
            Thread({
                run.sinkLock.withLock {
                    run.sink?.let { sink ->
                        runCatching { sink.pause() }
                        runCatching { sink.flush() }
                        runCatching { sink.stop() }
                    }
                }
            }, "audio-preview-stop").apply { isDaemon = true; start() }
        }
        if (!run.done.await(stopTimeoutMillis, TimeUnit.MILLISECONDS)) return false
        retiring = null
        return true
    }

    private fun playRun(run: Run) {
        var failure: PreviewFailure? = null
        try {
            val sink = sinkFactory()
            run.sinkLock.withLock {
                run.sink = sink
                if (!run.cancelled.get()) sink.play()
            }
            if (run.cancelled.get()) return
            val edit = run.request.edit
            val ranges = if (run.request.mode == PreviewMode.ORIGINAL) {
                listOf(AudioRange(run.start, edit.selection.endExclusive))
            } else edit.kept.mapNotNull { it.intersect(AudioRange(run.start, edit.selection.endExclusive)) }
            val buffer = ByteArray(4096)
            var writtenFrames = 0L
            var lastHead = 0L
            var lastAdvance = nowNanos()
            var lastReport = 0L

            fun updatePosition() {
                val head = sink.playedFrames.coerceIn(0, writtenFrames)
                val now = nowNanos()
                if (head > lastHead) { lastHead = head; lastAdvance = now }
                val sourceFrame = if (run.request.mode == PreviewMode.ORIGINAL) run.start + head
                    else edit.sourceFrame(edit.resultOffset(run.start) + head)
                synchronized(state) {
                    if (active === run && !run.cancelled.get()) position.set(sourceFrame)
                }
                if (now - lastReport >= 50_000_000L) {
                    lastReport = now
                    deliverProgress(run.token) { run.request.progress?.invoke(sourceFrame) }
                }
            }

            fun waitForOutput(draining: Boolean = false) {
                updatePosition()
                if (draining && lastHead >= writtenFrames) return
                if (nowNanos() - lastAdvance >= stallTimeoutMillis * 1_000_000L) {
                    throw PlaybackException(PreviewFailure.OUTPUT_STALLED)
                }
                sink.awaitProgress(50)
            }

            for (range in ranges) {
                if (run.cancelled.get()) return
                val snapshot = try { snapshotProvider(range) } catch (_: Exception) { null }
                    ?: throw PlaybackException(PreviewFailure.SOURCE_UNAVAILABLE)
                snapshot.use { lease ->
                    if (run.cancelled.get()) return
                    lease.openStream().use { stream ->
                        var remaining = range.frames * 2
                        while (remaining > 0 && !run.cancelled.get()) {
                            val count = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (count <= 0 || count % 2 != 0) throw PlaybackException(PreviewFailure.SOURCE_UNAVAILABLE)
                            var offset = 0
                            while (offset < count && !run.cancelled.get()) {
                                val accepted = sink.write(buffer, offset, count - offset)
                                if (accepted < 0 || accepted > count - offset || accepted % 2 != 0) {
                                    throw PlaybackException(PreviewFailure.OUTPUT_FAILED)
                                }
                                if (accepted == 0) {
                                    waitForOutput()
                                } else {
                                    offset += accepted
                                    writtenFrames += accepted / 2
                                    updatePosition()
                                }
                            }
                            remaining -= count
                        }
                    }
                }
            }
            var inputFinished = false
            while (!run.cancelled.get() && lastHead < writtenFrames) {
                if (!inputFinished) inputFinished = sink.finishInput()
                waitForOutput(draining = true)
            }
            if (!run.cancelled.get()) updatePosition()
        } catch (error: PlaybackException) {
            failure = error.reason
        } catch (_: Exception) {
            failure = PreviewFailure.OUTPUT_FAILED
        } finally {
            run.sinkLock.withLock {
                run.sink?.let { sink ->
                    runCatching { sink.stop() }
                    runCatching { sink.flush() }
                    if (runCatching { sink.close() }.isFailure && failure == null) failure = PreviewFailure.OUTPUT_FAILED
                }
                run.sink = null
            }
            synchronized(state) {
                if (active === run) { active = null; playing.set(false) }
            }
            run.done.countDown()
            if (!run.cancelled.get()) {
                val result = failure
                deliver(run.token) {
                    if (result == null) {
                        run.request.progress?.invoke(run.request.edit.selection.endExclusive)
                        run.request.complete?.invoke()
                    } else run.request.error?.invoke(result)
                }
            }
        }
    }

    private fun deliver(token: Long, callback: () -> Unit) {
        runCatching {
            callbackExecutor.execute {
                if (generation.get() == token) runCatching(callback)
            }
        }
    }

    private fun deliverProgress(token: Long, callback: () -> Unit) {
        if (generation.get() != token) return
        latestProgress.updateAndGet { old ->
            if (old == null || token >= old.token) Progress(token, callback) else old
        }
        scheduleProgress()
    }

    private fun scheduleProgress() {
        if (!progressScheduled.compareAndSet(false, true)) return
        try {
            callbackExecutor.execute {
                val progress = latestProgress.getAndSet(null)
                try {
                    if (progress != null && generation.get() == progress.token) runCatching(progress.callback)
                } finally {
                    progressScheduled.set(false)
                    if (latestProgress.get() != null) scheduleProgress()
                }
            }
        } catch (_: Exception) {
            latestProgress.set(null)
            progressScheduled.set(false)
        }
    }

    private data class Progress(val token: Long, val callback: () -> Unit)

    private fun resolveStart(edit: AudioEdit, mode: PreviewMode, frame: Long): Long {
        val clamped = frame.coerceIn(edit.selection.start, edit.selection.endExclusive)
        return if (mode == PreviewMode.ORIGINAL) clamped else edit.sourceFrame(edit.resultOffset(clamped))
    }

    private data class Request(
        val edit: AudioEdit, val mode: PreviewMode,
        val progress: ((Long) -> Unit)?, val complete: (() -> Unit)?, val error: ((PreviewFailure) -> Unit)?,
    )

    private class Run(val token: Long, val request: Request, val start: Long) {
        val cancelled = AtomicBoolean(false)
        val interruptSent = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val sinkLock = ReentrantLock()
        var sink: AudioSink? = null
    }

    private class PlaybackException(val reason: PreviewFailure) : Exception()
}
