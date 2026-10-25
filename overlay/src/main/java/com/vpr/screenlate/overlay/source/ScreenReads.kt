package com.vpr.screenlate.overlay.source

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.photos.LookupSource

/** The reads of the screen a scan makes; [ScreenReads] lets them through. [S] is a screenshot. */
internal interface ScreenReader<S> {
    /** Opens the cloud recognizer's connection for a scan that may follow. */
    fun prepareCloud()

    suspend fun capture(): S

    /** Frees a screenshot that came after the source changed. */
    fun discard(shot: S)

    suspend fun readAppText(): OcrPage?
}

/** The screen stopped being the lookup source while it was read. */
internal class SourceChangedException : Exception("The lookup source changed")

/**
 * The one way the overlay reads the screen: the cloud connection opened on a touch, a scan's screenshot and app text,
 * and a note's screenshot. Each read runs only while the screen is the lookup source of the generation the scan was
 * started under ([open]), so a photo, or the Camera page without one, never falls back to the screen, and a read that
 * finishes after the source changed gives nothing.
 */
internal class ScreenReads<S>(private val source: () -> LookupSource, private val reader: ScreenReader<S>) {
    /** The generation of the screen source for a scan starting now; null when the screen is not the source. */
    fun open(): Long? = (source() as? LookupSource.Screen)?.generation

    fun isOpen(generation: Long): Boolean = source().let { it is LookupSource.Screen && it.generation == generation }

    fun prepareCloud() {
        if (open() != null) reader.prepareCloud()
    }

    /** Throws [SourceChangedException] when the screen is not the source of [generation], before or after the capture. */
    suspend fun capture(generation: Long): S {
        if (!isOpen(generation)) throw SourceChangedException()
        val shot = reader.capture()
        if (!isOpen(generation)) {
            reader.discard(shot)
            throw SourceChangedException()
        }
        return shot
    }

    /** Null as well when the screen is not the source of [generation]. */
    suspend fun readAppText(generation: Long): OcrPage? {
        if (!isOpen(generation)) return null
        return reader.readAppText()?.takeIf { isOpen(generation) }
    }
}

/** What the bubble, out over [open], does when the lookup source becomes [next]. */
internal enum class SourceChange {
    /** The same source: nothing changes. */
    NONE,

    /** The photo moved or was zoomed under the bubble; its text stays. */
    MOVED,

    /** The photo the bubble was out over is no longer shown: the bubble docks. */
    DOCK,

    /** The old source's scan is dropped; the new one has nothing open on it until the next scan. */
    CLOSE,

    /** The old source's scan is dropped and the photo's stored text takes its place, without recognition. */
    ATTACH,
}

internal fun sourceChange(open: LookupSource, next: LookupSource): SourceChange = when {
    open.generation == next.generation -> if (open == next) SourceChange.NONE else SourceChange.MOVED
    open is LookupSource.Photo && next !is LookupSource.Photo -> SourceChange.DOCK
    next is LookupSource.Photo -> SourceChange.ATTACH
    else -> SourceChange.CLOSE
}

/** The language the bubble shows: a recognized photo's while it is the source, the active language otherwise. */
internal fun glyphLanguage(source: LookupSource, active: Language): Language =
    (source as? LookupSource.Photo)?.recognition?.language ?: active
