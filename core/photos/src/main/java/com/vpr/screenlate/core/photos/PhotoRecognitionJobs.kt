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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

sealed interface PhotoWork {
    val documentId: Long
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
            documents.current.catch { Log.w("PhotoOCR", "Document observation failed: " + it.javaClass.simpleName) }.collect { photo ->
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

    @Synchronized
    fun recognize(document: PhotoDocument, language: Language, engines: OcrEngines) = start(document, language, engines, null)

    @Synchronized
    fun recognize(document: PhotoDocument, language: Language, settings: PhotoOcrSettings) =
        start(document, language, settings.engines, null, settings)

    /** Keeps successful tiles and the original attempt's settings/language; only failures and unattempted tiles run. */
    @Synchronized
    fun retry(document: PhotoDocument) {
        val attempt = document.attempt ?: (document.text as? PhotoText.Recognized)?.recognition?.let { recognition ->
            if (recognition.tiles.isEmpty() || recognition.failed.isEmpty()) return
            PhotoAttempt("retry", document.revision, recognition.language, recognition.settings,
                (recognition.tiles.map { it.tile } + recognition.failed.map { it.box.toTile() }).distinct(),
                recognition.tiles.map { PhotoTileResult(it.tile, page = it.page) }, recognition.recognizedAt)
        } ?: return
        if (attempt.remaining.isNotEmpty()) start(document, attempt.language, attempt.settings.engines, attempt)
    }

    private fun start(document: PhotoDocument, language: Language, engines: OcrEngines, retry: PhotoAttempt?, requested: PhotoOcrSettings? = null) {
        val previous = job
        previous?.cancel()
        target = document.id
        job = scope.launch {
            previous?.join()
            val id = newId()
            mutableWork.value = PhotoWork.Running(document.id, id, 0, 0)
            try {
                engine.open(document, language, engines, retry?.settings ?: requested).use { session ->
                    var attempt = PhotoAttempt(id, document.revision, language, session.settings,
                        retry?.tiles ?: PhotoTiles.grid(document.width, document.height),
                        retry?.results?.filter { it.page != null } ?: emptyList(), clock())
                    if (!documents.saveAttempt(document.id, attempt, replace = true)) return@launch
                    mutableWork.value = PhotoWork.Running(document.id, id, attempt.results.size, attempt.tiles.size)
                    val progress = Mutex()
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
                                progress.withLock {
                                    attempt = attempt.copy(results = attempt.results + result)
                                    if (!documents.saveAttempt(document.id, attempt)) throw CancellationException("Stale photo attempt")
                                    mutableWork.value = PhotoWork.Running(document.id, id, attempt.results.size, attempt.tiles.size)
                                }
                            }
                        } }.awaitAll()
                    }
                    attempt = attempt.copy(finished = true)
                    if (!documents.saveAttempt(document.id, attempt)) return@launch
                    val successful = attempt.results.mapNotNull { result -> result.page?.let { PhotoTilePage(result.tile, it) } }
                    if (successful.isNotEmpty()) {
                        val page = PhotoAssembly.assemble(document.width, document.height, language, successful)
                        documents.commitRecognition(document.id, document.revision, PhotoRecognition(language, session.settings, page,
                            attempt.results.mapNotNull { result -> result.failure?.let { FailedRegion(result.tile.box, it) } }, clock(), successful))
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
}
