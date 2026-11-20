package com.vpr.screenlate.core.media

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.vpr.screenlate.core.common.log.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SentenceRecordingState {
    data object Idle : SentenceRecordingState
    data class Recording(val startTimeMillis: Long) : SentenceRecordingState
    data class Stopped(val state: PcmCoordinatorState) : SentenceRecordingState
    data class Failed(val failure: PcmCoordinatorFailure) : SentenceRecordingState
}

/**
 * Public facade of `core:media` for coordinating sentence audio recording.
 */
@Singleton
class SentenceRecorder internal constructor(
    private val storageDir: File,
    private val settingsRepository: RecordingSettingsRepository,
) : AutoCloseable {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        settingsRepository: RecordingSettingsRepository,
    ) : this(
        File(context.noBackupFilesDir, "sentence_recording").apply { mkdirs() },
        settingsRepository,
    )

    private var coordinator: PcmStorageCoordinator? = null
    private var previewPlayer: AudioPreviewPlayer? = null
    private val _recordingState = MutableStateFlow<SentenceRecordingState>(SentenceRecordingState.Idle)
    val recordingState: StateFlow<SentenceRecordingState> = _recordingState.asStateFlow()

    val isRecording: Boolean get() = _recordingState.value is SentenceRecordingState.Recording

    private var onStoppedListener: (() -> Unit)? = null

    fun setOnStoppedListener(listener: (() -> Unit)?) {
        onStoppedListener = listener
    }

    @Synchronized
    fun start(source: AudioSource): Boolean {
        if (isRecording) return true
        coordinator?.close()
        val limits = settingsRepository.cachedSettings.limits
        val coord = PcmStorageCoordinator(
            directory = storageDir,
            limits = limits,
            nowMillis = { SystemClock.elapsedRealtime() },
            nowNanos = { SystemClock.elapsedRealtimeNanos() },
        )
        if (!coord.start(source)) {
            val failure = coord.failureReason ?: PcmCoordinatorFailure.SOURCE_START_FAILED
            Log.w(TAG, "Failed to start audio recording: $failure")
            _recordingState.value = SentenceRecordingState.Failed(failure)
            coord.close()
            return false
        }
        coordinator = coord
        val startTime = SystemClock.elapsedRealtime()
        _recordingState.value = SentenceRecordingState.Recording(startTime)
        Log.i(TAG, "Sentence audio recording started")
        return true
    }

    @Synchronized
    fun stop() {
        val coord = coordinator ?: return
        if (!isRecording) return
        coord.stop()
        _recordingState.value = SentenceRecordingState.Stopped(coord.currentState)
        Log.i(TAG, "Sentence audio recording stopped")
        onStoppedListener?.invoke()
    }

    @Synchronized
    fun acquireScan(scanMillis: Long = SystemClock.elapsedRealtime()): AudioContextId? {
        val coord = coordinator ?: return null
        return coord.acquireScan(scanMillis)
    }

    @Synchronized
    fun openEditor(id: AudioContextId, openMillis: Long = SystemClock.elapsedRealtime()): AudioTimelineView? {
        val coord = coordinator ?: return null
        return coord.openEditor(id, openMillis)
    }

    @Synchronized
    fun refresh(id: AudioContextId, refreshMillis: Long = SystemClock.elapsedRealtime()): AudioTimelineView? {
        val coord = coordinator ?: return null
        return coord.refresh(id, refreshMillis)
    }

    @Synchronized
    fun releaseContext(id: AudioContextId) {
        coordinator?.releaseContext(id)
    }

    @Synchronized
    internal fun snapshot(range: AudioRange): PcmHistory.Snapshot? {
        val coord = coordinator ?: return null
        return runCatching { coord.snapshot(range) }.getOrNull()
    }

    @Synchronized
    fun waveformSummary(range: AudioRange, buckets: Int = 200): WaveformSummary? {
        val snapshot = snapshot(range) ?: return null
        return try {
            snapshot.openStream().use { stream ->
                WaveformSummary.compute(stream, range, buckets)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Waveform computation failed", e)
            null
        } finally {
            snapshot.close()
        }
    }

    @Synchronized
    fun findSilence(
        range: AudioRange,
        sampleRate: Int = 48000,
        minSilenceMillis: Long = SilenceDetector.DEFAULT_MIN_SILENCE_MILLIS,
        edgePaddingMillis: Long = SilenceDetector.DEFAULT_EDGE_PADDING_MILLIS,
    ): List<AudioRange> {
        val snapshot = snapshot(range) ?: return emptyList()
        return try {
            snapshot.openStream().use { stream ->
                SilenceDetector.findSilenceCuts(stream, range, sampleRate, minSilenceMillis, edgePaddingMillis)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Silence search failed", e)
            emptyList()
        } finally {
            snapshot.close()
        }
    }

    @Synchronized
    fun initialSelection(view: AudioTimelineView, sampleRate: Int = 48000): AudioEdit? {
        val source = view.source ?: return null
        val initial = InitialSelection.suggest(view.scanFrame, source, sampleRate) ?: return null
        val snapshot = snapshot(initial) ?: return AudioEdit(initial)
        return try {
            val quiet = snapshot.openStream().use { stream ->
                SilenceDetector.detectLeadingQuiet(stream, initial, sampleRate)
            }
            val refined = InitialSelection.refine(initial, quiet, sampleRate)
            AudioEdit(refined)
        } catch (e: Exception) {
            AudioEdit(initial)
        } finally {
            snapshot.close()
        }
    }

    @Synchronized
    fun startPreview(
        edit: AudioEdit,
        mode: PreviewMode = PreviewMode.RESULT,
        startFrame: Long? = null,
        onProgress: ((Long) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        if (previewPlayer == null) {
            previewPlayer = AudioPreviewPlayer(snapshotProvider = ::snapshot)
        }
        previewPlayer?.play(edit, mode, startFrame, onProgress, onComplete)
    }

    @Synchronized
    fun pausePreview() {
        previewPlayer?.pause()
    }

    @Synchronized
    fun stopPreview() {
        previewPlayer?.stop()
    }

    @Synchronized
    fun seekPreview(frame: Long, edit: AudioEdit? = null, mode: PreviewMode = PreviewMode.RESULT) {
        previewPlayer?.seek(frame, edit, mode)
    }

    val isPreviewPlaying: Boolean get() = previewPlayer?.isPlaying == true
    val previewPosition: Long get() = previewPlayer?.currentFrame ?: 0L

    data class ExportOutcome(
        val isQuiet: Boolean,
    )

    @Synchronized
    fun export(
        edit: AudioEdit,
        outputFile: File,
        sampleRate: Int = 48000,
    ): ExportOutcome? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val coord = coordinator ?: return null
        val snapshot = runCatching { coord.snapshot(edit.selection) }.getOrNull() ?: return null
        return try {
            val result = snapshot.openStream().use { rawStream ->
                val editStream = AudioEditInputStream(rawStream, edit)
                AudioExporter.exportOpus(editStream, edit, sampleRate, outputFile)
            }
            ExportOutcome(isQuiet = result.isQuiet)
        } catch (e: Exception) {
            Log.w(TAG, "Export failed", e)
            outputFile.delete()
            null
        } finally {
            snapshot.close()
        }
    }

    @Synchronized
    override fun close() {
        stop()
        previewPlayer?.close()
        previewPlayer = null
        coordinator?.close()
        coordinator = null
        _recordingState.value = SentenceRecordingState.Idle
    }

    companion object {
        private const val TAG = "SentenceRecorder"
    }
}
