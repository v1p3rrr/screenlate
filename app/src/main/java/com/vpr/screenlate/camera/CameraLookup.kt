package com.vpr.screenlate.camera

import com.vpr.screenlate.core.photos.LookupSources
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.PhotoViewport

/** What the Camera page shows, as far as the bubble is concerned. */
sealed interface CameraView {
    /** The live camera, the permission prompt, a photo loading or one that cannot be opened. */
    data object Nothing : CameraView

    /** [document] drawn where [viewport] says. */
    data class Photo(val document: PhotoDocument, val viewport: PhotoViewport) : CameraView
}

/**
 * Keeps the bubble's lookup source in step with the Camera page: what the page shows while it is in the foreground,
 * the screen otherwise. A photo without readable text counts as nothing to read.
 */
class CameraLookup(private val sources: LookupSources) {
    private var presentation: LookupSources.Presentation? = null
    private var view: CameraView = CameraView.Nothing

    fun onForeground() {
        if (presentation != null) return
        presentation = sources.present()
        publish()
    }

    fun onBackground() {
        presentation?.close()
        presentation = null
    }

    fun show(view: CameraView) {
        this.view = view
        publish()
    }

    private fun publish() {
        val presentation = presentation ?: return
        val photo = view as? CameraView.Photo
        val text = photo?.document?.text as? PhotoText.Recognized
        if (photo == null || text == null) {
            presentation.showNothing()
        } else {
            presentation.showPhoto(photo.document.id, photo.document.revision, text.recognition, photo.viewport)
        }
    }
}
