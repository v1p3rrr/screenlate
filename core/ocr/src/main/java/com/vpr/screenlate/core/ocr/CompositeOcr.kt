package com.vpr.screenlate.core.ocr

import android.graphics.Bitmap
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.lens.LensOcrEngine
import com.vpr.screenlate.core.ocr.mlkit.MlKitOcrEngine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

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

/**
 * Runs ML Kit and Lens in parallel: the ML Kit page is emitted as a [OcrUpdate.Draft] if it arrives first, the Lens
 * page as the [OcrUpdate.Final]. Without network or after a Lens failure the ML Kit page becomes final.
 */
@Singleton
class CompositeOcr @Inject constructor(
    private val lens: LensOcrEngine,
    private val mlKit: MlKitOcrEngine,
    private val networkStatus: NetworkStatus,
) {
    fun recognize(image: Bitmap, language: Language): Flow<OcrUpdate> = channelFlow {
        val emitter = UpdateEmitter(this)
        val draft = async { runCatching { mlKit.recognize(image, language) } }

        if (!networkStatus.isOnline()) {
            emitter.emitFinal(OcrUpdate.Final(draft.await().getOrThrow(), OfflineException()))
            return@channelFlow
        }

        launch { draft.await().onSuccess { emitter.emitDraft(OcrUpdate.Draft(it)) } }

        val lensResult = runCatching { withTimeout(LENS_TIMEOUT) { lens.recognize(image, language) } }
        lensResult
            .onSuccess { emitter.emitFinal(OcrUpdate.Final(it)) }
            .onFailure { lensError ->
                val fallback = draft.await().getOrElse { throw lensError }
                emitter.emitFinal(OcrUpdate.Final(fallback, lensError))
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
        val LENS_TIMEOUT = 15.seconds
    }
}
