package com.vpr.screenlate.core.media

internal object InitialSelection {
    fun suggest(scanFrame: Long, shown: AudioRange, sampleRate: Int): AudioRange? {
        require(scanFrame >= 0 && sampleRate > 0)
        val before = sampleRate * 15L
        val after = sampleRate * 5L
        return shown.intersect(AudioRange(maxOf(0, scanFrame - before), Math.addExact(scanFrame, after)))
    }

    /** [leadingQuiet] is supplied only by a confident classifier, never inferred from a missing file. */
    fun refine(selection: AudioRange, leadingQuiet: AudioRange?, sampleRate: Int): AudioRange {
        require(sampleRate > 0)
        val quiet = leadingQuiet?.intersect(selection) ?: return selection
        if (quiet.start != selection.start || quiet.endExclusive == selection.endExclusive || quiet.frames <= sampleRate * 5L) {
            return selection
        }
        val padding = sampleRate * 150L / 1000
        return AudioRange(maxOf(selection.start, quiet.endExclusive - padding), selection.endExclusive)
    }
}
