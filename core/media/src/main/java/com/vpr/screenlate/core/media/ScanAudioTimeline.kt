package com.vpr.screenlate.core.media

import java.util.UUID

data class AudioContextId(val session: UUID, val scan: Long)

data class AudioTimelineView(
    val contextId: AudioContextId,
    val scanFrame: Long,
    val source: AudioRange?,
    val revision: Long,
    val newFrames: Long,
    val retention: RetentionState,
    val sampleRate: Int = 48_000,
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
    private var lastSeenMillis: Long = 0L

    var discontinuityFloor: Long = 0L
        private set

    val hasContext: Boolean get() = context != null

    fun updateLimits(limits: RecordingLimits) { this.limits = limits }

    fun acquireScan(nowMillis: Long, availableStartFrame: Long, committedEnd: Long = Long.MAX_VALUE): AudioContextId? {
        require(availableStartFrame >= 0)
        lastSeenMillis = maxOf(lastSeenMillis, nowMillis)
        if (stoppedAtFrame != null) return null
        val floor = maxOf(availableStartFrame, discontinuityFloor)
        val rawMarker = clock.frameAtMillis(nowMillis)
        val marker = if (committedEnd != Long.MAX_VALUE && rawMarker > committedEnd + clock.framesForMillis(SUSPEND_GAP_MILLIS)) {
            maxOf(floor, committedEnd)
        } else {
            maxOf(rawMarker, floor)
        }
        val start = maxOf(floor, marker - clock.framesForMillis(limits.historyMillis))
        context?.retention?.release()
        val id = AudioContextId(session, Math.incrementExact(nextScan))
        nextScan = id.scan
        context = Context(id, marker, start, ContextRetention(nowMillis, limits))
        return id
    }

    /** Include the upcoming committed end when pruning before a write, so the ring keeps a fixed duration. */
    fun retainedRanges(committedEnd: Long, nowMillis: Long): List<AudioRange> {
        require(committedEnd >= 0)
        lastSeenMillis = maxOf(lastSeenMillis, nowMillis)
        val end = minOf(committedEnd, stoppedAtFrame ?: committedEnd)
        val ringStart = maxOf(discontinuityFloor, end - clock.framesForMillis(limits.historyMillis))
        val ring = if (end > ringStart) AudioRange(ringStart, end) else null
        val protected = context?.let { ctx ->
            val cappedCutoff = minOf(clock.frameAtMillis(ctx.retention.current(nowMillis).cutoffMillis), ctx.maxEndFrame ?: Long.MAX_VALUE)
            if (cappedCutoff > ctx.startFrame) AudioRange(ctx.startFrame, cappedCutoff) else null
        }
        return listOfNotNull(ring, protected)
    }

    fun onSuspendGap(gapFrame: Long, endFrame: Long, endNanos: Long, nowMillis: Long) {
        lastSeenMillis = maxOf(lastSeenMillis, nowMillis)
        discontinuityFloor = maxOf(discontinuityFloor, gapFrame)
        context?.let { ctx ->
            val preGapCutoff = clock.frameAtMillis(ctx.retention.current(nowMillis).cutoffMillis)
            val cap = minOf(gapFrame, preGapCutoff)
            ctx.maxEndFrame = ctx.maxEndFrame?.let { minOf(it, cap) } ?: cap
            ctx.retention.stop(nowMillis, RetentionEnd.CAPTURE_STOPPED)
        }
        clock.recalibrate(endFrame, endNanos)
    }

    fun prepareEditor(id: AudioContextId, committedEnd: Long, nowMillis: Long): AudioTimelineView? {
        lastSeenMillis = maxOf(lastSeenMillis, nowMillis)
        val ctx = current(id) ?: return null
        if (!ctx.prepared) {
            ctx.prepared = true
            ctx.shownEnd = availableEnd(ctx, committedEnd, nowMillis)
            ctx.revision = 1
        }
        return view(ctx, committedEnd, nowMillis).copy(source = AudioRange(ctx.startFrame, ctx.shownEnd))
    }

    fun openEditor(id: AudioContextId, committedEnd: Long, nowMillis: Long, capacityAvailable: Boolean = true): AudioTimelineView? {
        lastSeenMillis = maxOf(lastSeenMillis, nowMillis)
        val ctx = current(id) ?: return null
        if (!ctx.opened) {
            ctx.retention.editorReady(nowMillis, capacityAvailable)
            ctx.opened = true
            ctx.prepared = true
            if (ctx.shownEnd != availableEnd(ctx, committedEnd, nowMillis) || ctx.revision == 0L) ctx.revision++
            ctx.shownEnd = availableEnd(ctx, committedEnd, nowMillis)
        }
        return view(ctx, committedEnd, nowMillis)
    }

    fun refresh(id: AudioContextId, committedEnd: Long, nowMillis: Long): AudioTimelineView? {
        lastSeenMillis = maxOf(lastSeenMillis, nowMillis)
        val ctx = current(id) ?: return null
        check(ctx.opened)
        val end = availableEnd(ctx, committedEnd, nowMillis)
        if (end > ctx.shownEnd) {
            ctx.shownEnd = end
            ctx.revision = Math.incrementExact(ctx.revision)
        }
        return view(ctx, committedEnd, nowMillis)
    }

    fun view(id: AudioContextId, committedEnd: Long, nowMillis: Long): AudioTimelineView? {
        lastSeenMillis = maxOf(lastSeenMillis, nowMillis)
        return current(id)?.let { view(it, committedEnd, nowMillis) }
    }

    fun stop(nowMillis: Long, reason: RetentionEnd = RetentionEnd.CAPTURE_STOPPED) {
        if (stoppedAtFrame != null) return
        val effectiveMillis = maxOf(nowMillis, lastSeenMillis)
        lastSeenMillis = effectiveMillis
        stoppedAtFrame = clock.frameAtMillis(effectiveMillis)
        context?.retention?.stop(effectiveMillis, reason)
    }

    fun releaseContext(id: AudioContextId) {
        val ctx = current(id) ?: return
        ctx.retention.release()
        context = null
    }

    private fun current(id: AudioContextId): Context? = context?.takeIf { it.id == id }

    private fun availableEnd(ctx: Context, committedEnd: Long, nowMillis: Long): Long {
        require(committedEnd >= 0)
        val cappedCommitted = minOf(committedEnd, ctx.maxEndFrame ?: committedEnd)
        val cutoff = clock.frameAtMillis(ctx.retention.current(nowMillis).cutoffMillis)
        return minOf(cappedCommitted, cutoff, clock.frameAtMillis(nowMillis)).coerceAtLeast(ctx.startFrame)
    }

    private fun view(ctx: Context, committedEnd: Long, nowMillis: Long): AudioTimelineView {
        val available = availableEnd(ctx, committedEnd, nowMillis)
        return AudioTimelineView(ctx.id, ctx.scanFrame,
            if (ctx.shownEnd > ctx.startFrame) AudioRange(ctx.startFrame, ctx.shownEnd) else null,
            ctx.revision, maxOf(0, available - ctx.shownEnd), ctx.retention.current(nowMillis), clock.sampleRate)
    }

    private class Context(
        val id: AudioContextId,
        val scanFrame: Long,
        val startFrame: Long,
        val retention: ContextRetention,
        var opened: Boolean = false,
        var prepared: Boolean = false,
        var shownEnd: Long = startFrame,
        var revision: Long = 0,
        var maxEndFrame: Long? = null,
    )

    private companion object {
        const val SUSPEND_GAP_MILLIS = 5_000L
    }
}
