package com.vpr.screenlate.core.photos

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.ocr.NoDeviceOcrException
import com.vpr.screenlate.core.ocr.OfflineException
import com.vpr.screenlate.core.ocr.LensPausedException
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.PhotoAssembly
import com.vpr.screenlate.core.ocr.PhotoTile
import com.vpr.screenlate.core.ocr.PhotoTilePage
import com.vpr.screenlate.core.ocr.PhotoTiles
import com.vpr.screenlate.core.ocr.lens.LensHttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

sealed interface PhotoWork {
    val documentId: Long

    /** [completed] and [total] count the photo's regions, not the overview pass ([PhotoTiles.overview]). */
    data class Running(override val documentId: Long, val attemptId: String, val completed: Int, val total: Int) : PhotoWork
    data class Failed(override val documentId: Long, val cause: FailureCause) : PhotoWork
}

/** Process-owned explicit work: survives page disposal, cancels on replacement, never restarts from stored progress. */
@Singleton
class PhotoRecognitionJobs internal constructor(
    private val documents: PhotoDocuments,
    private val engine: PhotoRecognitionEngine,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val newId: () -> String,
) {
    @Inject constructor(documents: PhotoDocuments, engine: PhotoRecognitionEngine) : this(
        documents, engine, CoroutineScope(SupervisorJob() + Dispatchers.Default), System::currentTimeMillis, { UUID.randomUUID().toString() },
    )

    private val mutableWork = MutableStateFlow<PhotoWork?>(null)
    val work = mutableWork.asStateFlow()
    private var job: Job? = null
    @Volatile private var target: Long? = null
    private val slots = Semaphore(2)

    init {
        scope.launch {
            documents.current.retryWhen { error, attempt ->
                // Without this observation a replaced photo's attempt would run on until its next store write.
                if (attempt == 0L) Log.w("PhotoOCR", "Document observation failed: " + error.javaClass.simpleName)
                delay(OBSERVATION_RETRY_MS)
                true
            }.collect { photo ->
                synchronized(this@PhotoRecognitionJobs) {
                    if (target != null && target != photo?.id) {
                        job?.cancel()
                        target = null
                        mutableWork.value = null
                    }
                }
            }
        }
    }

    /** Recognizes the whole photo with [settings], replacing the text it has by the time the attempt starts. */
    @Synchronized
    fun recognize(document: PhotoDocument, language: Language, settings: PhotoOcrSettings) = start(document, language, settings, null)

    /** Keeps successful tiles and the original attempt's settings/language; only failures and unattempted tiles run. */
    @Synchronized
    fun retry(document: PhotoDocument) {
        val attempt = document.attempt ?: (document.text as? PhotoText.Recognized)?.recognition?.let { recognition ->
            if (recognition.tiles.isEmpty() || recognition.failed.isEmpty()) return
            PhotoAttempt("retry", document.revision, recognition.language, recognition.settings,
                (recognition.tiles.map { it.tile } + recognition.failed.map { it.box.toTile() }).distinct(),
                recognition.tiles.map { PhotoTileResult(it.tile, page = it.page) }, recognition.recognizedAt)
        } ?: return
        if (attempt.remaining.isNotEmpty()) start(document, attempt.language, attempt.settings, attempt)
    }

    private fun start(document: PhotoDocument, language: Language, settings: PhotoOcrSettings, retry: PhotoAttempt?) {
        val previous = job
        previous?.cancel()
        target = document.id
        job = scope.launch {
            previous?.join()
            val id = newId()
            mutableWork.value = PhotoWork.Running(document.id, id, 0, 0)
            try {
                // A recognition that finished meanwhile, such as the one just replaced, may have committed newer text.
                val base = if (retry != null) document
                    else documents.current.first()?.takeIf { it.id == document.id } ?: return@launch
                val basedOn = base.revision
                engine.open(document, language, settings).use { session ->
                    // The overview goes first, so the regions' progress does not wait for it at the end.
                    var attempt = PhotoAttempt(id, basedOn, language, session.settings,
                        retry?.tiles ?: (listOfNotNull(PhotoTiles.overview(document.width, document.height)) +
                            PhotoTiles.grid(document.width, document.height)),
                        retry?.results?.filter { it.page != null } ?: emptyList(), clock())
                    if (!documents.saveAttempt(document.id, attempt, replace = true)) return@launch
                    fun progress(attempt: PhotoAttempt) = PhotoWork.Running(document.id, id,
                        attempt.results.count { !document.isOverview(it.tile) }, attempt.tiles.count { !document.isOverview(it) })
                    mutableWork.value = progress(attempt)
                    val saving = Mutex()
                    coroutineScope {
                        attempt.remaining.map { tile -> async {
                            slots.withPermit {
                                val result = try {
                                    PhotoTileResult(tile, page = session.recognize(tile))
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) {
                                    Log.w("PhotoOCR", "Tile failed: " + e.javaClass.simpleName)
                                    PhotoTileResult(tile, failure = cause(e, session.settings.engines))
                                }
                                saving.withLock {
                                    attempt = attempt.copy(results = attempt.results + result)
                                    if (!documents.saveAttempt(document.id, attempt)) throw CancellationException("Stale photo attempt")
                                    mutableWork.value = progress(attempt)
                                }
                            }
                        } }.awaitAll()
                    }
                    attempt = attempt.copy(finished = true)
                    if (!documents.saveAttempt(document.id, attempt)) return@launch
                    val successful = attempt.results.mapNotNull { result -> result.page?.let { PhotoTilePage(result.tile, it) } }
                    val (overview, regions) = successful.partition { document.isOverview(it.tile) }
                    // The overview alone holds only large text: it replaces no text the photo has, and without large
                    // text it does not make the photo one without text while its regions failed.
                    val page = if (regions.isEmpty() && (overview.isEmpty() || base.text is PhotoText.Recognized)) null
                        else PhotoAssembly.assemble(document.width, document.height, language, regions, overview.singleOrNull())
                    if (page != null && (regions.isNotEmpty() || page.paragraphs.isNotEmpty())) {
                        // A failed overview only leaves out large text across tile seams: not a missing region.
                        val failed = attempt.results.filterNot { document.isOverview(it.tile) }
                            .mapNotNull { result -> result.failure?.let { FailedRegion(result.tile.box, it) } }
                        documents.commitRecognition(document.id, basedOn, PhotoRecognition(language, session.settings, page,
                            failed, clock(), successful))
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.w("PhotoOCR", "Attempt failed: " + e.javaClass.simpleName)
                if (target == document.id) mutableWork.value = PhotoWork.Failed(document.id,
                    if (e is NoDeviceOcrException) FailureCause.DEVICE else FailureCause.OTHER)
            } finally {
                if ((mutableWork.value as? PhotoWork.Running)?.attemptId == id) mutableWork.value = null
            }
        }
    }

    private fun com.vpr.screenlate.core.common.geometry.Box.toTile() =
        PhotoTile(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

    private fun cause(error: Exception, engines: OcrEngines): FailureCause = when {
        error is PhotoReadException -> FailureCause.OTHER
        engines == OcrEngines.DEVICE -> FailureCause.DEVICE
        error is NoDeviceOcrException -> FailureCause.DEVICE
        error is OfflineException -> FailureCause.OFFLINE
        error is LensPausedException -> FailureCause.REFUSED
        error is LensHttpException -> if (error.refused) FailureCause.REFUSED else FailureCause.SERVER
        error is IOException -> FailureCause.NETWORK
        else -> FailureCause.OTHER
    }

    private companion object {
        const val OBSERVATION_RETRY_MS = 1_000L
    }
}
