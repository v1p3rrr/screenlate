package com.vpr.screenlate.overlay.anki

import com.vpr.screenlate.core.media.AudioEdit
import com.vpr.screenlate.core.media.AudioRange
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Main-confined owner of one editor result, immutable edits and cancellable work. */
internal class AudioEditorOperation(
    parent: CoroutineScope,
    source: AudioRange,
    initial: AudioEdit,
    private val onChanged: () -> Unit = {},
    opening: Boolean = false,
    private val export: suspend (AudioEdit) -> File?,
) {
    enum class Phase { OPENING, EDITING, EXPORTING, FAILED, FINISHED }

    sealed interface Result {
        data class Audio(val file: File, val edit: AudioEdit) : Result
        data object WithoutAudio : Result
        data object Cancelled : Result
    }

    private val completion = CompletableDeferred<Result>()
    private val job = SupervisorJob(parent.coroutineContext[Job])
    val scope = CoroutineScope(parent.coroutineContext + job)
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
        completion.complete(value)
        job.cancel()
        onChanged()
        return true
    }

    private fun contains(range: AudioRange): Boolean =
        range.start >= source.start && range.endExclusive <= source.endExclusive
}
