package com.vpr.screenlate.core.ocr

import android.graphics.Bitmap
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.lens.LensOcrEngine
import com.vpr.screenlate.core.ocr.mlkit.MlKitOcrEngine
import java.io.IOException
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
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

    /**
     * The result to keep. [lensError] is set when Lens failed or was skipped and [page] comes from ML Kit.
     */
    data class Final(override val page: OcrPage, val lensError: Throwable? = null) : OcrUpdate
}

class OfflineException : IOException("No network connection")

/** Lens refused recent requests (e.g. HTTP 429); it is not asked again for a while. */
class LensPausedException : IOException("Lens refused recent requests")

/**
 * Runs ML Kit and Lens in parallel: ML Kit pages are emitted as [OcrUpdate.Draft]s while Lens is pending, the Lens
 * page as the [OcrUpdate.Final]. Without network or after a Lens failure the ML Kit page of the whole image becomes
 * final. [OcrOptions] can leave out either engine.
 *
 * ML Kit first reads a band around the aim ([FocusBand]), and another one when the aim has moved out of it meanwhile,
 * so the word under the aim has a draft long before the whole screen is read.
 */
@Singleton
class CompositeOcr internal constructor(
    private val lens: OcrEngine,
    private val mlKit: OcrEngine,
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
) {
    @Inject
    constructor(lens: LensOcrEngine, mlKit: MlKitOcrEngine, networkStatus: NetworkStatus) :
        this(lens, mlKit, networkStatus::isOnline)

    /** @param focus the aim's row in [image] at the moment, or null without an aim. */
    fun recognize(
        image: Bitmap,
        language: Language,
        options: OcrOptions = OcrOptions(),
        focus: () -> Float? = { null },
    ): Flow<OcrUpdate> = channelFlow {
        if (options.engines == OcrEngines.CLOUD) {
            send(OcrUpdate.Final(recognizeWithLens(image, language)))
            return@channelFlow
        }
        val lensSkipped = when {
            options.engines == OcrEngines.DEVICE -> null
            !isOnline() -> OfflineException()
            lensPaused() -> LensPausedException()
            else -> null
        }
        val useLens = options.engines == OcrEngines.BOTH && lensSkipped == null
        val wholeImage = CompletableDeferred<Unit>()
        if (!useLens || !options.deferWholeImage) wholeImage.complete(Unit)

        val emitter = UpdateEmitter(this)
        // ML Kit cannot be stopped once it runs, so it reads its own copy outside this flow: the flow ends with the
        // Lens result while ML Kit finishes, and the copy is freed once ML Kit no longer reads it.
        val copy = withContext(worker) { copyOf(image) }
        val draft = CoroutineScope(coroutineContext.minusKey(Job)).async {
            try {
                runCatching { recognizeOnDevice(copy, language, focus, emitter, wholeImage) }
                    .onFailure { if (it !is CancellationException) Log.w(TAG, "ML Kit failed", it) }
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
            runCatching { withTimeout(LENS_TIMEOUT) { lens.recognize(image, language) } }
                .onFailure(::noteLensFailure)
                .onSuccess { emitter.emitFinal(OcrUpdate.Final(it)) }
                .onFailure { lensError ->
                    wholeImage.complete(Unit)
                    val fallback = draft.await().getOrElse { throw lensError }
                    emitter.emitFinal(OcrUpdate.Final(fallback, lensError))
                }
        } finally {
            draft.cancel()
            coroutineContext.job.cancelChildren()
        }
    }

    private suspend fun recognizeWithLens(image: Bitmap, language: Language): OcrPage {
        if (!isOnline()) throw OfflineException()
        if (lensPaused()) throw LensPausedException()
        return try {
            withTimeout(LENS_TIMEOUT) { lens.recognize(image, language) }
        } catch (e: TimeoutCancellationException) {
            // Not a cancellation of the caller.
            throw IOException("Lens timed out", e)
        } catch (e: Exception) {
            noteLensFailure(e)
            throw e
        }
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
            val band = try {
                mlKit.recognize(crop, language)
            } finally {
                if (crop !== image) release(crop)
            }
            Log.d(TAG, "ML Kit band: ${band.paragraphs.size} paragraphs in ${clock() - started} ms")
            val placed = band.offset(0f, rows.first.toFloat()).copy(height = height)
            draft = draft?.let(placed::withMissingFrom) ?: placed
            done += rows
            emitter.emitDraft(OcrUpdate.Draft(draft))
        }
        val started = clock()
        return mlKit.recognize(image, language).also {
            Log.d(TAG, "ML Kit: ${it.paragraphs.size} paragraphs in ${clock() - started} ms")
        }
    }

    /** Unloads the on-device model when it is turned off; a later scan loads it again. */
    fun releaseOnDevice() {
        mlKit.release()
        Log.i(TAG, "Released the on-device model")
    }

    /** Loads the on-device model, which would otherwise delay the first draft. */
    suspend fun warmUp() {
        val image = Bitmap.createBitmap(WARM_UP_SIZE, WARM_UP_SIZE, Bitmap.Config.ARGB_8888)
        try {
            mlKit.recognize(image, Language.JAPANESE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit warm-up failed", e)
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
        return runCatching { withTimeout(LENS_TIMEOUT) { lens.recognize(image, language) } }
            .onFailure(::noteLensFailure)
            .getOrNull()
    }

    @Volatile
    private var lensPausedUntil = 0L

    private fun lensPaused(): Boolean = clock() < lensPausedUntil

    private fun noteLensFailure(error: Throwable) {
        if (error is CancellationException) return
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
