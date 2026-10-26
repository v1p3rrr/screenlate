package com.vpr.screenlate.core.photos

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * The current photo and its committed text. It lives until it is replaced or cleared: through navigation, the app in
 * the background, process death and restarts. Closing the overlay or resetting settings does not touch it.
 */
interface PhotoDocuments {
    /** The current photo; null when there is none. Its stored text is read off the main thread. */
    val current: Flow<PhotoDocument?>

    /** A new file for the camera, or an import, to write a photo into; then [replaceCurrent] or [discard] it. */
    suspend fun newCaptureFile(): File

    /**
     * Makes the photo written to [capture] the current one; the previous current photo is deleted unless the history
     * keeps it. Throws [InvalidPhotoException], keeping the current photo, when [capture] is not a readable image.
     */
    suspend fun replaceCurrent(capture: File): PhotoDocument

    /** Deletes a capture that was cancelled or failed; the current photo stays. */
    suspend fun discard(capture: File)

    suspend fun clearCurrent()

    /** Stores progress without replacing committed text. [replace] starts a new attempt; updates must match its id. */
    suspend fun saveAttempt(documentId: Long, attempt: PhotoAttempt, replace: Boolean = false): Boolean

    /**
     * Stores [recognition] as the text of photo [documentId] if its text is still at [basedOn], the revision the
     * recognition started from. Returns the photo with its new text; null, storing nothing, when the photo is gone or
     * got another text meanwhile.
     */
    suspend fun commitRecognition(documentId: Long, basedOn: Int, recognition: PhotoRecognition): PhotoDocument?
}
