package com.vpr.screenlate.overlay.anki

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.media.AudioContextId
import com.vpr.screenlate.core.media.AudioEdit
import com.vpr.screenlate.core.media.AudioRange
import com.vpr.screenlate.core.media.AudioTimelineView
import com.vpr.screenlate.core.media.PreviewMode
import com.vpr.screenlate.core.media.RecordingSettingsRepository
import com.vpr.screenlate.core.media.RetentionStage
import com.vpr.screenlate.core.media.SentenceRecorder
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.popup.PopupController
import com.vpr.screenlate.overlay.web.LookupPage
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface AudioEditorResult {
    data class WithAudio(val file: File) : AudioEditorResult
    data object WithoutAudio : AudioEditorResult
    data object Cancelled : AudioEditorResult
}

@Serializable
private data class AudioEditDto(
    val selection: List<Long>,
    val exclusions: List<List<Long>> = emptyList(),
)

/**
 * Coordinates the overlay Sentence Audio Editor with [SentenceRecorder] and [LookupPage].
 */
class AudioEditorController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val page: LookupPage,
    private val popupController: PopupController,
    private val sentenceRecorder: SentenceRecorder,
    private val recordingSettings: RecordingSettingsRepository? = null,
) : LookupPage.AudioEditorActions {

    private val json = Json { ignoreUnknownKeys = true }
    private var activeContextId: AudioContextId? = null
    private var activeContinuation: CancellableContinuation<AudioEditorResult>? = null
    private var currentEdit: AudioEdit? = null
    private var lastTimelineView: AudioTimelineView? = null

    private var silenceMinSeconds: Float = 1.5f
    private var silenceEdgeMarginMillis: Int = 150

    init {
        page.audioEditorActions = this
    }

    /**
     * Opens the audio editor for [audioContextId] and suspends until the user confirms (Add),
     * chooses Without audio, or cancels.
     */
    suspend fun edit(
        audioContextId: AudioContextId,
        term: String,
        reading: String,
    ): AudioEditorResult = withContext(Dispatchers.Main) {
        val timelineView = sentenceRecorder.openEditor(audioContextId)
            ?: return@withContext AudioEditorResult.WithoutAudio

        activeContextId = audioContextId
        lastTimelineView = timelineView
        popupController.isAudioEditorActive = true

        val totalFrames = timelineView.source?.endExclusive ?: 0L
        val isLimitReached = timelineView.retention.stage == RetentionStage.SEALED || timelineView.retention.reason != null
        val remainingMillis = maxOf(0L, timelineView.retention.cutoffMillis - SystemClock.elapsedRealtime())

        val sampleRate = 48000
        val initialEdit = sentenceRecorder.initialSelection(timelineView, sampleRate)
            ?: AudioEdit(timelineView.source ?: AudioRange(0, totalFrames))
        currentEdit = initialEdit

        val sourceRange = timelineView.source ?: AudioRange(0, totalFrames)
        val summary = sentenceRecorder.waveformSummary(sourceRange, buckets = 200)

        val labels = buildJsonObject {
            put("audio_editor_recording_active", context.getString(R.string.audio_editor_recording_active))
            put("audio_editor_recording_stopped", context.getString(R.string.audio_editor_recording_stopped))
            put("audio_editor_limit_reached", context.getString(R.string.audio_editor_limit_reached))
            put("audio_editor_remaining", context.getString(R.string.audio_editor_remaining))
            put("audio_editor_new_sound", context.getString(R.string.audio_editor_new_sound))
            put("audio_editor_found_pauses", context.getString(R.string.audio_editor_found_pauses))
            put("audio_editor_no_silence", context.getString(R.string.audio_editor_no_silence))
            put("audio_editor_compact", context.getString(R.string.audio_editor_compact))
            put("audio_editor_expand", context.getString(R.string.audio_editor_expand))
            put("audio_editor_listen", context.getString(R.string.audio_editor_listen))
            put("audio_editor_pause", context.getString(R.string.audio_editor_pause))
            put("audio_editor_discard_title", context.getString(R.string.audio_editor_discard_title))
            put("audio_editor_discard_message", context.getString(R.string.audio_editor_discard_message))
            put("audio_editor_keep_editing", context.getString(R.string.audio_editor_keep_editing))
            put("audio_editor_discard", context.getString(R.string.audio_editor_discard))
            put("audio_editor_silence_title", context.getString(R.string.audio_editor_silence_title))
            put("audio_editor_min_pause", context.getString(R.string.audio_editor_min_pause))
            put("audio_editor_edge_margin", context.getString(R.string.audio_editor_edge_margin))
            put("audio_editor_cancel", context.getString(R.string.audio_editor_cancel))
            put("audio_editor_save", context.getString(R.string.audio_editor_save))
            put("audio_editor_info_title", context.getString(R.string.audio_editor_info_title))
            put("audio_editor_info_body", context.getString(R.string.audio_editor_info_body))
            put("audio_editor_understood", context.getString(R.string.audio_editor_understood))
            put("audio_editor_title", context.getString(R.string.audio_editor_title))
            put("audio_editor_retained", context.getString(R.string.audio_editor_retained))
            put("audio_editor_marker_legend", context.getString(R.string.audio_editor_marker_legend))
            put("audio_editor_from_scan", context.getString(R.string.audio_editor_from_scan))
            put("audio_editor_start", context.getString(R.string.audio_editor_start))
            put("audio_editor_end", context.getString(R.string.audio_editor_end))
            put("audio_editor_to_card", context.getString(R.string.audio_editor_to_card))
            put("audio_editor_result", context.getString(R.string.audio_editor_result))
            put("audio_editor_original", context.getString(R.string.audio_editor_original))
            put("audio_editor_cut", context.getString(R.string.audio_editor_cut))
            put("audio_editor_find_silence", context.getString(R.string.audio_editor_find_silence))
            put("audio_editor_undo", context.getString(R.string.audio_editor_undo))
            put("audio_editor_refresh", context.getString(R.string.audio_editor_refresh))
            put("audio_editor_without", context.getString(R.string.audio_editor_without))
            put("audio_editor_add", context.getString(R.string.audio_editor_add))
            put("audio_editor_apply", context.getString(R.string.audio_editor_apply))
            put("audio_editor_dismiss", context.getString(R.string.audio_editor_dismiss))
            put("audio_editor_collapse", context.getString(R.string.audio_editor_collapse))
            put("audio_editor_zoom_in", context.getString(R.string.audio_editor_zoom_in))
            put("audio_editor_zoom_out", context.getString(R.string.audio_editor_zoom_out))
            put("audio_editor_remove_cut", context.getString(R.string.audio_editor_remove_cut))
            put("audio_editor_select_cut", context.getString(R.string.audio_editor_select_cut))
            put("audio_editor_cut_edge_start", context.getString(R.string.audio_editor_cut_edge_start))
            put("audio_editor_cut_edge_end", context.getString(R.string.audio_editor_cut_edge_end))
            put("audio_editor_start_handle", context.getString(R.string.audio_editor_start_handle))
            put("audio_editor_end_handle", context.getString(R.string.audio_editor_end_handle))
            put("audio_editor_empty_output", context.getString(R.string.audio_editor_empty_output))
            put("audio_editor_export_failed", context.getString(R.string.audio_editor_export_failed))
        }

        val openPayload = buildJsonObject {
            put("sampleRate", sampleRate)
            put("totalFrames", totalFrames)
            put("scanFrame", timelineView.scanFrame)
            put("sourceStart", sourceRange.start)
            put("sourceEnd", sourceRange.endExclusive)
            put("selection", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(initialEdit.selection.start))
                add(kotlinx.serialization.json.JsonPrimitive(initialEdit.selection.endExclusive))
            })
            put("cuts", buildJsonArray {
                for (cut in initialEdit.exclusions) {
                    add(buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive(cut.start))
                        add(kotlinx.serialization.json.JsonPrimitive(cut.endExclusive))
                    })
                }
            })
            put("mode", "result")
            put("limit", isLimitReached)
            put("remainingMillis", remainingMillis)
            put("newFrames", timelineView.newFrames)
            put("term", term)
            put("reading", reading)
            if (summary != null) {
                put("overviewSummary", buildJsonArray {
                    for (peak in summary.peaks) {
                        add(kotlinx.serialization.json.JsonPrimitive(peak))
                    }
                })
            }
            put("labels", labels)
        }

        page.openAudioEditor(openPayload.toString())

        suspendCancellableCoroutine<AudioEditorResult> { continuation ->
            activeContinuation = continuation
            continuation.invokeOnCancellation {
                cleanup()
            }
        }
    }

    override fun onAudioPlay(cursor: Long, mode: String) {
        val edit = currentEdit ?: return
        val previewMode = if (mode == "original") PreviewMode.ORIGINAL else PreviewMode.RESULT
        sentenceRecorder.startPreview(
            edit = edit,
            mode = previewMode,
            startFrame = cursor,
            onProgress = { frame ->
                page.updateAudioCursor(frame)
            },
            onComplete = {
                page.setAudioPlaying(false)
            },
        )
        page.setAudioPlaying(true)
    }

    override fun onAudioPause() {
        sentenceRecorder.pausePreview()
        page.setAudioPlaying(false)
    }

    override fun onAudioSeek(cursor: Long, mode: String) {
        val edit = currentEdit
        val previewMode = if (mode == "original") PreviewMode.ORIGINAL else PreviewMode.RESULT
        sentenceRecorder.seekPreview(cursor, edit, previewMode)
    }

    override fun onAudioFindSilence(fromFrame: Long, toFrame: Long, request: Int) {
        scope.launch(Dispatchers.Default) {
            val cuts = sentenceRecorder.findSilence(
                range = AudioRange(fromFrame, toFrame),
                sampleRate = 48000,
                minSilenceMillis = (silenceMinSeconds * 1000).toLong(),
                edgePaddingMillis = silenceEdgeMarginMillis.toLong(),
            )
            val jsonArray = buildJsonArray {
                for (cut in cuts) {
                    add(buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive(cut.start))
                        add(kotlinx.serialization.json.JsonPrimitive(cut.endExclusive))
                    })
                }
            }
            withContext(Dispatchers.Main) {
                page.setAudioSilenceProposals(request, jsonArray.toString())
            }
        }
    }

    override fun onAudioSilenceSettingsChanged(minPauseSeconds: Float, edgeMarginMillis: Int) {
        silenceMinSeconds = minPauseSeconds
        silenceEdgeMarginMillis = edgeMarginMillis
    }

    override fun onAudioRefresh() {
        val contextId = activeContextId ?: return
        val refreshedView = sentenceRecorder.refresh(contextId) ?: return
        lastTimelineView = refreshedView
        val totalFrames = refreshedView.source?.endExclusive ?: 0L
        val isLimitReached = refreshedView.retention.stage == RetentionStage.SEALED || refreshedView.retention.reason != null
        val remainingMillis = maxOf(0L, refreshedView.retention.cutoffMillis - SystemClock.elapsedRealtime())
        val sourceRange = refreshedView.source ?: AudioRange(0, totalFrames)
        val summary = sentenceRecorder.waveformSummary(sourceRange, buckets = 200)

        val updatePayload = buildJsonObject {
            put("totalFrames", totalFrames)
            put("newFrames", refreshedView.newFrames)
            put("limit", isLimitReached)
            put("remainingMillis", remainingMillis)
            put("recording", sentenceRecorder.isRecording)
            if (summary != null) {
                put("overviewSummary", buildJsonArray {
                    for (peak in summary.peaks) {
                        add(kotlinx.serialization.json.JsonPrimitive(peak))
                    }
                })
            }
        }
        page.updateAudioTimeline(updatePayload.toString())
    }

    override fun onAudioExpand(expanded: Boolean) {
        popupController.setAudioEditorExpanded(expanded)
    }

    override fun onAudioCollapse(collapsed: Boolean) {
        popupController.setAudioEditorCollapsed(collapsed)
    }

    override fun onAudioCancel(dirty: Boolean) {
        finish(AudioEditorResult.Cancelled)
    }

    override fun onAudioWithout() {
        finish(AudioEditorResult.WithoutAudio)
    }

    override fun onAudioAdd(editJson: String) {
        scope.launch(Dispatchers.Default) {
            val editDto = runCatching { json.decodeFromString<AudioEditDto>(editJson) }.getOrNull()
            if (editDto == null || editDto.selection.size < 2) {
                withContext(Dispatchers.Main) { finish(AudioEditorResult.WithoutAudio) }
                return@launch
            }
            val edit = AudioEdit(
                selection = AudioRange(editDto.selection[0], editDto.selection[1]),
                exclusions = editDto.exclusions.filter { it.size >= 2 }.map { AudioRange(it[0], it[1]) },
            )
            if (edit.outputFrames <= 0L) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.audio_editor_empty_output, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val tempFile = File.createTempFile("screenlate_sentence_", ".opus", context.cacheDir)
            val exported = sentenceRecorder.export(edit, tempFile)
            withContext(Dispatchers.Main) {
                if (exported) {
                    finish(AudioEditorResult.WithAudio(tempFile))
                } else {
                    tempFile.delete()
                    Toast.makeText(context, R.string.audio_editor_export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun finish(result: AudioEditorResult) {
        cleanup()
        val cont = activeContinuation
        activeContinuation = null
        cont?.resume(result)
    }

    private fun cleanup() {
        sentenceRecorder.stopPreview()
        page.closeAudioEditor()
        popupController.resetAudioEditorPresentation()
        activeContextId = null
        currentEdit = null
        lastTimelineView = null
    }

    companion object {
        private const val TAG = "AudioEditorController"
    }
}
