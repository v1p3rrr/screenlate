package com.vpr.screenlate.core.media

/** Half-open positions in one recording session's PCM frame space. */
data class AudioRange(val start: Long, val endExclusive: Long) {
    init {
        require(start >= 0 && endExclusive >= start)
    }

    val frames: Long get() = endExclusive - start

    fun intersect(other: AudioRange): AudioRange? {
        val left = maxOf(start, other.start)
        val right = minOf(endExclusive, other.endExclusive)
        return if (left < right) AudioRange(left, right) else null
    }
}

/** Immutable source-coordinate edits. Exclusions never change the original frame positions. */
class AudioEdit(val selection: AudioRange, exclusions: List<AudioRange> = emptyList()) {
    val exclusions: List<AudioRange> = normalize(selection, exclusions)
    val kept: List<AudioRange> = buildList {
        var position = selection.start
        for (cut in this@AudioEdit.exclusions) {
            if (position < cut.start) add(AudioRange(position, cut.start))
            position = cut.endExclusive
        }
        if (position < selection.endExclusive) add(AudioRange(position, selection.endExclusive))
    }
    val outputFrames: Long = kept.sumOf { it.frames }

    /** A result-end position maps to the selection end; positions in cuts are skipped. */
    fun sourceFrame(resultOffset: Long): Long {
        require(resultOffset in 0..outputFrames)
        var remaining = resultOffset
        for (range in kept) {
            if (remaining < range.frames) return range.start + remaining
            remaining -= range.frames
        }
        return selection.endExclusive
    }

    /** Source positions in an exclusion map to the next kept position, or the result end. */
    fun resultOffset(sourceFrame: Long): Long {
        val frame = sourceFrame.coerceIn(selection.start, selection.endExclusive)
        var offset = 0L
        for (range in kept) {
            if (frame <= range.start) return offset
            offset += minOf(frame, range.endExclusive) - range.start
            if (frame < range.endExclusive) return offset
        }
        return outputFrames
    }

    override fun equals(other: Any?): Boolean = other is AudioEdit &&
        selection == other.selection && exclusions == other.exclusions

    override fun hashCode(): Int = 31 * selection.hashCode() + exclusions.hashCode()

    private companion object {
        fun normalize(selection: AudioRange, cuts: List<AudioRange>): List<AudioRange> {
            val sorted = cuts.mapNotNull(selection::intersect).sortedBy { it.start }
            return buildList {
                for (cut in sorted) {
                    val previous = lastOrNull()
                    if (previous != null && cut.start <= previous.endExclusive) {
                        this[lastIndex] = AudioRange(previous.start, maxOf(previous.endExclusive, cut.endExclusive))
                    } else {
                        add(cut)
                    }
                }
            }
        }
    }
}
