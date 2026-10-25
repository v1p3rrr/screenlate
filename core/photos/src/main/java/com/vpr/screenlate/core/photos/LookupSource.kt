package com.vpr.screenlate.core.photos

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the bubble reads. Exactly one source applies at a time, and only the screen source reads the screen: a photo
 * or the Camera page without one never falls back to a screenshot or the apps' own text.
 */
sealed interface LookupSource {
    /**
     * Changes whenever the source does: another kind, photo or recognition. Results and callbacks of a scan started
     * under another generation are stale. Moving the photo ([Photo.viewport]) keeps it.
     */
    val generation: Long

    /** Screenshots and the apps' own text, as in any other app. */
    data class Screen(override val generation: Long) : LookupSource

    /**
     * The Camera page shows nothing to read: the live camera, a photo loading, one not recognized yet or with
     * unreadable text. Nothing is scanned.
     */
    data class NoText(override val generation: Long) : LookupSource

    /** A recognized photo shown by the Camera page; its stored [recognition] is the text, placed by [viewport]. */
    data class Photo(
        override val generation: Long,
        val documentId: Long,
        val revision: Int,
        val recognition: PhotoRecognition,
        val viewport: PhotoViewport,
    ) : LookupSource
}

/**
 * The lookup source, set by the Camera page while it is in the foreground and read by the overlay. The page opens a
 * [Presentation] when it starts showing and closes it when it stops (another page, the app in the background); the
 * screen is the source otherwise. Independent of the accessibility service: the page works without it.
 */
@Singleton
class LookupSources @Inject constructor() {
    private val state = MutableStateFlow<LookupSource>(LookupSource.Screen(0))
    private var generation = 0L
    private var presenter: Presentation? = null

    val source: StateFlow<LookupSource> = state.asStateFlow()

    /** Starts presenting the Camera page, with nothing to read until [Presentation.showPhoto]; replaces an older one. */
    fun present(): Presentation = synchronized(this) {
        Presentation().also {
            presenter = it
            state.value = LookupSource.NoText(++generation)
        }
    }

    /** What the Camera page shows; calls after [close], or once a newer presentation started, change nothing. */
    inner class Presentation internal constructor() {
        fun showNothing() = update { current ->
            if (current is LookupSource.NoText) current else LookupSource.NoText(++generation)
        }

        /** The photo [documentId] at [revision] with its [recognition], placed by [viewport]. */
        fun showPhoto(documentId: Long, revision: Int, recognition: PhotoRecognition, viewport: PhotoViewport) = update { current ->
            if (current is LookupSource.Photo && current.documentId == documentId && current.revision == revision) {
                current.copy(recognition = recognition, viewport = viewport)
            } else {
                LookupSource.Photo(++generation, documentId, revision, recognition, viewport)
            }
        }

        /** The shown photo moved or was zoomed; its text and the generation stay. */
        fun moveViewport(viewport: PhotoViewport) = update { current ->
            if (current is LookupSource.Photo) current.copy(viewport = viewport) else current
        }

        /** The page no longer shows: the screen is the source again. */
        fun close() {
            synchronized(this@LookupSources) {
                if (presenter !== this) return
                presenter = null
                state.value = LookupSource.Screen(++generation)
            }
        }

        private fun update(change: (LookupSource) -> LookupSource) {
            synchronized(this@LookupSources) {
                if (presenter !== this) return
                state.value = change(state.value)
            }
        }
    }
}
