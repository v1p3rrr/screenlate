// The support library backports fixes the platform class has had since Android 7.1; minSdk is 30.
@file:SuppressLint("ExifInterface")

package com.vpr.screenlate.core.photos.store

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.photos.HistoryEntry
import com.vpr.screenlate.core.photos.HistoryLimits
import com.vpr.screenlate.core.photos.InvalidPhotoException
import com.vpr.screenlate.core.photos.PhotoDocument
import com.vpr.screenlate.core.photos.PhotoDocuments
import com.vpr.screenlate.core.photos.PhotoHistory
import com.vpr.screenlate.core.photos.PhotoThumbnails
import com.vpr.screenlate.core.photos.PhotoOrientation
import com.vpr.screenlate.core.photos.PhotoRecognition
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.PhotoAttempt
import com.vpr.screenlate.core.photos.PhotoImage
import com.vpr.screenlate.core.photos.ThumbnailMaker
import java.nio.file.Files
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Photos in `noBackupFilesDir/photos/<uuid>/`: the image as the camera wrote it and the committed recognition, with
 * their metadata in Room. A capture is written in `.staging/` and moved into place with a rename, and a recognition is
 * written next to its photo before the database names it, so an interrupted write leaves the committed photo and text
 * as they were. What an interruption leaves behind is deleted before the first change after a start. A photo in the
 * history also has a thumbnail there.
 */
@Singleton
class PhotoStore internal constructor(
    private val root: File,
    private val index: PhotoIndex,
    private val probe: ImageProbe,
    private val thumbnails: ThumbnailMaker,
    private val clock: () -> Long,
    private val io: CoroutineDispatcher,
) : PhotoDocuments, PhotoHistory {

    @Inject
    constructor(@ApplicationContext context: Context, database: PhotoDatabase) : this(
        root = File(context.noBackupFilesDir, ROOT),
        index = RoomPhotoIndex(database.photoDao()),
        probe = BitmapImageProbe,
        thumbnails = PhotoThumbnails,
        clock = System::currentTimeMillis,
        io = Dispatchers.IO,
    )

    private val mutex = Mutex()
    private val staging = File(root, STAGING)
    private var tidied = false

    override suspend fun pinImage(documentId: Long): PhotoImage? {
        var pinned: PhotoImage? = null
        try {
            locked {
                val row = index.get(documentId) ?: return@locked
                val image = imageOf(row)
                if (!image.isFile) return@locked
                val directory = File(root, ".pins").apply { mkdirs() }
                val file = File(directory, UUID.randomUUID().toString())
                try {
                    try { Files.createLink(file.toPath(), image.toPath()) }
                    catch (_: IOException) { image.copyTo(file) }
                    catch (_: UnsupportedOperationException) { image.copyTo(file) }
                    pinned = PhotoImage(documentOf(row, PhotoText.None).copy(image = file))
                } catch (e: Exception) { file.delete(); throw e }
            }
            return pinned
        } catch (e: Exception) { pinned?.close(); throw e }
    }

    override val current: Flow<PhotoDocument?> = index.observeCurrent()
        .distinctUntilChanged()
        .map { mutex.withLock { index.current()?.let(::load) } }
        .distinctUntilChanged()
        .flowOn(io)

    override suspend fun newCaptureFile(): File = locked {
        staging.mkdirs()
        File(staging, "${UUID.randomUUID()}$CAPTURE_SUFFIX")
    }

    override suspend fun replaceCurrent(capture: File): PhotoDocument = locked {
        require(isCapture(capture)) { "Not a capture file" }
        val info = probe.probe(capture)
        if (info == null) {
            capture.delete()
            throw InvalidPhotoException()
        }
        sync(capture)
        val name = UUID.randomUUID().toString()
        val directory = File(root, name)
        val image = File(directory, IMAGE)
        if (!directory.mkdirs() || !capture.renameTo(image)) {
            directory.deleteRecursively()
            capture.delete()
            throw IOException("Cannot move the capture into place")
        }
        val previous = index.current()
        val row = PhotoEntity(
            directory = name,
            rawWidth = info.width,
            rawHeight = info.height,
            orientation = info.orientation.exif,
            imageBytes = image.length(),
            createdAt = clock(),
        )
        val id = try {
            index.insertCurrent(row)
        } catch (e: Exception) {
            directory.deleteRecursively()
            throw e
        }
        if (previous != null && !previous.saved) remove(previous)
        Log.i(TAG, "Photo $id is current (${info.width}x${info.height}, orientation ${info.orientation.exif})")
        documentOf(row.copy(id = id), PhotoText.None)
    }

    override suspend fun discard(capture: File) {
        locked { if (isCapture(capture)) capture.delete() }
    }

    override suspend fun clearCurrent() {
        locked {
            val current = index.current() ?: return@locked
            index.clearCurrent()
            if (!current.saved) remove(current)
        }
    }

    override suspend fun saveAttempt(documentId: Long, attempt: PhotoAttempt, replace: Boolean): Boolean = locked {
        val row = index.get(documentId)?.takeIf {
            it.recognitionRevision == attempt.basedOn && index.current()?.id == documentId
        } ?: return@locked false
        if (!replace && attemptOf(row)?.id != attempt.id) return@locked false
        val orientation = PhotoOrientation.fromExif(row.orientation)
        val width = if (orientation.swapsAxes) row.rawHeight else row.rawWidth
        val height = if (orientation.swapsAxes) row.rawWidth else row.rawHeight
        require(attempt.tiles.all { it.right <= width && it.bottom <= height })
        val directory = File(root, row.directory)
        val name = "attempt-${UUID.randomUUID()}"
        val file = File(directory, name)
        writeAtomically(file, AttemptCodec.encode(attempt))
        try {
            if (!index.setAttempt(documentId, attempt.basedOn, name)) {
                file.delete()
                return@locked false
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        row.attemptFile?.let { File(directory, it).delete() }
        true
    }

    override suspend fun commitRecognition(documentId: Long, basedOn: Int, recognition: PhotoRecognition): PhotoDocument? = locked {
        val row = index.get(documentId)?.takeIf { it.recognitionRevision == basedOn } ?: return@locked null
        val directory = File(root, row.directory)
        val revision = basedOn + 1
        val name = "$RECOGNITION$revision"
        val file = File(directory, name)
        writeAtomically(file, RecognitionCodec.encode(recognition))
        if (!index.setRecognition(documentId, basedOn, name, revision, recognition.language.code)) {
            file.delete()
            return@locked null
        }
        row.recognitionFile?.let { File(directory, it).delete() }
        row.attemptFile?.let { File(directory, it).delete() }
        documentOf(row.copy(recognitionFile = name, recognitionRevision = revision, attemptFile = null), PhotoText.Recognized(recognition))
    }

    override val entries: Flow<List<HistoryEntry>> = index.observeHistory()
        .distinctUntilChanged()
        .map { rows -> rows.map(::entryOf) }
        .distinctUntilChanged()
        .flowOn(io)

    override suspend fun save(documentId: Long, limits: HistoryLimits): Boolean = locked {
        val row = index.get(documentId) ?: return@locked false
        if (!row.saved) {
            val thumbnail = File(File(root, row.directory), THUMBNAIL)
            try {
                writeAtomically(thumbnail, thumbnails.thumbnail(documentOf(row, PhotoText.None)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The history shows a placeholder instead.
                Log.w(TAG, "No thumbnail for photo ${row.id}: " + e.javaClass.simpleName)
            }
            // A recognition committed before the language was stored names it only in its file.
            val language = row.language
                ?: row.recognitionFile?.let { (textOf(row) as? PhotoText.Recognized)?.recognition?.language?.code }
            if (!index.save(documentId, clock(), language)) return@locked false
            Log.i(TAG, "Photo ${row.id} saved to the history")
        }
        trimHistory(limits)
        true
    }

    override suspend fun open(documentId: Long): PhotoDocument? = locked {
        val row = index.get(documentId)?.takeIf { it.saved } ?: return@locked null
        if (!imageOf(row).isFile) {
            Log.w(TAG, "Photo ${row.id} lost its image")
            remove(row)
            return@locked null
        }
        val previous = index.current()
        if (previous?.id != documentId) {
            index.setCurrent(documentId)
            if (previous != null && !previous.saved) remove(previous)
        }
        load(row)
    }

    override suspend fun remove(ids: Set<Long>) {
        locked { ids.forEach { id -> index.get(id)?.takeIf { it.saved }?.let { leaveHistory(it) } } }
    }

    override suspend fun clear() {
        locked { index.history().forEach { leaveHistory(it) } }
    }

    override suspend fun excess(limits: HistoryLimits): Int = locked {
        val rows = index.history()
        rows.size - limits.kept(rows.map(::bytesOf))
    }

    override suspend fun trim(limits: HistoryLimits) {
        locked { trimHistory(limits) }
    }

    private suspend fun trimHistory(limits: HistoryLimits) {
        val rows = index.history()
        val beyond = rows.drop(limits.kept(rows.map(::bytesOf)))
        if (beyond.isEmpty()) return
        beyond.forEach { leaveHistory(it) }
        Log.i(TAG, "${beyond.size} oldest photos left the history")
    }

    /** Deletes the photo; the current one is closed first. */
    private suspend fun leaveHistory(row: PhotoEntity) {
        if (row.id == index.current()?.id) index.clearCurrent()
        remove(row)
    }

    private fun entryOf(row: PhotoEntity) = HistoryEntry(
        id = row.id,
        createdAt = row.createdAt,
        language = Language.of(row.language),
        thumbnail = File(File(root, row.directory), THUMBNAIL).takeIf { it.isFile },
        bytes = bytesOf(row),
    )

    private fun bytesOf(row: PhotoEntity): Long = File(root, row.directory).listFiles()?.sumOf { it.length() } ?: 0L

    /** Runs [block] alone, after the leftovers of an interrupted change were deleted once. */
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(io) {
        mutex.withLock {
            if (!tidied) {
                tidy()
                tidied = true
            }
            block()
        }
    }

    /**
     * Deletes captures never committed, directories no photo names, files a photo no longer names, and photos that are
     * neither current nor kept by the history; a photo whose image is gone is dropped as well.
     */
    private suspend fun tidy() {
        staging.deleteRecursively()
        val current = index.current()
        val kept = hashSetOf<String>()
        for (row in index.all()) {
            when {
                !imageOf(row).isFile -> {
                    Log.w(TAG, "Photo ${row.id} lost its image")
                    if (row.id == current?.id) index.clearCurrent()
                    remove(row)
                }
                row.id != current?.id && !row.saved -> remove(row)
                else -> {
                    kept += row.directory
                    File(root, row.directory).listFiles()
                        ?.filter {
                            it.name != IMAGE && it.name != THUMBNAIL && it.name != row.recognitionFile && it.name != row.attemptFile
                        }
                        ?.forEach { it.deleteRecursively() }
                }
            }
        }
        root.listFiles()?.filter { it.name !in kept }?.forEach { it.deleteRecursively() }
    }

    /** The row first: files without a row are leftovers that [tidy] deletes, a row without files would be a lost photo. */
    private suspend fun remove(row: PhotoEntity) {
        index.delete(row.id)
        File(root, row.directory).deleteRecursively()
    }

    private fun load(row: PhotoEntity): PhotoDocument? {
        if (!imageOf(row).isFile) {
            Log.w(TAG, "Photo ${row.id} has no image")
            return null
        }
        return documentOf(row, textOf(row))
    }

    private fun textOf(row: PhotoEntity): PhotoText {
        val name = row.recognitionFile ?: return PhotoText.None
        val bytes = try {
            File(File(root, row.directory), name).readBytes()
        } catch (e: IOException) {
            Log.w(TAG, "Cannot read the text of photo ${row.id}: " + e.javaClass.simpleName)
            null
        }
        val recognition = bytes?.let(RecognitionCodec::decode)
        if (recognition == null) {
            Log.w(TAG, "The stored text of photo ${row.id} is unreadable")
            return PhotoText.Unreadable
        }
        return PhotoText.Recognized(recognition)
    }

    private fun attemptOf(row: PhotoEntity): PhotoAttempt? {
        val name = row.attemptFile ?: return null
        return try {
            AttemptCodec.decode(File(File(root, row.directory), name).readBytes())?.takeIf { it.basedOn == row.recognitionRevision }
        } catch (e: IOException) {
            Log.w(TAG, "Cannot read attempt of photo ${row.id}: " + e.javaClass.simpleName)
            null
        }
    }

    private fun documentOf(row: PhotoEntity, text: PhotoText): PhotoDocument {
        val attempt = attemptOf(row)
        return PhotoDocument(
            id = row.id,
            image = imageOf(row),
            rawWidth = row.rawWidth,
            rawHeight = row.rawHeight,
            orientation = PhotoOrientation.fromExif(row.orientation),
            createdAt = row.createdAt,
            revision = row.recognitionRevision,
            text = text,
            attempt = attempt,
            attemptUnreadable = row.attemptFile != null && attempt == null,
            saved = row.saved,
        )
    }

    private fun imageOf(row: PhotoEntity) = File(File(root, row.directory), IMAGE)

    private fun isCapture(file: File) = file.absoluteFile.parentFile == staging.absoluteFile

    private companion object {
        const val TAG = "PhotoStore"
        const val ROOT = "photos"
        const val STAGING = ".staging"
        const val CAPTURE_SUFFIX = ".capture"
        const val IMAGE = "image"
        const val THUMBNAIL = "thumbnail"
        const val RECOGNITION = "recognition-"
        const val TEMP_SUFFIX = ".tmp"

        /** Written and flushed to the disk under a temporary name, then renamed: readers see the old file or the new. */
        fun writeAtomically(file: File, bytes: ByteArray) {
            val temp = File(file.parentFile, file.name + TEMP_SUFFIX)
            FileOutputStream(temp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (!temp.renameTo(file)) {
                temp.delete()
                throw IOException("Cannot store " + file.name)
            }
        }

        fun sync(file: File) = RandomAccessFile(file, "rw").use { it.fd.sync() }
    }
}

/** The size and orientation of an image file; null when it is not an image. */
internal fun interface ImageProbe {
    fun probe(file: File): ImageInfo?
}

internal data class ImageInfo(val width: Int, val height: Int, val orientation: PhotoOrientation)

internal object BitmapImageProbe : ImageProbe {
    override fun probe(file: File): ImageInfo? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val exif = try {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return ImageInfo(options.outWidth, options.outHeight, PhotoOrientation.fromExif(exif))
    }
}
