package com.vpr.screenlate.core.ocr

import android.graphics.Bitmap
import android.os.SystemClock
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.redacted
import com.vpr.screenlate.core.ocr.lens.LensOcrEngine
import com.vpr.screenlate.core.ocr.model.DeviceOcr
import com.vpr.screenlate.diagnostics.Diag
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import com.vpr.screenlate.core.ocr.lens.LensHttpException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface OcrUpdate {
    val page: OcrPage

    /** On-device result shown while Lens is still pending. A [Final] update always follows. */
    data class Draft(override val page: OcrPage) : OcrUpdate

    /** The result to keep. [lensError] is set when Lens failed or was skipped and [page] comes from the device. */
    data class Final(override val page: OcrPage, val lensError: Throwable? = null) : OcrUpdate
}

class OfflineException : IOException("No network connection")

/** Lens refused recent requests (e.g. HTTP 429); it is not asked again for a while. */
class LensPausedException : IOException("Lens refused recent requests")

/**
 * Runs the device ([DeviceOcr]: ML Kit, or an engine with downloaded models) and Lens in parallel: device pages are
 * emitted as [OcrUpdate.Draft]s while Lens is pending, the Lens page as the [OcrUpdate.Final]. Without network or after
 * a Lens failure the device page of the whole image becomes final. [OcrOptions] can leave out either engine; a
 * language the device cannot read goes to Lens alone.
 *
 * The device first reads a band around the aim ([FocusBand]), and another one when the aim has moved out of it meanwhile,
 * so the word under the aim has a draft long before the whole screen is read.
 */
@Singleton
class CompositeOcr internal constructor(
    private val lens: OcrEngine,
    private val device: OcrEngine,
    private val isOnline: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val heightOf: (Bitmap) -> Int = Bitmap::getHeight,
    private val cropRows: (Bitmap, IntRange) -> Bitmap = { image, rows ->
        Bitmap.createBitmap(image, 0, rows.first, image.width, rows.last - rows.first + 1)
    },
    private val copyOf: (Bitmap) -> Bitmap = { it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) },
    private val release: (Bitmap) -> Unit = Bitmap::recycle,
    /** Where images are copied and cropped, off the collector's (main) thread. */
    private val worker: CoroutineContext = Dispatchers.Default,
    /** What reads a language on the device, now and after each change ([DeviceOcr.activeUpdates]). */
    private val deviceModels: (Language) -> Flow<Any?> = { emptyFlow() },
) {
    @Inject
    constructor(lens: LensOcrEngine, device: DeviceOcr, networkStatus: NetworkStatus) :
        this(lens, device, networkStatus::isOnline, deviceModels = device::activeUpdates)

    /**
     * @param focus the aim's row in [image] at the moment, or null without an aim.
     * @return pages with their words as [language] writes them ([inLanguage]).
     */
    fun recognize(
        image: Bitmap,
        language: Language,
        options: OcrOptions = OcrOptions(),
        focus: () -> Float? = { null },
    ): Flow<OcrUpdate> = channelFlow {
        // A language the device cannot read goes to the cloud alone, unless the device alone was asked for.
        val deviceReads = deviceReads(language)
        if (!deviceReads && options.engines == OcrEngines.DEVICE) throw NoDeviceOcrException()
        if (options.engines == OcrEngines.CLOUD || !deviceReads) {
            if (!deviceReads) Log.d(TAG, "No on-device recognition for ${language.code}; cloud only")
            Diag.scanNote("ocr_plan", "cloud") // TEMPORARY DIAGNOSTICS BUILD
            send(OcrUpdate.Final(diagLens { recognizeWithLens(image, language) }))
            return@channelFlow
        }
        val lensSkipped = when {
            options.engines == OcrEngines.DEVICE -> null
            !isOnline() -> OfflineException()
            lensPaused() -> LensPausedException()
            else -> null
        }
        val useLens = options.engines == OcrEngines.BOTH && lensSkipped == null
        // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
        Diag.scanNote("ocr_plan", if (useLens) "both" else "device")
        lensSkipped?.let { Diag.scanNote("lens_skipped", it.javaClass.simpleName) }
        val wholeImage = CompletableDeferred<Unit>()
        if (!useLens || !options.deferWholeImage) wholeImage.complete(Unit)

        val emitter = UpdateEmitter(this)
        // The device cannot be stopped once it runs, so it reads its own copy outside this flow: the flow ends with
        // the Lens result while the device finishes, and the copy is freed once the device no longer reads it. Started
        // at once, so a scan cancelled before the draft would have run still frees the copy.
        val diagCopy = SystemClock.elapsedRealtime() // TEMPORARY DIAGNOSTICS BUILD
        val copy = copyOnWorker(image)
        Diag.scanNote("copy_ms", SystemClock.elapsedRealtime() - diagCopy) // TEMPORARY DIAGNOSTICS BUILD
        val draft = CoroutineScope(coroutineContext.minusKey(Job)).async(start = CoroutineStart.UNDISPATCHED) {
            try {
                runCatching { recognizeOnDevice(copy, language, focus, emitter, wholeImage) }
                    .onFailure { if (it !is CancellationException) Log.w(TAG, "On-device OCR failed", it.redacted()) }
            } finally {
                release(copy)
            }
        }
        try {
            if (!useLens) {
                emitter.emitFinal(OcrUpdate.Final(draft.await().getOrThrow(), lensSkipped))
                return@channelFlow
            }
            launch { draft.await().onSuccess { emitter.emitDraft(OcrUpdate.Draft(it)) } }
            if (options.deferWholeImage) {
                launch {
                    delay(WHOLE_IMAGE_DELAY)
                    wholeImage.complete(Unit)
                }
            }
            runCatching { diagLens { askLens { lens.recognize(image, language) } } }
                .onFailure { if (it is CancellationException) throw it }
                .onFailure(::noteLensFailure)
                .onSuccess { page -> emitter.emitFinal(OcrUpdate.Final(page)) }
                .onFailure { lensError ->
                    wholeImage.complete(Unit)
                    val fallback = draft.await().getOrElse { throw lensError }
                    emitter.emitFinal(OcrUpdate.Final(fallback, lensError))
                }
        } finally {
            draft.cancel()
            coroutineContext.job.cancelChildren()
        }
    }.map { update ->
        when (update) {
            is OcrUpdate.Draft -> update.copy(page = update.page.inLanguage(language))
            is OcrUpdate.Final -> update.copy(page = update.page.inLanguage(language))
        }
    }

    // TEMPORARY DIAGNOSTICS BUILD: the cloud request's start and end in the scan summary (ai/notes/diagnostics.md).
    private suspend fun <T> diagLens(block: suspend () -> T): T {
        val started = SystemClock.elapsedRealtime()
        Diag.scanMark("lens_start")
        return try {
            block().also { Diag.scanMark("lens_done") }
        } catch (e: Throwable) {
            Diag.scanMark(if (e is CancellationException) "lens_cancelled" else "lens_failed")
            throw e
        } finally {
            Diag.scanNote("lens_ms", SystemClock.elapsedRealtime() - started)
        }
    }

    /** [image] copied on [worker]. A scan cancelled meanwhile drops what withContext returns, so the copy is freed here. */
    private suspend fun copyOnWorker(image: Bitmap): Bitmap {
        var copy: Bitmap? = null
        try {
            return withContext(worker) { copyOf(image).also { copy = it } }
        } catch (e: CancellationException) {
            copy?.let(release)
            throw e
        }
    }

    private suspend fun recognizeWithLens(image: Bitmap, language: Language): OcrPage {
        if (!isOnline()) throw OfflineException()
        if (lensPaused()) throw LensPausedException()
        return try {
            askLens { lens.recognize(image, language) }
        } catch (e: Exception) {
            noteLensFailure(e)
            throw e
        }
    }

    /**
     * Runs a Lens request with the timeout. The timeout becomes a [SocketTimeoutException]: as a
     * [TimeoutCancellationException] it would pass for a cancellation of the caller and end the scan without an error.
     */
    private suspend fun <T> askLens(block: suspend () -> T): T = try {
        withTimeout(LENS_TIMEOUT) { block() }
    } catch (e: TimeoutCancellationException) {
        // A timeout of the caller's own stays a cancellation.
        currentCoroutineContext().ensureActive()
        throw SocketTimeoutException("Lens timed out").apply { initCause(e) }
    }

    /**
     * Focus bands around the aim as drafts, then the whole image, which is returned. The whole image waits for
     * [wholeImage]; meanwhile an aim that moves away still gets its band.
     */
    private suspend fun recognizeOnDevice(
        image: Bitmap,
        language: Language,
        focus: () -> Float?,
        emitter: UpdateEmitter,
        wholeImage: Deferred<Unit>,
    ): OcrPage {
        val height = heightOf(image)
        val done = mutableListOf<IntRange>()
        var draft: OcrPage? = null
        while (true) {
            val rows = if (done.size < MAX_FOCUS_BANDS) FocusBand.next(height, focus(), done) else null
            if (rows == null) {
                if (wholeImage.isCompleted) break
                if (done.size < MAX_FOCUS_BANDS) withTimeoutOrNull(FOCUS_POLL) { wholeImage.await() } else wholeImage.await()
                continue
            }
            val started = clock()
            val crop = withContext(worker) { cropRows(image, rows) }
            val diagCropped = clock() // TEMPORARY DIAGNOSTICS BUILD
            val band = try {
                device.recognize(crop, language)
            } finally {
                if (crop !== image) release(crop)
            }
            Log.d(TAG, "${band.source} band: ${band.paragraphs.size} paragraphs in ${clock() - started} ms")
            // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
            Diag.scanStat("band_crop", diagCropped - started)
            Diag.scanStat("device_band", clock() - diagCropped)
            Diag.scanMark("band_done")
            val placed = band.offset(0f, rows.first.toFloat()).copy(height = height)
            draft = draft?.let(placed::withMissingFrom) ?: placed
            done += rows
            emitter.emitDraft(OcrUpdate.Draft(draft))
        }
        val started = clock()
        Diag.scanMark("device_whole_start") // TEMPORARY DIAGNOSTICS BUILD
        return device.recognize(image, language).also {
            Log.d(TAG, "${it.source}: ${it.paragraphs.size} paragraphs in ${clock() - started} ms")
            // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
            Diag.scanMark("device_whole_done")
            Diag.scanNote("device_whole_ms", clock() - started)
            Diag.scanNote("device_bands", done.size)
        }
    }

    /** The on-device engine that read the page, for the log. */
    private val OcrPage.source: String get() = if (engine == OcrEngineType.ML_KIT) "ML Kit" else "On-device model"

    /** Whether the device reads [language]; a scan of a language it does not read asks the cloud alone. */
    fun deviceReads(language: Language): Boolean = device.reads(language)

    /** A scan may follow (the bubble is touched): Lens opens its connection meanwhile, unless it cannot be asked. */
    fun prepareLens() {
        if (isOnline() && !lensPaused()) lens.prepare() else Diag.count("lens_prepare_skipped") // TEMPORARY DIAGNOSTICS BUILD
    }

    /** Unloads the on-device model when it is turned off; a later scan loads it again. */
    fun releaseOnDevice() {
        device.release()
        Log.i(TAG, "Released the on-device model")
        Diag.event("device_release", *Diag.memoryNow()) // TEMPORARY DIAGNOSTICS BUILD
    }

    /** Emits each time another on-device model takes over reading [language]: chosen, installed or deleted. */
    fun deviceModelChanges(language: Language): Flow<Unit> = deviceModels(language).drop(1).map { }

    /** Loads the on-device model for [language], which would otherwise delay the first draft. */
    suspend fun warmUp(language: Language) {
        if (!device.reads(language)) return
        val image = Bitmap.createBitmap(WARM_UP_SIZE, WARM_UP_SIZE, Bitmap.Config.ARGB_8888)
        try {
            val started = SystemClock.elapsedRealtime()
            device.recognize(image, language)
            Log.i(TAG, "On-device recognition for ${language.code} warmed up in ${SystemClock.elapsedRealtime() - started} ms")
            // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
            Diag.event(
                "warm_up",
                "language" to language.code,
                "ms" to SystemClock.elapsedRealtime() - started,
                "scan" to Diag.currentScan().takeIf { it > 0 },
                *Diag.memoryNow(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "On-device OCR warm-up failed", e.redacted())
            Diag.event("warm_up", "language" to language.code, "error" to e.javaClass.simpleName) // TEMPORARY DIAGNOSTICS BUILD
        } finally {
            image.recycle()
        }
    }

    /**
     * Lens only, for a crop of the screen (small text). Null without network, while Lens is paused, or on failure:
     * the crop only adds to a result the caller already has.
     */
    suspend fun recognizeRegion(image: Bitmap, language: Language): OcrPage? {
        if (!isOnline() || lensPaused()) return null
        val diagStarted = SystemClock.elapsedRealtime() // TEMPORARY DIAGNOSTICS BUILD
        return runCatching { askLens { lens.recognize(image, language) } }
            .also { Diag.scanStat("lens_region", SystemClock.elapsedRealtime() - diagStarted) } // TEMPORARY DIAGNOSTICS BUILD
            .onFailure { if (it is CancellationException) throw it }
            .onFailure(::noteLensFailure)
            .getOrNull()
            ?.inLanguage(language)
    }

    @Volatile
    private var lensPausedUntil = 0L

    private fun lensPaused(): Boolean = clock() < lensPausedUntil

    private fun noteLensFailure(error: Throwable) {
        if (error is LensHttpException && error.refused) {
            lensPausedUntil = clock() + LENS_PAUSE.inWholeMilliseconds
            Log.w(TAG, "Lens refused a request (HTTP ${error.code}); using on-device OCR for $LENS_PAUSE")
        }
    }

    /** Guarantees that no draft is emitted after the final update. */
    private class UpdateEmitter(private val scope: ProducerScope<OcrUpdate>) {
        private val mutex = Mutex()
        private var finished = false

        suspend fun emitDraft(update: OcrUpdate.Draft) = mutex.withLock {
            if (!finished) scope.send(update)
        }

        suspend fun emitFinal(update: OcrUpdate.Final) = mutex.withLock {
            finished = true
            scope.send(update)
        }
    }

    private companion object {
        const val TAG = "CompositeOcr"
        val LENS_TIMEOUT = 15.seconds
        val WHOLE_IMAGE_DELAY = 3.seconds
        val FOCUS_POLL = 150.milliseconds
        const val MAX_FOCUS_BANDS = 3
        const val WARM_UP_SIZE = 32
        val LENS_PAUSE = 5.minutes
    }
}
