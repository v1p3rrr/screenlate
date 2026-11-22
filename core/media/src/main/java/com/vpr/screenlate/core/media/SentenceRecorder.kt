package com.vpr.screenlate.core.media

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import com.vpr.screenlate.core.common.log.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SentenceRecordingState {
    data object Idle : SentenceRecordingState
    data object Starting : SentenceRecordingState
    data class Recording(val startTimeMillis: Long) : SentenceRecordingState
    data object Stopping : SentenceRecordingState
    data class Stopped(val state: PcmCoordinatorState) : SentenceRecordingState
    data class Failed(val failure: PcmCoordinatorFailure) : SentenceRecordingState
}

/** Owns recording sessions. File, platform and worker waits suspend callers instead of blocking Main. */
@Singleton
class SentenceRecorder internal constructor(
    private val storageDir: File,
    private val settingsRepository: RecordingSettingsRepository,
    private val nowMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val nowNanos: () -> Long = { SystemClock.elapsedRealtimeNanos() },
    private val previewCallbackExecutor: Executor = Executor { it.run() },
    private val playback: PlaybackCoordinator = PlaybackCoordinator(),
    private val previewSinkFactory: (() -> AudioSink)? = null,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settingsRepository: RecordingSettingsRepository,
        playback: PlaybackCoordinator,
    ) : this(File(context.noBackupFilesDir, "sentence_recording"), settingsRepository,
        previewCallbackExecutor = ContextCompat.getMainExecutor(context), playback = playback)

    private val lifecycle = Mutex()
    private val previewCommands = Mutex()
    private val previewRequest = AtomicLong()
    private val generation = AtomicLong()
    @Volatile private var coordinator: PcmStorageCoordinator? = null
    @Volatile private var currentSource: AudioSource? = null
    private var abandonedCleaned = false
    @Volatile private var previewPlayer: AudioPreviewPlayer? = null
    private var previewSession: PcmStorageCoordinator? = null
    private var previewLease: PlaybackCoordinator.Lease? = null
    private var previewStopped: (() -> Unit)? = null
    private val _recordingState = MutableStateFlow<SentenceRecordingState>(SentenceRecordingState.Idle)
    val recordingState: StateFlow<SentenceRecordingState> = _recordingState.asStateFlow()
    val isRecording: Boolean get() = recordingState.value is SentenceRecordingState.Recording
    val isCaptureActive: Boolean get() = coordinator?.hasActiveInput == true || when (recordingState.value) {
        SentenceRecordingState.Starting, is SentenceRecordingState.Recording, SentenceRecordingState.Stopping -> true
        else -> false
    }

    suspend fun prepareCapture(): PlaybackCoordinator.CaptureLease? = playback.beginCapture().also {
        if (it == null) _recordingState.value = SentenceRecordingState.Failed(PcmCoordinatorFailure.PLAYBACK_STOP_FAILED)
    }

    suspend fun start(source: AudioSource): Boolean {
        var attempt: PcmStorageCoordinator? = null
        var sourceReleased = false
        var captureLease: PlaybackCoordinator.CaptureLease? = null
        var captureTransferred = false
        try {
            return withContext(Dispatchers.IO) {
                lifecycle.withLock {
                    currentCoroutineContext().ensureActive()
                    if (isRecording) {
                        if (source !== currentSource) {
                            source.close()
                            sourceReleased = true
                        }
                        return@withLock true
                    }
                    stopPreview()
                    coordinator?.stop()
                    if (coordinator?.hasActiveInput == true) {
                        source.close()
                        sourceReleased = true
                        return@withLock false
                    }
                    coordinator?.close()
                    if (coordinator?.isReleased == false) {
                        source.close()
                        sourceReleased = true
                        return@withLock false
                    }
                    coordinator = null
                    currentSource = null
                    currentCoroutineContext().ensureActive()
                    captureLease = prepareCapture()
                    if (captureLease == null) {
                        source.close()
                        sourceReleased = true
                        _recordingState.value = SentenceRecordingState.Failed(PcmCoordinatorFailure.PLAYBACK_STOP_FAILED)
                        return@withLock false
                    }
                    try {
                        cleanupAbandonedSessions()
                    } catch (error: IOException) {
                        runCatching { source.close() }
                        sourceReleased = true
                        _recordingState.value = SentenceRecordingState.Failed(PcmCoordinatorFailure.STORAGE_IO_ERROR)
                        Log.w(TAG, "Cannot prepare recording storage", error)
                        return@withLock false
                    }
                    val token = generation.incrementAndGet()
                    val sessionCapture = checkNotNull(captureLease)
                    var inputReleased = false
                    var terminal = false
                    val session = PcmStorageCoordinator(
                        directory = File(storageDir, UUID.randomUUID().toString()),
                        limits = settingsRepository.settings.first().limits,
                        config = PcmCoordinatorConfig(sampleRate = source.sampleRate),
                        nowMillis = nowMillis,
                        nowNanos = nowNanos,
                        retireWhenUnused = true,
                        onInputReleased = {
                            inputReleased = true
                            if (terminal) sessionCapture.close()
                        },
                        onStateChanged = { state, failure ->
                            if (generation.get() == token) {
                                if (state == PcmCoordinatorState.CLOSED) currentSource = null
                                _recordingState.value = when (state) {
                                    PcmCoordinatorState.STARTING -> SentenceRecordingState.Starting
                                    PcmCoordinatorState.RUNNING -> SentenceRecordingState.Recording(nowMillis())
                                    PcmCoordinatorState.STOPPING -> SentenceRecordingState.Stopping
                                    PcmCoordinatorState.STOPPED -> SentenceRecordingState.Stopped(state)
                                    PcmCoordinatorState.FAILED -> SentenceRecordingState.Failed(checkNotNull(failure))
                                    else -> _recordingState.value
                                }
                            }
                            if (state == PcmCoordinatorState.STOPPED || state == PcmCoordinatorState.FAILED ||
                                state == PcmCoordinatorState.CLOSED) {
                                terminal = true
                                if (inputReleased || state == PcmCoordinatorState.CLOSED) sessionCapture.close()
                            }
                        },
                    )
                    attempt = session
                    captureTransferred = true
                    coordinator = session
                    currentSource = source
                    val started = session.start(source)
                    if (!started) {
                        session.close()
                        coordinator = null
                        currentSource = null
                    }
                    started
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                lifecycle.withLock {
                    val session = attempt
                    if (session != null) {
                        session.close()
                        if (coordinator === session && session.isReleased) {
                            coordinator = null
                            currentSource = null
                            generation.incrementAndGet()
                            _recordingState.value = SentenceRecordingState.Stopped(PcmCoordinatorState.STOPPED)
                        }
                    } else if (!sourceReleased && source !== currentSource) source.close()
                }
            }
            throw cancelled
        } finally {
            if (!captureTransferred) captureLease?.close()
        }
    }

    suspend fun stop() = withContext(NonCancellable + Dispatchers.IO) {
        lifecycle.withLock { coordinator?.stop() }
        Unit
    }

    suspend fun acquireScan(scanMillis: Long = nowMillis()): AudioContextId? {
        var session: PcmStorageCoordinator? = null
        var acquired: AudioContextId? = null
        try {
            return withContext(Dispatchers.IO) {
                lifecycle.withLock {
                    session = coordinator
                    session?.acquireScan(scanMillis, settingsRepository.settings.first().limits).also { acquired = it }
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                lifecycle.withLock {
                    acquired?.let { if (session === coordinator) session?.releaseContext(it) }
                }
            }
            throw cancelled
        }
    }

    suspend fun prepareEditor(id: AudioContextId, openMillis: Long = nowMillis()): AudioTimelineView? =
        withContext(Dispatchers.IO) { lifecycle.withLock { coordinator?.prepareEditor(id, openMillis) } }

    suspend fun openEditor(id: AudioContextId, openMillis: Long = nowMillis()): AudioTimelineView? =
        withContext(Dispatchers.IO) { lifecycle.withLock { coordinator?.openEditor(id, openMillis) } }

    suspend fun refresh(id: AudioContextId, refreshMillis: Long = nowMillis()): AudioTimelineView? =
        withContext(Dispatchers.IO) { lifecycle.withLock { coordinator?.refresh(id, refreshMillis) } }

    suspend fun releaseContext(id: AudioContextId) = withContext(NonCancellable + Dispatchers.IO) {
        lifecycle.withLock { coordinator?.releaseContext(id) }
        Unit
    }

    internal suspend fun snapshot(range: AudioRange, contextId: AudioContextId? = null): PcmHistory.Snapshot? {
        var acquired: PcmHistory.Snapshot? = null
        try {
            return withContext(Dispatchers.IO) {
                lifecycle.withLock {
                    val session = coordinator
                    if (contextId != null && session?.view(contextId) == null) return@withLock null
                    snapshotFrom(session, range).also { acquired = it }
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { acquired?.close() }
            throw cancelled
        }
    }

    suspend fun waveformSummary(range: AudioRange, buckets: Int = 200, contextId: AudioContextId? = null): WaveformSummary? = withContext(Dispatchers.IO) {
        val snapshot = snapshot(range, contextId) ?: return@withContext null
        snapshot.use { lease ->
            try { lease.openStream().use { WaveformSummary.compute(it, range, buckets) } }
            catch (e: Exception) { Log.w(TAG, "Waveform computation failed", e); null }
        }
    }

    suspend fun findSilence(
        range: AudioRange,
        sampleRate: Int = 48000,
        minSilenceMillis: Long = SilenceDetector.DEFAULT_MIN_SILENCE_MILLIS,
        edgePaddingMillis: Long = SilenceDetector.DEFAULT_EDGE_PADDING_MILLIS,
        contextId: AudioContextId? = null,
    ): List<AudioRange> = withContext(Dispatchers.IO) {
        val snapshot = snapshot(range, contextId) ?: return@withContext emptyList()
        snapshot.use { lease ->
            try {
                lease.openStream().use {
                    SilenceDetector.findSilenceCuts(it, range, sampleRate, minSilenceMillis, edgePaddingMillis)
                }
            } catch (e: Exception) { Log.w(TAG, "Silence search failed", e); emptyList() }
        }
    }

    fun initialBounds(view: AudioTimelineView, sampleRate: Int = 48000): AudioRange? =
        view.source?.let { InitialSelection.suggest(view.scanFrame, it, sampleRate) }

    suspend fun initialSelection(view: AudioTimelineView, sampleRate: Int = 48000): AudioEdit? = withContext(Dispatchers.IO) {
        val source = view.source ?: return@withContext null
        val initial = InitialSelection.suggest(view.scanFrame, source, sampleRate) ?: return@withContext null
        val snapshot = snapshot(initial, view.contextId) ?: return@withContext AudioEdit(initial)
        snapshot.use { lease ->
            try {
                val quiet = lease.openStream().use { SilenceDetector.detectLeadingQuiet(it, initial, sampleRate) }
                AudioEdit(InitialSelection.refine(initial, quiet, sampleRate))
            } catch (_: Exception) { AudioEdit(initial) }
        }
    }

    suspend fun startPreview(
        edit: AudioEdit,
        mode: PreviewMode = PreviewMode.RESULT,
        startFrame: Long? = null,
        onProgress: ((Long) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((PreviewFailure) -> Unit)? = null,
        onStopped: (() -> Unit)? = null,
        contextId: AudioContextId? = null,
    ) {
        val request = previewRequest.incrementAndGet()
        var requestLease: PlaybackCoordinator.Lease? = null
        requestLease = playback.acquire { stopPreviewOwned(requestLease) }
        val lease = requestLease ?: run {
            previewCallbackExecutor.execute {
                if (previewRequest.get() == request) runCatching { onError?.invoke(PreviewFailure.OUTPUT_BUSY) }
            }
            return
        }
        var installed = false
        try {
            previewCommands.withLock {
                val caller = currentCoroutineContext()
                withContext(NonCancellable + Dispatchers.IO) {
                    caller.ensureActive()
                    if (!lease.isCurrent) return@withContext
                    previewLease = lease
                    previewStopped = {
                        previewCallbackExecutor.execute {
                            if (previewRequest.get() == request) runCatching { onStopped?.invoke() }
                        }
                    }
                    installed = true
                    val session = coordinator
                    if (contextId != null && session?.view(contextId) == null) {
                        lease.close()
                        previewCallbackExecutor.execute {
                            if (previewRequest.get() == request) onError?.invoke(PreviewFailure.SOURCE_UNAVAILABLE)
                        }
                        return@withContext
                    }
                    if (previewPlayer != null && previewSession !== session) {
                        previewPlayer?.close()
                        if (previewPlayer?.isReleased == false) {
                            previewCallbackExecutor.execute {
                                if (lease.isCurrent && previewRequest.get() == request) {
                                    runCatching { onError?.invoke(PreviewFailure.OUTPUT_BUSY) }
                                }
                            }
                            return@withContext
                        }
                        previewPlayer = null
                    }
                    val player = previewPlayer ?: AudioPreviewPlayer(
                        snapshotProvider = { snapshotFrom(session, it) }, callbackExecutor = previewCallbackExecutor,
                        sinkFactory = previewSinkFactory ?: { AndroidAudioTrackSink(48000) },
                    ).also { previewPlayer = it; previewSession = session }
                    player.play(edit, mode, startFrame,
                        onProgress = { if (lease.isCurrent) onProgress?.invoke(it) },
                        onComplete = { if (lease.isCurrent) { lease.close(); onComplete?.invoke() } },
                        onError = {
                            if (lease.isCurrent) {
                                if (it != PreviewFailure.OUTPUT_BUSY) lease.close()
                                onError?.invoke(it)
                            }
                        })
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { stopPreviewOwned(lease) }
            throw cancelled
        } finally {
            if (!installed) lease.close()
        }
    }

    suspend fun pausePreview() = previewCommands.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            previewPlayer?.pause()
            previewStopped?.invoke()
            previewStopped = null
            if (previewPlayer?.isReleased != false) { previewLease?.close(); previewLease = null }
        }
    }

    suspend fun stopPreview() { stopPreviewOwned(null) }

    private suspend fun stopPreviewOwned(lease: PlaybackCoordinator.Lease?): Boolean = previewCommands.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            if (lease != null && previewLease !== lease) return@withContext true
            previewPlayer?.close()
            previewStopped?.invoke()
            previewStopped = null
            if (previewPlayer?.isReleased == false) return@withContext false
            previewPlayer = null
            previewSession = null
            previewLease?.close()
            previewLease = null
            true
        }
    }

    suspend fun seekPreview(frame: Long, edit: AudioEdit? = null, mode: PreviewMode = PreviewMode.RESULT) =
        previewCommands.withLock { withContext(Dispatchers.IO) {
            if (previewPlayer?.isPlaying == true && previewLease?.isCurrent != true) return@withContext
            previewPlayer?.seek(frame, edit, mode)
            Unit
        } }

    val isPreviewPlaying: Boolean get() = previewPlayer?.isPlaying == true
    val previewPosition: Long get() = previewPlayer?.currentFrame ?: 0L

    data class ExportOutcome(val isQuiet: Boolean)

    suspend fun export(edit: AudioEdit, outputFile: File, sampleRate: Int = 48000, contextId: AudioContextId? = null): ExportOutcome? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val snapshot = snapshot(edit.selection, contextId) ?: return@withContext null
        snapshot.use { lease ->
            try {
                val result = lease.openStream().use {
                    AudioExporter.exportOpus(AudioEditInputStream(it, edit), edit, sampleRate, outputFile)
                }
                ExportOutcome(result.isQuiet)
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                outputFile.delete()
                null
            }
        }
    }

    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        lifecycle.withLock {
            stopPreview()
            coordinator?.close()
            if (coordinator?.isReleased == false) return@withLock
            coordinator = null
            currentSource = null
            generation.incrementAndGet()
            _recordingState.value = SentenceRecordingState.Idle
        }
    }

    private fun snapshotFrom(session: PcmStorageCoordinator?, range: AudioRange): PcmHistory.Snapshot? =
        try { session?.snapshot(range) } catch (_: IllegalStateException) { null } catch (_: java.io.IOException) { null }

    /** Runs once before this singleton owns any session; later starts must not sweep live prior leases. */
    private fun cleanupAbandonedSessions() {
        if (abandonedCleaned) return
        if (!storageDir.isDirectory && !storageDir.mkdirs()) throw IOException("Cannot create recording directory")
        val root = storageDir.canonicalFile
        fun clear(directory: File) {
            PcmStorageCoordinator.cleanAbandonedFiles(directory)
            val remaining = directory.listFiles() ?: throw IOException("Cannot read recording directory")
            if (remaining.any { it.isFile && (it.name.endsWith(".pcm") || it.name.endsWith(".tmp")) }) {
                throw IOException("Cannot clear abandoned recording files")
            }
        }
        clear(root)
        for (child in root.listFiles() ?: throw IOException("Cannot read recording directory")) {
            if (!child.isDirectory || child.canonicalFile.parentFile != root) continue
            if (runCatching { UUID.fromString(child.name) }.isFailure) continue
            clear(child)
            child.delete() // Only empty session directories are removed; unrelated contents stay intact.
        }
        abandonedCleaned = true
    }

    companion object { private const val TAG = "SentenceRecorder" }
}
