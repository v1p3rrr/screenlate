package com.vpr.screenlate.core.photos

import com.vpr.screenlate.core.common.Language
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Photos kept apart from the current one. Saving does not copy a photo: the current photo can be in the history, and
 * a photo that leaves the history is deleted, the current one included.
 */
interface PhotoHistory {
    /** The photos in the history, the latest saved first. */
    val entries: Flow<List<HistoryEntry>>

    /** Adds photo [documentId], then deletes the oldest photos beyond [limits]; false when the photo is gone. */
    suspend fun save(documentId: Long, limits: HistoryLimits): Boolean

    /**
     * Makes history photo [documentId] the current one; the previous current photo is deleted unless the history
     * keeps it. Null when the photo is not in the history.
     */
    suspend fun open(documentId: Long): PhotoDocument?

    /** Deletes history photos [ids]; the current photo among them is closed. */
    suspend fun remove(ids: Set<Long>)

    /** [remove] for every photo in the history. */
    suspend fun clear()

    /** How many of the oldest photos [limits] would delete. */
    suspend fun excess(limits: HistoryLimits): Int

    /** Deletes the oldest photos beyond [limits], the current one too when it is among them. */
    suspend fun trim(limits: HistoryLimits)
}

/**
 * A photo in the history.
 *
 * @property language the language of its recognized text; null before the first recognition.
 * @property thumbnail a small upright copy; null when it could not be made.
 * @property bytes the image, its thumbnail and its recognized text on the disk.
 */
data class HistoryEntry(
    val id: Long,
    val createdAt: Long,
    val language: Language?,
    val thumbnail: File?,
    val bytes: Long,
)

/** The history's size; 0 means no limit. A megabyte is 1,000,000 bytes, as Android shows file sizes. */
data class HistoryLimits(val count: Int = 0, val megabytes: Int = 0) {
    init { require(count >= 0 && megabytes >= 0) }

    /**
     * How many of the latest photos, whose sizes are [sizes] from the latest, stay within the limits. The latest stays
     * even when it alone is larger than the size limit.
     */
    fun kept(sizes: List<Long>): Int {
        var total = 0L
        sizes.forEachIndexed { index, size ->
            total += size
            val over = (count > 0 && index + 1 > count) || (megabytes > 0 && total > megabytes * MEGABYTE)
            if (over && index > 0) return index
        }
        return sizes.size
    }

    private companion object { const val MEGABYTE = 1_000_000L }
}
