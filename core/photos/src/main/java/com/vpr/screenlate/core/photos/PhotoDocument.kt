package com.vpr.screenlate.core.photos

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.PhotoTile
import com.vpr.screenlate.core.ocr.PhotoTilePage
import com.vpr.screenlate.core.ocr.PhotoTiles
import java.io.File

/**
 * A photograph in the app's private storage. Coordinates on it are in oriented pixels ([PhotoOrientation]): the
 * stored image turned upright, [width] × [height].
 *
 * @property image the stored image, as the camera wrote it.
 * @property revision changes with every recognition committed for the photo; 0 before the first.
 * @property saved the photo is in the history ([PhotoHistory]).
 */
data class PhotoDocument(
    val id: Long,
    val image: File,
    val rawWidth: Int,
    val rawHeight: Int,
    val orientation: PhotoOrientation,
    val createdAt: Long,
    val revision: Int,
    val text: PhotoText,
    val attempt: PhotoAttempt? = null,
    val attemptUnreadable: Boolean = false,
    val saved: Boolean = false,
) {
    val width: Int get() = if (orientation.swapsAxes) rawHeight else rawWidth
    val height: Int get() = if (orientation.swapsAxes) rawWidth else rawHeight

    /** Whether [tile] is the pass over the whole photo ([PhotoTiles.overview]), which is not one of its regions. */
    fun isOverview(tile: PhotoTile): Boolean = PhotoTiles.isOverview(tile, width, height)
}

/** The recognized text kept with a photo. */
sealed interface PhotoText {
    /** The photo has not been recognized yet. */
    data object None : PhotoText

    data class Recognized(val recognition: PhotoRecognition) : PhotoText

    /** The stored text is damaged or was written by a newer version; the image is kept and can be recognized again. */
    data object Unreadable : PhotoText
}

/**
 * The committed result of one recognition of a photo, in its oriented pixels.
 *
 * @property language the language the photo was recognized in; lookups use it, not a language chosen since.
 * @property settings what the recognition ran with, to tell when the current settings differ.
 * @property failed regions whose recognition failed, shown and retried apart from the rest; a failed overview pass
 *   ([PhotoTiles.overview]) is not one.
 * @property recognizedAt milliseconds since the epoch.
 */
data class PhotoRecognition(
    val language: Language,
    val settings: PhotoOcrSettings,
    val page: OcrPage,
    val failed: List<FailedRegion> = emptyList(),
    val recognizedAt: Long,
    /** Original tile observations for retry; empty for snapshots written before tile processing was introduced. */
    val tiles: List<PhotoTilePage> = emptyList(),
)

/**
 * The OCR settings a photo is recognized with, shared with screen scans and passed in by the caller.
 *
 * @property deviceModel the id of the language's on-device model (`OcrModelChoices`); null when none is used.
 */
data class PhotoOcrSettings(val engines: OcrEngines, val deviceModel: String? = null)

/** A part of the photo that was not recognized, in its oriented pixels. */
data class FailedRegion(val box: Box, val cause: FailureCause)

/** Why a region was not recognized; a cause unknown to this version reads as [OTHER]. */
enum class FailureCause { OFFLINE, NETWORK, REFUSED, SERVER, DEVICE, OTHER }

/** The file given to [PhotoDocuments.replaceCurrent] is not an image; the current photo stays. */
class InvalidPhotoException : Exception("Not a readable image")

/** An image given to [PhotoImports.copy] is larger than [MAX_IMPORT_BYTES]; nothing is kept. */
class PhotoTooLargeException : Exception("Larger than the import limit")
