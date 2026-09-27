package com.vpr.screenlate.dictionary.api.imports

import java.io.IOException

/**
 * What one dictionary takes in a collection export, from [YomitanBackup.measure].
 *
 * @property rows bank rows (terms, meta, kanji, tags).
 * @property textBytes bytes of the bank rows as the archive stores them.
 * @property mediaBytes decoded bytes of the media files.
 */
data class CollectionSize(val title: String, val rows: Long, val textBytes: Long, val mediaBytes: Long)

/** How a collection import writes its temporary archives, or why it cannot start. */
sealed interface CollectionSpacePlan {
    data object Uncompressed : CollectionSpacePlan

    data object Compressed : CollectionSpacePlan

    data class NotEnough(val neededBytes: Long, val freeBytes: Long) : CollectionSpacePlan
}

/** The free space a collection import cannot do without; see [CollectionSpacePlan.NotEnough]. */
class NotEnoughSpaceException(val neededBytes: Long, val freeBytes: Long) :
    IOException("Not enough free space: $neededBytes bytes needed, $freeBytes free")

/**
 * Estimates the storage a collection import needs. [YomitanBackup.convert] writes every archive before the first
 * one is imported; the import then installs them one by one and deletes each archive after it. The peak is
 * therefore the largest sum of the archives not yet imported and the dictionaries installed so far.
 *
 * The ratios come from a 2.7 GB export of 16 dictionaries and are chosen so that no dictionary there took more
 * than estimated: deflated banks took 5–38% of their text; installed dictionaries took 12–140% of their text,
 * the most for small rows (frequency lists, kanji), which the per-row and per-dictionary terms cover; media
 * files are kept as they are. Large glossary dictionaries are overestimated up to four times.
 */
object CollectionSpace {
    /** Kept free on top of the estimate, for the file system and the rest of the phone. */
    const val RESERVE_BYTES = 128L shl 20

    fun archiveBytes(size: CollectionSize, compressed: Boolean): Long =
        (if (compressed) size.textBytes * 2 / 5 else size.textBytes) + size.mediaBytes

    fun installedBytes(size: CollectionSize): Long = size.textBytes / 2 + size.rows * 120 + size.mediaBytes + (1L shl 20)

    /** The most space in use at once while importing [sizes] in their order, reserve not included. */
    fun peakBytes(sizes: List<CollectionSize>, compressed: Boolean): Long {
        var remaining = sizes.sumOf { archiveBytes(it, compressed) }
        var installed = 0L
        var peak = remaining
        for (size in sizes) {
            installed += installedBytes(size)
            peak = maxOf(peak, remaining + installed)
            remaining -= archiveBytes(size, compressed)
        }
        return peak
    }

    /** Uncompressed archives when they fit, compressed ones when only those fit, otherwise how much is missing. */
    fun plan(sizes: List<CollectionSize>, freeBytes: Long): CollectionSpacePlan {
        if (peakBytes(sizes, compressed = false) + RESERVE_BYTES <= freeBytes) return CollectionSpacePlan.Uncompressed
        val needed = peakBytes(sizes, compressed = true) + RESERVE_BYTES
        if (needed <= freeBytes) return CollectionSpacePlan.Compressed
        return CollectionSpacePlan.NotEnough(needed, freeBytes)
    }
}
