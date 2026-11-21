package com.vpr.screenlate.core.anki.audio

import android.content.Context
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.common.redacted
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Owns pronunciation preparation, output and temporary files; shares output ownership with preview. */
@Singleton
class AudioPlayer internal constructor(
    private val directory: File,
    private val settings: AudioSettingsRepository,
    private val playback: PlaybackCoordinator,
    private val speech: SpeechOutput,
    private val outputFactory: () -> FileAudioOutput,
    private val main: CoroutineDispatcher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    constructor(@ApplicationContext context: Context, settings: AudioSettingsRepository, playback: PlaybackCoordinator) :
        this(File(context.cacheDir, "tts_playback"), settings, playback, AndroidSpeechOutput(context),
            ::AndroidFileAudioOutput, Dispatchers.Main.immediate)

    private val scope = CoroutineScope(SupervisorJob() + main)
    private val cleanup = Mutex()
    private var cleaned = false
    private var active: Operation? = null

    /** False if preparation, output ownership or private synthesis could not be submitted. */
    suspend fun play(pronunciation: Pronunciation, canStart: () -> Boolean = { true }): Boolean = withContext(main) {
        val op = Operation(canStart)
        op.lease = playback.acquire { stopOperation(op) } ?: return@withContext false
        active = op
        var submitted = false
        try {
            val work = scope.async { prepareAndPlay(op, pronunciation) }
            op.job = work
            submitted = work.await()
            submitted
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            false // Another Screenlate output cancelled this operation, not its caller.
        } catch (error: Exception) {
            Log.w(TAG, "Cannot prepare pronunciation", error.redacted())
            false
        } finally {
            if (!submitted && !op.stopping) withContext(NonCancellable + main) { stopOperation(op) }
        }
    }

    suspend fun stop(): Boolean = withContext(NonCancellable + main) { active?.let { stopOperation(it) } ?: true }

    private suspend fun prepareAndPlay(op: Operation, pronunciation: Pronunciation): Boolean {
        cleanup.withLock {
            if (!cleaned) withContext(io) {
                if (!directory.isDirectory && !directory.mkdirs()) error("Cannot create pronunciation directory")
                val files = checkNotNull(directory.listFiles()) { "Cannot read pronunciation directory" }
                files.filter { it.isFile && it.name.startsWith("tts_") && it.extension == "wav" }
                    .forEach { check(!it.exists() || it.delete()) { "Cannot clean abandoned pronunciation" } }
                cleaned = true
            }
        }
        val volume = settings.volume().coerceIn(0, 100) / 100f
        if (!op.canPlay()) return false
        if (pronunciation is Pronunciation.Clip) return playFile(op, pronunciation.clip.file, volume)
        pronunciation as Pronunciation.Speech
        op.speechPending = true
        val prepared = withTimeoutOrNull(5000) { speech.prepare(pronunciation.language) { op.isCurrent() } } == true
        if (!prepared || !op.canPlay()) return false
        if (playback.requiresPrivateSpeech) {
            withContext(io) {
                synchronized(op) {
                    if (!op.retired) op.file = File.createTempFile("tts_", ".wav", directory)
                }
            }
            val file = op.file ?: return false
            val complete = withTimeoutOrNull(5000) { speech.synthesize(pronunciation.text, file) { op.isCurrent() } } == true
            if (!complete || !op.canPlay() || !withContext(io) { file.isFile && file.length() > 0 }) return false
            op.speechPending = false
            return playFile(op, file, volume)
        }
        // Recheck in the native lane too: capture may begin after preparation.
        return speech.speak(pronunciation.text, volume,
            { op.isCurrent() && !playback.requiresPrivateSpeech }, { finish(op) })
    }

    private fun playFile(op: Operation, file: File, volume: Float): Boolean {
        if (!op.canPlay()) return false
        val output = outputFactory().also { op.output = it }
        output.prepare(file, volume, ready = {
            if (op.canPlay()) {
                try { output.start() } catch (_: Exception) { finish(op) }
            } else finish(op)
        }, finished = { finish(op) })
        return true
    }

    private fun finish(op: Operation) {
        if (!op.finishQueued.compareAndSet(false, true)) return
        scope.launch { if (active === op && !op.stopping) stopOperation(op, cancelPreparation = false) }
    }

    private suspend fun stopOperation(op: Operation, cancelPreparation: Boolean = true): Boolean = withContext(NonCancellable + main) {
        op.stopping = true
        op.retired = true
        if (cancelPreparation) op.job?.cancel()
        op.close.withLock {
            if (op.released) return@withLock true
            if (op.speechPending && !speech.stop()) return@withLock false
            op.speechPending = false
            try { op.output?.close() } catch (_: Exception) { return@withLock false }
            op.output = null
            val removed = withContext(io) {
                synchronized(op) { op.file?.let { !it.exists() || it.delete() } ?: true }
            }
            if (!removed) return@withLock false
            op.file = null
            op.released = true
            op.lease?.close()
            if (active === op) active = null
            true
        }
    }

    private class Operation(val canStart: () -> Boolean) {
        var lease: PlaybackCoordinator.Lease? = null
        var job: Job? = null
        var output: FileAudioOutput? = null
        @Volatile var file: File? = null
        @Volatile var retired = false
        var stopping = false
        var released = false
        var speechPending = false
        val close = Mutex()
        val finishQueued = java.util.concurrent.atomic.AtomicBoolean()
        fun isCurrent() = !retired && lease?.isCurrent == true
        fun canPlay() = isCurrent() && canStart()
    }

    private companion object { const val TAG = "AudioPlayer" }
}
