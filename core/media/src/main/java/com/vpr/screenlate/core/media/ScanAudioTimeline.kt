package com.vpr.screenlate.core.media

import java.util.UUID

internal data class AudioContextId(val session: UUID, val scan: Long)

internal data class AudioTimelineView(
    val contextId: AudioContextId,
    val scanFrame: Long,
    val source: AudioRange?,
    val revision: Long,
    val newFrames: Long,
    val retention: RetentionState,
)

/**
 * Serialized scan/editor policy over a separately owned PCM store. Word lookups do not create contexts.
 * Its ranges tell the writer what to retain; displayed revisions change only on first open or Refresh.
 */
internal class ScanAudioTimeline(private val clock: PcmClock, limits: RecordingLimits) {
    private val session = UUID.randomUUID()
    private var nextScan = 0L
    private var limits = limits
    private var context: Context? = null
    private var stoppedAtFrame: Long? = null

    fun updateLimits(limits: RecordingLimits) { this.limits = limits }

    fun acquireScan(nowMillis: Long, availableStartFrame: Long): AudioContextId? {
        if (stoppedAtFrame != null) return null
        val marker = clock.frameAtMillis(nowMillis)
        require(availableStartFrame in 0..marker)
        val start = maxOf(availableStartFrame, marker - clock.framesForMillis(limits.historyMillis))
        context?.retention?.release()
        val id = AudioContextId(session, Math.incrementExact(nextScan))
        nextScan = id.scan
        context = Context(id, marker, start, ContextRetention(nowMillis, limits))
        return id
    }

    /** Include the upcoming committed end when pruning before a write, so the ring keeps a fixed duration. */
    fun retainedRanges(committedEnd: Long, nowMillis: Long): List<AudioRange> {
        require(committedEnd >= 0)
        val end = minOf(committedEnd, stoppedAtFrame ?: committedEnd)
        val ring = AudioRange(maxOf(0, end - clock.framesForMillis(limits.historyMillis)), end)
        val protected = context?.let { ctx ->
            val cutoff = clock.frameAtMillis(ctx.retention.current(nowMillis).cutoffMillis)
            if (cutoff > ctx.startFrame) AudioRange(ctx.startFrame, cutoff) else null
        }
        return listOfNotNull(ring.takeIf { it.frames > 0 }, protected)
    }

    fun openEditor(id: AudioContextId, committedEnd: Long, nowMillis: Long, capacityAvailable: Boolean = true): AudioTimelineView? {
        val ctx = current(id) ?: return null
        if (!ctx.opened) {
            ctx.retention.editorReady(nowMillis, capacityAvailable)
            ctx.opened = true
            ctx.shownEnd = availableEnd(ctx, committedEnd, nowMillis)
            ctx.revision = 1
        }
        return view(ctx, committedEnd, nowMillis)
    }

    fun refresh(id: AudioContextId, committedEnd: Long, nowMillis: Long): AudioTimelineView? {
        val ctx = current(id) ?: return null
        check(ctx.opened)
        val end = availableEnd(ctx, committedEnd, nowMillis)
        if (end > ctx.shownEnd) {
            ctx.shownEnd = end
            ctx.revision = Math.incrementExact(ctx.revision)
        }
        return view(ctx, committedEnd, nowMillis)
    }

    fun view(id: AudioContextId, committedEnd: Long, nowMillis: Long): AudioTimelineView? =
        current(id)?.let { view(it, committedEnd, nowMillis) }

    fun stop(nowMillis: Long, reason: RetentionEnd = RetentionEnd.CAPTURE_STOPPED) {
        if (stoppedAtFrame != null) return
        stoppedAtFrame = clock.frameAtMillis(nowMillis)
        context?.retention?.stop(nowMillis, reason)
    }

    fun releaseContext(id: AudioContextId) {
        val ctx = current(id) ?: return
        ctx.retention.release()
        context = null
    }

    private fun current(id: AudioContextId): Context? = context?.takeIf { it.id == id }

    private fun availableEnd(ctx: Context, committedEnd: Long, nowMillis: Long): Long {
        require(committedEnd >= 0)
        val cutoff = clock.frameAtMillis(ctx.retention.current(nowMillis).cutoffMillis)
        return minOf(committedEnd, cutoff, clock.frameAtMillis(nowMillis)).coerceAtLeast(ctx.startFrame)
    }

    private fun view(ctx: Context, committedEnd: Long, nowMillis: Long): AudioTimelineView {
        val available = availableEnd(ctx, committedEnd, nowMillis)
        return AudioTimelineView(ctx.id, ctx.scanFrame,
            if (ctx.shownEnd > ctx.startFrame) AudioRange(ctx.startFrame, ctx.shownEnd) else null,
            ctx.revision, maxOf(0, available - ctx.shownEnd), ctx.retention.current(nowMillis))
    }

    private class Context(
        val id: AudioContextId,
        val scanFrame: Long,
        val startFrame: Long,
        val retention: ContextRetention,
        var opened: Boolean = false,
        var shownEnd: Long = startFrame,
        var revision: Long = 0,
    )
}
