package com.vpr.screenlate.overlay.anki

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.media.AudioContextId
import com.vpr.screenlate.core.media.AudioEdit
import com.vpr.screenlate.core.media.AudioRange
import com.vpr.screenlate.core.media.AudioTimelineView
import com.vpr.screenlate.core.media.PreviewMode
import com.vpr.screenlate.core.media.RecordingSettingsRepository
import com.vpr.screenlate.core.media.RetentionStage
import com.vpr.screenlate.core.media.RetentionEnd
import com.vpr.screenlate.core.media.SentenceRecorder
import com.vpr.screenlate.overlay.R
import com.vpr.screenlate.overlay.popup.PopupController
import com.vpr.screenlate.overlay.web.LookupPage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface AudioEditorResult {
    data class WithAudio(val file: File, val contextId: AudioContextId, val edit: AudioEdit) : AudioEditorResult
    data object WithoutAudio : AudioEditorResult
    data object Cancelled : AudioEditorResult
}

@Serializable
private data class AudioEditorCommand(
    val operation: String,
    val sequence: Long,
    val sourceRevision: Long,
    val action: String,
    val selection: List<Long> = emptyList(),
    val exclusions: List<List<Long>> = emptyList(),
    val cursor: Long = 0,
    val mode: String = "result",
    val request: Int = 0,
    val range: List<Long> = emptyList(),
    val buckets: Int = 256,
    val minPauseSeconds: Float = 1.5f,
    val edgeMarginMillis: Int = 150,
    val expanded: Boolean = false,
    val collapsed: Boolean = false,
    val dx: Float = 0f,
    val dy: Float = 0f,
)

/** Android adapter for one owned editor operation; every page command carries its identity and source revision. */
class AudioEditorController(
    private val context: Context,
    private val page: LookupPage,
    private val popupController: PopupController,
    private val sentenceRecorder: SentenceRecorder,
    private val recordingSettings: RecordingSettingsRepository? = null,
    private val onVisibilityChanged: (Boolean) -> Unit = {},
    private val onOpenAnkiSettings: () -> Unit = {},
) : LookupPage.AudioEditorActions {
    private val json = Json { ignoreUnknownKeys = true }
    private var retainedContextId: AudioContextId? = null
    private var lastConfirmed: Pair<AudioContextId, AudioEdit>? = null
    private var active: Session? = null
    private var released = false

    private class Session(val contextId: AudioContextId) {
        val id = UUID.randomUUID().toString()
        lateinit var operation: AudioEditorOperation
        var refresh: Job? = null
        var analysis: Job? = null
        var ready: Job? = null
        val waveforms = Channel<AudioEditorCommand>(Channel.CONFLATED)
        var waveformRequest = 0
        var transferFailed = false
        fun isReady() = this::operation.isInitialized
    }

    init { page.audioEditorActions = this }

    suspend fun edit(
        audioContextId: AudioContextId,
        term: String,
        reading: String,
        duplicateKeep: Boolean = false,
        prepareAudio: suspend (File) -> Boolean = { true },
    ): AudioEditorResult = withContext(Dispatchers.Main) {
        if (released || active != null) return@withContext AudioEditorResult.Cancelled
        val session = Session(audioContextId)
        retainedContextId = audioContextId
        active = session
        var delivered: File? = null
        var exports: AudioExportPreparation? = null
        try {
            coroutineScope {
                val timeline = sentenceRecorder.prepareEditor(audioContextId)
                    ?: return@coroutineScope AudioEditorResult.Cancelled
                val source = timeline.source ?: return@coroutineScope AudioEditorResult.Cancelled
                val previous = lastConfirmed?.takeIf { it.first == audioContextId }?.second
                val initial = previous?.takeIf { source.intersect(it.selection) == it.selection }
                    ?: sentenceRecorder.initialSelection(timeline) ?: AudioEdit(source)
                val exporter = AudioExportPreparation(encode = { encode(session, it) }, prepare = { file ->
                    session.transferFailed = true
                    prepareAudio(file).also { if (it) session.transferFailed = false }
                })
                exports = exporter
                session.operation = AudioEditorOperation(this, source, initial, onChanged = {
                    if (active === session) send(session) {
                        put("phase", session.operation.phase.name.lowercase())
                        if (session.operation.phase == AudioEditorOperation.Phase.FAILED) {
                            put("error", context.getString(if (session.transferFailed) R.string.audio_editor_transfer_failed else R.string.audio_editor_export_failed))
                        }
                    }
                }, opening = true, preview = { preview(session, it) }) { edit ->
                    exporter.export(edit)
                }
                session.operation.scope.launch {
                    for (request in session.waveforms) {
                        val operation = session.operation
                        if (request.request != session.waveformRequest || request.sourceRevision != operation.sourceRevision) continue
                        val range = AudioRange(request.range[0], request.range[1])
                        val sourceRevision = operation.sourceRevision
                        val summary = sentenceRecorder.waveformSummary(range, request.buckets, audioContextId)
                        val overview = sentenceRecorder.waveformSummary(operation.source, 200, audioContextId)
                        if (active === session && request.request == session.waveformRequest &&
                            operation.sourceRevision == sourceRevision && summary != null) send(session) {
                            put("waveform", buildJsonObject {
                                put("request", request.request)
                                put("sourceRevision", sourceRevision)
                                put("range", ranges(listOf(range)).first())
                                put("peaks", buildJsonArray { summary.peaks.forEach { add(JsonPrimitive(it)) } })
                                overview?.let { put("overview", buildJsonArray { it.peaks.forEach { add(JsonPrimitive(it)) } }) }
                            })
                        }
                    }
                }
                if (!popupController.enterAudioEditor()) {
                    session.operation.cancel()
                    return@coroutineScope AudioEditorResult.Cancelled
                }
                page.onEditorBack = { page.cancelAudioEditor(session.id) }
                onVisibilityChanged(true)
                val labels = buildJsonObject {
                    put("audio_editor_recording_active", context.getString(R.string.audio_editor_recording_active))
                    put("audio_editor_recording_stopped", context.getString(R.string.audio_editor_recording_stopped))
                    put("audio_editor_limit_reached", context.getString(R.string.audio_editor_limit_reached))
                    put("audio_editor_remaining", context.getString(R.string.audio_editor_remaining))
                    put("audio_editor_limit_toast", context.getString(R.string.audio_editor_limit_toast))
                    put("audio_editor_refreshed_toast", context.getString(R.string.audio_editor_refreshed_toast))
                    put("audio_editor_no_new_toast", context.getString(R.string.audio_editor_no_new_toast))
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
                    put("audio_editor_retry", context.getString(R.string.audio_editor_retry))
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
                    put("audio_editor_duplicate_keep_title", context.getString(R.string.audio_editor_duplicate_keep_title))
                    put("audio_editor_duplicate_keep_body", context.getString(R.string.audio_editor_duplicate_keep_body))
                    put("audio_editor_duplicate_keep_continue", context.getString(R.string.audio_editor_duplicate_keep_continue))
                    put("audio_editor_duplicate_keep_change_settings", context.getString(R.string.audio_editor_duplicate_keep_change_settings))
                }

                val openPayload = buildJsonObject {
                    put("operation", session.id)
                    put("phase", "opening")
                    put("sourceRevision", session.operation.sourceRevision)
                    put("duplicateKeep", duplicateKeep)
                    put("sampleRate", 48000)
                    put("totalFrames", source.endExclusive)
                    put("scanFrame", timeline.scanFrame)
                    put("sourceStart", source.start)
                    put("sourceEnd", source.endExclusive)
                    put("selection", ranges(listOf(initial.selection)).first())
                    put("cuts", ranges(initial.exclusions))
                    put("mode", "result")
                    put("recording", sentenceRecorder.isRecording(audioContextId))
                    putRetention(timeline)
                    put("term", term)
                    put("reading", reading)
                    put("labels", labels)
                }
                page.openAudioEditor(openPayload.toString())
                when (val result = session.operation.result.await()) {
                    is AudioEditorOperation.Result.Audio -> {
                        delivered = result.file
                        AudioEditorResult.WithAudio(result.file, audioContextId, result.edit)
                    }
                    AudioEditorOperation.Result.WithoutAudio -> AudioEditorResult.WithoutAudio
                    AudioEditorOperation.Result.Cancelled -> AudioEditorResult.Cancelled
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                if (session.isReady()) {
                    session.operation.cancel()
                    session.operation.awaitClosed()
                }
                exports?.close(keep = delivered)
                if (active === session) {
                    sentenceRecorder.stopPreview()
                    if (!released) {
                        page.onEditorBack = null
                        send(session) { put("closed", true) }
                        popupController.resetAudioEditorPresentation()
                        onVisibilityChanged(false)
                    }
                    active = null
                }
            }
        }
    }

    override fun onAudioEditorCommand(payload: String) {
        val session = active ?: return
        if (!session.isReady() || payload.length > 65_536) return
        val command = runCatching { json.decodeFromString<AudioEditorCommand>(payload) }.getOrNull() ?: run {
            val identity = runCatching { json.parseToJsonElement(payload).jsonObject["operation"]?.jsonPrimitive?.content }.getOrNull()
            if (identity == session.id) rejectCommand(session)
            return
        }
        if (command.operation != session.id) return
        val operation = session.operation
        if (command.action == "ready") {
            if (operation.phase != AudioEditorOperation.Phase.OPENING || session.ready?.isActive == true) return
            session.ready = operation.scope.launch {
                val view = sentenceRecorder.openEditor(session.contextId) ?: return@launch
                val range = view.source ?: operation.source
                val previous = lastConfirmed?.takeIf { it.first == session.contextId }?.second
                val initial = previous?.takeIf { range.intersect(it.selection) == it.selection } ?: run {
                    val bounds = sentenceRecorder.initialBounds(view) ?: range
                    AudioEdit(AudioRange(operation.edit.selection.start.coerceIn(bounds.start, bounds.endExclusive), bounds.endExclusive))
                }
                if (active !== session || !operation.ready(range, initial, command.sequence)) return@launch
                val revision = operation.sourceRevision
                val sequence = operation.sequence
                send(session) {
                    put("phase", "editing")
                    put("initialSelection", ranges(listOf(initial.selection)).first())
                    put("initialCuts", ranges(initial.exclusions))
                    put("timeline", buildJsonObject {
                        put("sourceRevision", operation.sourceRevision)
                        put("totalFrames", range.endExclusive)
                        put("sourceStart", range.start)
                        put("sourceEnd", range.endExclusive)
                        putRetention(view)
                        put("recording", sentenceRecorder.isRecording(session.contextId))
                        put("overviewSummary", buildJsonArray {})
                    })
                }
                if (previous == null) {
                    val refined = sentenceRecorder.initialSelection(view)
                    if (active === session && refined != null && operation.refineInitial(sequence, revision, refined)) send(session) {
                        put("initialSequence", sequence)
                        put("initialSelection", ranges(listOf(refined.selection)).first())
                        put("initialCuts", ranges(refined.exclusions))
                        put("editRevision", operation.editRevision)
                    }
                }
                while (isActive && active === session) {
                    val status = sentenceRecorder.view(session.contextId) ?: break
                    send(session) {
                        put("status", buildJsonObject {
                            putRetention(status)
                            val availableEnd = (status.source?.endExclusive ?: operation.source.endExclusive) + status.newFrames
                            put("newFrames", maxOf(0, availableEnd - operation.source.endExclusive))
                            put("recording", sentenceRecorder.isRecording(session.contextId))
                        })
                    }
                    delay(1000)
                }
            }
            return
        }
        when (command.action) {
            "waveform" -> {
                if (operation.phase !in setOf(AudioEditorOperation.Phase.EDITING, AudioEditorOperation.Phase.FAILED) ||
                    command.sourceRevision != operation.sourceRevision || command.request <= session.waveformRequest ||
                    command.range.size != 2 || command.buckets !in 1..512) return
                val range = runCatching { AudioRange(command.range[0], command.range[1]) }.getOrNull() ?: return
                if (range.frames <= 0 || operation.source.intersect(range) != range) return
                session.waveformRequest = command.request
                session.waveforms.trySend(command)
                return
            }
            "expand" -> { popupController.setAudioEditorExpanded(command.expanded); return }
            "collapse" -> { popupController.setAudioEditorCollapsed(command.collapsed); return }
            "drag" -> {
                if (command.dx.isFinite() && command.dy.isFinite()) {
                    val density = context.resources.displayMetrics.density
                    popupController.dragBy(command.dx * density, command.dy * density)
                }
                return
            }
        }
        if (command.action in setOf("cancel", "without", "settings")) {
            when (command.action) {
                "cancel" -> operation.cancel()
                "without" -> operation.withoutAudio()
                "settings" -> { operation.cancel(); onOpenAnkiSettings() }
            }
            return
        }
        if (command.action == "pause") {
            operation.pause(command.sequence)
            return
        }
        if (command.selection.size != 2 || command.exclusions.any { it.size != 2 }) { rejectCommand(session); return }
        val edit = runCatching {
            AudioEdit(AudioRange(command.selection[0], command.selection[1]),
                command.exclusions.map { AudioRange(it[0], it[1]) })
        }.getOrNull() ?: run { rejectCommand(session); return }
        if (!operation.accept(command.sequence, command.sourceRevision, edit)) {
            // Refresh may have published a new source while this command was in flight.
            send(session) {
                put("phase", operation.phase.name.lowercase())
                put("timeline", buildJsonObject {
                    put("sourceRevision", operation.sourceRevision)
                    put("sourceStart", operation.source.start)
                    put("sourceEnd", operation.source.endExclusive)
                    put("totalFrames", operation.source.endExclusive)
                })
            }
            return
        }
        val revision = operation.editRevision
        send(session) { put("sequence", command.sequence); put("editRevision", revision) }
        val previewMode = if (command.mode == "original") PreviewMode.ORIGINAL else PreviewMode.RESULT
        when (command.action) {
            "edit" -> {
                session.analysis?.cancel()
                operation.pause(command.sequence)
            }
            "play", "seek" -> {
                if (command.action == "play") operation.play(previewMode, command.cursor, command.sequence)
                else operation.seek(previewMode, command.cursor, command.sequence)
            }
            "save" -> {
                session.analysis?.cancel()
                session.refresh?.cancel()
                operation.save()
            }
            "silence" -> {
                session.analysis?.cancel()
                session.analysis = operation.scope.launch {
                    val settings = recordingSettings?.cachedSettings
                    val cuts = sentenceRecorder.findSilence(edit.selection,
                        minSilenceMillis = settings?.minSilenceMillis ?: 1500,
                        edgePaddingMillis = settings?.silenceMarginMillis ?: 150,
                        contextId = session.contextId)
                    if (active === session && operation.editRevision == revision) send(session) {
                        put("request", command.request); put("proposals", ranges(cuts))
                    }
                }
            }
            "silenceSettings" -> operation.scope.launch {
                if (command.minPauseSeconds.isFinite() && command.minPauseSeconds in 0.1f..30f && command.edgeMarginMillis in 0..2000) {
                    recordingSettings?.setMinSilenceMillis((command.minPauseSeconds * 1000).toLong())
                    recordingSettings?.setSilenceMarginMillis(command.edgeMarginMillis.toLong())
                }
            }
            "refresh" -> {
                if (session.refresh?.isActive == true) return
                session.refresh = operation.scope.launch {
                    val view = sentenceRecorder.refresh(session.contextId) ?: return@launch
                    val range = view.source ?: return@launch
                    val previousEnd = operation.source.endExclusive
                    if (active !== session || !operation.append(range)) return@launch
                    send(session) {
                        put("refresh", buildJsonObject {
                            put("addedFrames", range.endExclusive - previousEnd)
                            put("sealed", view.retention.stage == RetentionStage.SEALED)
                            put("limit", view.retention.reason != null && view.retention.reason != RetentionEnd.CAPTURE_STOPPED)
                        })
                        put("timeline", buildJsonObject {
                            put("sourceRevision", operation.sourceRevision)
                            put("totalFrames", range.endExclusive)
                            put("sourceStart", range.start)
                            put("sourceEnd", range.endExclusive)
                            put("recording", sentenceRecorder.isRecording(session.contextId))
                            putRetention(view)
                        })
                    }
                }
            }
        }
    }

    /** The note, rather than an abandoned export, confirms the reusable edit for this scan. */
    fun confirm(result: AudioEditorResult.WithAudio) {
        if (!released && retainedContextId == result.contextId) lastConfirmed = result.contextId to result.edit
    }

    fun releaseContext(id: AudioContextId, cancelActive: Boolean = true) {
        if (retainedContextId == id) retainedContextId = null
        if (lastConfirmed?.first == id) lastConfirmed = null
        if (cancelActive) active?.takeIf { it.contextId == id }?.let { if (it.isReady()) it.operation.cancel() }
    }

    val canFinish: Boolean get() = active?.let {
        it.isReady() && it.operation.phase in setOf(AudioEditorOperation.Phase.EDITING,
            AudioEditorOperation.Phase.FAILED, AudioEditorOperation.Phase.EXPORTING)
    } == true

    fun requestSave(): Boolean {
        val session = active?.takeIf { canFinish } ?: return false
        page.saveAudioEditor(session.id)
        return true
    }

    /** Detach callbacks synchronously before the host destroys its WebView. Owned cleanup may finish later. */
    fun release() {
        if (released) return
        released = true
        page.onEditorBack = null
        page.audioEditorActions = null
        active?.let { if (it.isReady()) it.operation.cancel() }
        popupController.resetAudioEditorPresentation()
        onVisibilityChanged(false)
        retainedContextId = null
        lastConfirmed = null
    }

    private suspend fun encode(session: Session, edit: AudioEdit): File? {
        session.transferFailed = false
        var file: File? = null
        var transferred = false
        return try {
            val output = withContext(Dispatchers.IO) {
                File.createTempFile("screenlate_sentence_", ".opus", AnkiDroid.mediaDirectory(context)).also { file = it }
            }
            withContext(Dispatchers.IO) {
                val outcome = sentenceRecorder.export(edit, output, contextId = session.contextId)
                if (outcome?.isQuiet == true) withContext(Dispatchers.Main) {
                    if (active === session) Toast.makeText(context, R.string.audio_editor_quiet_warning, Toast.LENGTH_SHORT).show()
                }
                if (outcome == null) null else output
            }.also { transferred = it != null }
        } finally {
            if (!transferred) withContext(NonCancellable + Dispatchers.IO) { file?.delete() }
        }
    }

    private suspend fun preview(session: Session, request: AudioEditorOperation.Preview) {
        fun message(body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
            if (request.isCurrent) send(session) { put("previewSequence", request.sequence); body() }
        }
        fun finished() {
            if (session.operation.previewFinished(request)) message { put("playing", false) }
        }
        fun failed() {
            if (request.isCurrent) {
                finished()
                Toast.makeText(context, R.string.audio_editor_preview_failed, Toast.LENGTH_SHORT).show()
            }
        }
        try {
            val edit = request.edit
            if (edit == null) {
                sentenceRecorder.stopPreview()
                message { put("playing", false) }
            } else {
                sentenceRecorder.startPreview(edit, request.mode, request.cursor,
                    onProgress = { frame -> message { put("cursor", frame) } },
                    onComplete = ::finished,
                    onStopped = ::finished,
                    onError = { failure ->
                        Log.w("AudioEditorController", "Audio preview failed: $failure")
                        failed()
                    }, contextId = session.contextId, canStart = { request.isCurrent })
                message { put("playing", sentenceRecorder.isPreviewPlaying) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { failed() }
    }

    private fun rejectCommand(session: Session) = send(session) {
        put("phase", session.operation.phase.name.lowercase())
        put("error", context.getString(R.string.audio_editor_export_failed))
    }

    private fun send(session: Session, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        if (released || active !== session) return
        page.audioEditorMessage(buildJsonObject { put("operation", session.id); body() }.toString())
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putRetention(view: AudioTimelineView) {
        put("newFrames", view.newFrames)
        put("limit", view.retention.reason != null && view.retention.reason != RetentionEnd.CAPTURE_STOPPED)
        put("reason", view.retention.reason?.name?.lowercase() ?: "")
        put("remainingMillis", maxOf(0L, view.retention.cutoffMillis - SystemClock.elapsedRealtime()))
    }

    private fun ranges(rows: List<AudioRange>) = buildJsonArray {
        rows.forEach { range -> add(buildJsonArray { add(JsonPrimitive(range.start)); add(JsonPrimitive(range.endExclusive)) }) }
    }
}
