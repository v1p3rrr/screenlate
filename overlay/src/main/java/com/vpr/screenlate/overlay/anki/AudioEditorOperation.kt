package com.vpr.screenlate.overlay.anki

import com.vpr.screenlate.core.media.AudioEdit
import com.vpr.screenlate.core.media.AudioRange
import com.vpr.screenlate.core.media.PreviewMode
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Main-confined owner of one editor result, immutable edits and cancellable work. */
internal class AudioEditorOperation(
    parent: CoroutineScope,
    source: AudioRange,
    initial: AudioEdit,
    private val onChanged: () -> Unit = {},
    opening: Boolean = false,
    private val preview: suspend (Preview) -> Unit = {},
    private val export: suspend (AudioEdit) -> File?,
) {
    enum class Phase { OPENING, EDITING, EXPORTING, FAILED, FINISHED }

    sealed interface Result {
        data class Audio(val file: File, val edit: AudioEdit) : Result
        data object WithoutAudio : Result
        data object Cancelled : Result
    }

    /** A null edit requests Stop. The guard must be checked before starting native output and callbacks. */
    class Preview internal constructor(
        val edit: AudioEdit?,
        val mode: PreviewMode,
        val cursor: Long,
        val sequence: Long,
        private val current: () -> Boolean,
    ) {
        val isCurrent: Boolean get() = current()
    }

    private val completion = CompletableDeferred<Result>()
    private val job = SupervisorJob(parent.coroutineContext[Job])
    val scope = CoroutineScope(parent.coroutineContext + job)
    private val previews = Channel<Preview>(Channel.CONFLATED)
    private val previewGeneration = AtomicLong()
    private var wantsPlayback = false
    val result: Deferred<Result> get() = completion
    var phase = if (opening) Phase.OPENING else Phase.EDITING
        private set
    var source = source
        private set
    var sourceRevision = 0L
        private set
    var sequence = 0L
        private set
    var editRevision = 0L
        private set
    var edit = initial
        private set

    init {
        require(contains(initial.selection))
        scope.launch { for (request in previews) if (request.isCurrent) preview(request) }
    }

    fun play(mode: PreviewMode, cursor: Long, sequence: Long) {
        if (phase != Phase.EDITING && phase != Phase.FAILED) return
        wantsPlayback = true
        enqueuePreview(edit, mode, cursor, sequence)
    }

    fun seek(mode: PreviewMode, cursor: Long, sequence: Long) {
        if (wantsPlayback) play(mode, cursor, sequence)
    }

    fun pause(sequence: Long) {
        if (sequence < this.sequence || phase == Phase.FINISHED) return
        this.sequence = sequence
        wantsPlayback = false
        enqueuePreview(null, PreviewMode.RESULT, 0, sequence)
    }

    fun previewFinished(request: Preview): Boolean {
        if (!request.isCurrent) return false
        wantsPlayback = false
        return true
    }

    private fun enqueuePreview(edit: AudioEdit?, mode: PreviewMode, cursor: Long, sequence: Long) {
        val generation = previewGeneration.incrementAndGet()
        previews.trySend(Preview(edit, mode, cursor, sequence) {
            job.isActive && previewGeneration.get() == generation
        })
    }

    fun accept(sequence: Long, sourceRevision: Long, edit: AudioEdit): Boolean {
        if (phase == Phase.FINISHED || phase == Phase.EXPORTING || phase == Phase.OPENING || sequence <= this.sequence ||
            sourceRevision != this.sourceRevision || !contains(edit.selection)
        ) return false
        this.sequence = sequence
        if (this.edit != edit) {
            this.edit = edit
            editRevision++
        }
        return true
    }

    fun ready(range: AudioRange, initial: AudioEdit): Boolean {
        if (phase != Phase.OPENING || initial.selection.start < range.start || initial.selection.endExclusive > range.endExclusive ||
            !append(range)
        ) return false
        edit = initial
        phase = Phase.EDITING
        return true
    }

    /** Only a continuous extension of this source is eligible for Refresh. */
    fun append(range: AudioRange): Boolean {
        if (phase == Phase.EXPORTING || phase == Phase.FINISHED ||
            range.start != source.start || range.endExclusive < source.endExclusive
        ) return false
        if (source != range) {
            source = range
            sourceRevision++
        }
        return true
    }

    fun save() {
        if (phase == Phase.OPENING || phase == Phase.EXPORTING || phase == Phase.FINISHED || edit.outputFrames <= 0) return
        val confirmed = edit
        pause(sequence)
        phase = Phase.EXPORTING
        onChanged()
        scope.launch {
            var file: File? = null
            var transferred = false
            try {
                file = export(confirmed)
                if (phase == Phase.FINISHED) return@launch
                if (file == null) {
                    phase = Phase.FAILED
                    onChanged()
                } else {
                    transferred = finish(Result.Audio(file, confirmed))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (phase != Phase.FINISHED) {
                    phase = Phase.FAILED
                    onChanged()
                }
            } finally {
                if (!transferred && file != null) withContext(NonCancellable + Dispatchers.IO) { file.delete() }
            }
        }
    }

    fun withoutAudio() { finish(Result.WithoutAudio) }
    fun cancel() { finish(Result.Cancelled) }

    suspend fun awaitClosed() { job.join() }

    private fun finish(value: Result): Boolean {
        if (phase == Phase.FINISHED) return false
        phase = Phase.FINISHED
        wantsPlayback = false
        previewGeneration.incrementAndGet()
        previews.close()
        completion.complete(value)
        job.cancel()
        onChanged()
        return true
    }

    private fun contains(range: AudioRange): Boolean =
        range.start >= source.start && range.endExclusive <= source.endExclusive
}
