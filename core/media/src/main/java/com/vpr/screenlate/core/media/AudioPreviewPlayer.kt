package com.vpr.screenlate.core.media

import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class PreviewMode {
    RESULT,
    ORIGINAL
}

interface AudioSink : AutoCloseable {
    fun write(buffer: ByteArray, offset: Int, size: Int): Int
    fun play()
    fun pause()
    fun stop()
    fun flush()
}

/**
 * Preview playback for sentence recording editor.
 * Streams kept PCM frames directly to an [AudioSink] with sample accuracy,
 * supporting Result mode (skips exclusions) and Original mode (uninterrupted).
 */
internal class AudioPreviewPlayer(
    private val snapshotProvider: (AudioRange) -> PcmHistory.Snapshot?,
    private val sampleRate: Int = 48000,
    private val sinkFactory: () -> AudioSink = { AndroidAudioTrackSink(sampleRate) },
) : AutoCloseable {

    private val lock = ReentrantLock()
    private var sink: AudioSink? = null
    private var playThread: Thread? = null
    private val _isPlaying = AtomicBoolean(false)
    private val _currentFrame = AtomicLong(0L)
    private val stopRequested = AtomicBoolean(false)

    private var activeEdit: AudioEdit? = null
    private var activeMode: PreviewMode = PreviewMode.RESULT
    private var activeOnProgress: ((Long) -> Unit)? = null
    private var activeOnComplete: (() -> Unit)? = null

    val isPlaying: Boolean get() = _isPlaying.get()
    val currentFrame: Long get() = _currentFrame.get()

    /**
     * Starts or resumes playback of [edit].
     *
     * @param startFrame starting frame in source coordinates. If null, starts from current frame or selection start.
     */
    fun play(
        edit: AudioEdit,
        mode: PreviewMode = PreviewMode.RESULT,
        startFrame: Long? = null,
        onProgress: ((Long) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        lock.withLock {
            activeEdit = edit
            activeMode = mode
            activeOnProgress = onProgress
            activeOnComplete = onComplete

            stopPlaybackLocked()

            val effectiveStart = resolveStartFrame(edit, mode, startFrame ?: _currentFrame.get())
            if (effectiveStart >= edit.selection.endExclusive) {
                _currentFrame.set(edit.selection.start)
                onProgress?.invoke(edit.selection.start)
                return
            }

            _currentFrame.set(effectiveStart)
            _isPlaying.set(true)
            stopRequested.set(false)

            val currentSink = sinkFactory().also { sink = it }
            currentSink.play()

            val rangesToPlay = if (mode == PreviewMode.RESULT) {
                edit.kept.mapNotNull { it.intersect(AudioRange(effectiveStart, edit.selection.endExclusive)) }
            } else {
                listOf(AudioRange(effectiveStart, edit.selection.endExclusive))
            }

            playThread = Thread({
                runPlaybackLoop(currentSink, rangesToPlay, onProgress, onComplete)
            }, "audio-preview-player").apply { start() }
        }
    }

    fun pause() {
        lock.withLock {
            stopPlaybackLocked()
        }
    }

    fun stop() {
        lock.withLock {
            stopPlaybackLocked()
        }
    }

    fun seek(frame: Long, edit: AudioEdit? = null, mode: PreviewMode = PreviewMode.RESULT) {
        lock.withLock {
            val wasPlaying = isPlaying
            val effectiveEdit = edit ?: activeEdit
            val effectiveMode = if (edit != null) mode else activeMode
            val target = if (effectiveEdit != null) resolveStartFrame(effectiveEdit, effectiveMode, frame) else frame
            _currentFrame.set(target)
            if (wasPlaying && effectiveEdit != null) {
                play(effectiveEdit, effectiveMode, target, activeOnProgress, activeOnComplete)
            } else {
                stopPlaybackLocked()
            }
        }
    }

    override fun close() {
        lock.withLock {
            stopPlaybackLocked()
        }
    }

    private fun stopPlaybackLocked() {
        stopRequested.set(true)
        _isPlaying.set(false)
        playThread?.let {
            if (Thread.currentThread() != it) {
                runCatching { it.join(500) }
            }
        }
        playThread = null
        sink?.let {
            runCatching { it.stop() }
            runCatching { it.close() }
        }
        sink = null
    }

    private fun resolveStartFrame(edit: AudioEdit, mode: PreviewMode, requestFrame: Long): Long {
        val clamped = requestFrame.coerceIn(edit.selection.start, edit.selection.endExclusive)
        if (mode == PreviewMode.ORIGINAL) return clamped
        for (cut in edit.exclusions) {
            if (clamped in cut.start until cut.endExclusive) {
                return cut.endExclusive.coerceAtMost(edit.selection.endExclusive)
            }
        }
        return clamped
    }

    private fun runPlaybackLoop(
        currentSink: AudioSink,
        ranges: List<AudioRange>,
        onProgress: ((Long) -> Unit)?,
        onComplete: (() -> Unit)?,
    ) {
        val buffer = ByteArray(4096)
        var completedNormally = true

        try {
            for (range in ranges) {
                if (stopRequested.get()) {
                    completedNormally = false
                    break
                }

                val snapshot = snapshotProvider(range)
                if (snapshot == null) {
                    completedNormally = false
                    break
                }

                snapshot.use { snap ->
                    snap.openStream().use { stream ->
                        var framesRead = 0L
                        val totalFrames = range.frames
                        var lastProgressReport = System.currentTimeMillis()

                        while (framesRead < totalFrames && !stopRequested.get()) {
                            val toRead = minOf(buffer.size.toLong(), (totalFrames - framesRead) * 2).toInt()
                            val count = stream.read(buffer, 0, toRead)
                            if (count <= 0) break

                            var written = 0
                            while (written < count && !stopRequested.get()) {
                                val w = currentSink.write(buffer, written, count - written)
                                if (w <= 0) break
                                written += w
                            }

                            val frames = count / 2
                            framesRead += frames
                            val currentPos = range.start + framesRead
                            _currentFrame.set(currentPos)

                            val now = System.currentTimeMillis()
                            if (now - lastProgressReport >= 50L) {
                                lastProgressReport = now
                                onProgress?.invoke(currentPos)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            completedNormally = false
        } finally {
            _isPlaying.set(false)
            if (completedNormally && !stopRequested.get()) {
                onProgress?.invoke(_currentFrame.get())
                onComplete?.invoke()
            }
        }
    }
}
