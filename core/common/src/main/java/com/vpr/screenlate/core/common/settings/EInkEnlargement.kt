package com.vpr.screenlate.core.common.settings

/**
 * The bubble and popup text sizes changed by "Make larger" when e-ink mode was turned on; null for a size it left as
 * it was.
 */
data class EInkEnlargement(val bubble: Change?, val font: Change?) {
    data class Change(val before: Int, val after: Int)

    /** Sizes to set when e-ink mode goes off: the ones still at their enlarged value go back, hand-set ones stay. */
    fun restore(bubbleNow: Int, fontNow: Int): Restore = Restore(
        bubble = bubble?.takeIf { it.after == bubbleNow }?.before,
        font = font?.takeIf { it.after == fontNow }?.before,
    )

    data class Restore(val bubble: Int?, val font: Int?)

    /**
     * This enlargement followed by [next], made while e-ink mode stayed on: a size still at this one's value goes back
     * to the size from before both; one set by hand in between goes back to that.
     */
    fun followedBy(next: EInkEnlargement): EInkEnlargement =
        EInkEnlargement(bubble = chain(bubble, next.bubble), font = chain(font, next.font))

    private fun chain(first: Change?, second: Change?): Change? = when {
        first == null -> second
        second == null -> first
        second.before == first.after -> Change(first.before, second.after)
        else -> second
    }

    companion object {
        /** A bubble of at least [minBubble] and text [fontStep] larger, at most [maxFont]; larger sizes stay. */
        fun of(bubbleNow: Int, fontNow: Int, minBubble: Int, fontStep: Int, maxFont: Int): EInkEnlargement {
            val font = (fontNow + fontStep).coerceAtMost(maxFont)
            return EInkEnlargement(
                bubble = Change(bubbleNow, minBubble).takeIf { bubbleNow < minBubble },
                font = Change(fontNow, font).takeIf { font > fontNow },
            )
        }
    }
}
