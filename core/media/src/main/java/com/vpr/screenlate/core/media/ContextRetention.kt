package com.vpr.screenlate.core.media

internal enum class RetentionStage { WAITING, EDITOR, SEALED, RELEASED }
internal enum class RetentionEnd { WAITING_LIMIT, EDITOR_LIMIT, CAPTURE_STOPPED, RESOURCE_LIMIT }

internal data class RetentionState(
    val stage: RetentionStage,
    val cutoffMillis: Long,
    val firstEditorMillis: Long?,
    val reason: RetentionEnd?,
)

/** Pure deadline policy; callers serialize events and apply the cutoff to source time, not callback time. */
internal class ContextRetention(scanMillis: Long, private val limits: RecordingLimits) {
    private var lastMillis = scanMillis
    private var state = RetentionState(
        RetentionStage.WAITING,
        Math.addExact(scanMillis, limits.waitingMillis),
        null,
        null,
    )

    init { require(scanMillis >= 0) }

    fun current(nowMillis: Long): RetentionState {
        advance(nowMillis)
        return state
    }

    /** True only for the single admitted transition, never for a reopened or already sealed editor. */
    fun editorReady(nowMillis: Long, capacityAvailable: Boolean = true): Boolean {
        advance(nowMillis)
        if (state.stage != RetentionStage.WAITING) return false
        val deadline = Math.addExact(nowMillis, limits.editorMillis)
        state = if (capacityAvailable) {
            RetentionState(RetentionStage.EDITOR, deadline, nowMillis, null)
        } else {
            RetentionState(RetentionStage.SEALED, nowMillis, nowMillis, RetentionEnd.RESOURCE_LIMIT)
        }
        return capacityAvailable
    }

    fun stop(nowMillis: Long, reason: RetentionEnd = RetentionEnd.CAPTURE_STOPPED) {
        require(reason == RetentionEnd.CAPTURE_STOPPED || reason == RetentionEnd.RESOURCE_LIMIT)
        advance(nowMillis)
        if (state.stage == RetentionStage.WAITING || state.stage == RetentionStage.EDITOR) {
            state = state.copy(stage = RetentionStage.SEALED, cutoffMillis = nowMillis, reason = reason)
        }
    }

    fun release() { state = state.copy(stage = RetentionStage.RELEASED) }

    private fun advance(nowMillis: Long) {
        require(nowMillis >= lastMillis) { "Elapsed time must not move backwards" }
        lastMillis = nowMillis
        if ((state.stage == RetentionStage.WAITING || state.stage == RetentionStage.EDITOR) && nowMillis >= state.cutoffMillis) {
            val reason = if (state.stage == RetentionStage.WAITING) RetentionEnd.WAITING_LIMIT else RetentionEnd.EDITOR_LIMIT
            state = state.copy(stage = RetentionStage.SEALED, reason = reason)
        }
    }
}
