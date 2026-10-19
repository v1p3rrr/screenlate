package com.vpr.screenlate.core.ocr.paddle

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.mnn.MnnNetwork
import com.vpr.screenlate.core.ocr.mnn.MnnTensor
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.core.ocr.model.ModelOcrEngine
import com.vpr.screenlate.diagnostics.Diag
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.IOException
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * PP-OCR on MNN. A recognizer's directory holds `model.mnn` and `dict.txt`, its characters one per line; the
 * detector it requires holds `model.mnn` and may hold `detector.properties` with its thresholds
 * ([DetectionThresholds.parse]). The detector and one recognizer stay loaded until [release] or a scan with
 * another model; the memory their largest input took is freed once scans pause ([TRIM_DELAY_MS]). A recognizer whose
 * characters include right-to-left letters (Arabic) reads its crops from left to right; its lines are turned into
 * reading order ([readingOrder]).
 */
@Singleton
class PaddleOcrEngine @Inject constructor() : ModelOcrEngine {
    override val id = ID

    private val mutex = Mutex()
    private var detector: Loaded<Detector>? = null
    private var recognizer: Loaded<Recognizer>? = null

    /** A whole screen leaves ~350 MB with MNN (1280 × 2856); freed once scans pause, kept while they follow. */
    private val trim = IdleAction(CoroutineScope(SupervisorJob() + Dispatchers.Default), TRIM_DELAY_MS) { trimNetworks() }

    private class Loaded<T : AutoCloseable>(val directory: String, val version: String, val value: T)

    private class Detector(val network: MnnNetwork, val thresholds: DetectionThresholds) : AutoCloseable {
        override fun close() = network.close()
    }

    private class Recognizer(val network: MnnNetwork, val dictionary: List<String>, val rightToLeft: Boolean) : AutoCloseable {
        override fun close() = network.close()
    }

    override suspend fun recognize(image: Bitmap, language: Language, model: InstalledOcrModel, required: List<InstalledOcrModel>): OcrPage =
        withContext(Dispatchers.Default) {
            trim.cancel()
            val diagStarted = SystemClock.elapsedRealtime() // TEMPORARY DIAGNOSTICS BUILD
            try {
                mutex.withLock {
                    // TEMPORARY DIAGNOSTICS BUILD: loads, network runs and session resizes (ai/notes/diagnostics.md).
                    val diagLocked = SystemClock.elapsedRealtime()
                    val diagLoads = diagLoadCount
                    val detectorModel = required.singleOrNull() ?: throw IOException("A recognizer needs one detector")
                    val detector = loaded(detectorModel, detector, { detector = it }) { detector(it) }
                    val recognizer = loaded(model, recognizer, { recognizer = it }) { recognizer(it) }
                    val diagLoaded = SystemClock.elapsedRealtime()
                    val detectorRuns = NetworkRuns(diagShapes[detector.network])
                    val recognizerRuns = NetworkRuns(diagShapes[recognizer.network])
                    val page = PaddlePipeline(
                        detector = Mnn(detector.network, detectorRuns),
                        recognizer = Mnn(recognizer.network, recognizerRuns),
                        dictionary = recognizer.dictionary,
                        thresholds = detector.thresholds,
                    ).recognize(image)
                    // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
                    diagShapes[detector.network] = detectorRuns.shape
                    diagShapes[recognizer.network] = recognizerRuns.shape
                    val diagDone = SystemClock.elapsedRealtime()
                    Diag.ocrPass(
                        "paddle", "recognize", model.id, image.width, image.height, diagDone - diagStarted,
                        "wait_ms" to diagLocked - diagStarted,
                        "load_ms" to (diagLoaded - diagLocked).takeIf { diagLoadCount != diagLoads },
                        "cold" to (diagLoadCount != diagLoads),
                        "det" to detectorRuns.describe(),
                        "rec" to recognizerRuns.describe(),
                        "other_ms" to diagDone - diagLoaded - detectorRuns.ms - recognizerRuns.ms,
                        "lines" to page.paragraphs.size,
                        "mnn_mb" to (detector.network.memoryMb + recognizer.network.memoryMb).toInt(),
                    )
                    if (recognizer.rightToLeft) readingOrder(page) else page
                }
            } finally {
                trim.schedule()
            }
        }

    private suspend fun trimNetworks() {
        mutex.withLock {
            val detector = detector?.value ?: return
            val recognizer = recognizer?.value ?: return
            val started = SystemClock.elapsedRealtime()
            val before = detector.network.memoryMb + recognizer.network.memoryMb
            val diagNative = android.os.Debug.getNativeHeapAllocatedSize() shr 10 // TEMPORARY DIAGNOSTICS BUILD
            detector.network.trim(DETECTOR_TRIM_SHAPE)
            recognizer.network.trim(RECOGNIZER_TRIM_SHAPE)
            val after = detector.network.memoryMb + recognizer.network.memoryMb
            Log.i(TAG, "Freed the memory of large inputs: ${before.roundToInt()} -> ${after.roundToInt()} MB in ${SystemClock.elapsedRealtime() - started} ms")
            // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
            diagShapes[detector.network] = DETECTOR_TRIM_SHAPE.toList()
            diagShapes[recognizer.network] = RECOGNIZER_TRIM_SHAPE.toList()
            Diag.event(
                "model_trim",
                "engine" to "paddle",
                "before_mb" to before.roundToInt(),
                "after_mb" to after.roundToInt(),
                "ms" to SystemClock.elapsedRealtime() - started,
                "native_before_kb" to diagNative,
                "native_kb" to (android.os.Debug.getNativeHeapAllocatedSize() shr 10),
            )
            Diag.memory("model_trim")
        }
    }

    override fun release() {
        trim.cancel()
        // Not under the mutex: a scan in progress keeps its models and frees them at the next release or model change.
        if (!mutex.tryLock()) {
            Diag.event("model_release", "engine" to "paddle", "busy" to true) // TEMPORARY DIAGNOSTICS BUILD
            return
        }
        try {
            Diag.event("model_release", "engine" to "paddle", "loaded" to (detector != null || recognizer != null)) // TEMPORARY DIAGNOSTICS BUILD
            detector?.value?.close()
            recognizer?.value?.close()
            detector = null
            recognizer = null
            diagShapes.clear() // TEMPORARY DIAGNOSTICS BUILD
        } finally {
            mutex.unlock()
        }
    }

    private inline fun <T : AutoCloseable> loaded(
        model: InstalledOcrModel,
        current: Loaded<T>?,
        store: (Loaded<T>?) -> Unit,
        load: (InstalledOcrModel) -> T,
    ): T {
        val directory = model.directory.path
        current?.takeIf { it.directory == directory && it.version == model.version }?.let { return it.value }
        current?.value?.close()
        store(null)
        val diagStarted = SystemClock.elapsedRealtime() // TEMPORARY DIAGNOSTICS BUILD
        val value = load(model)
        Log.i(TAG, "Loaded ${model.id} ${model.version}")
        // TEMPORARY DIAGNOSTICS BUILD (ai/notes/diagnostics.md).
        diagLoadCount++
        Diag.event(
            "model_load",
            "engine" to "paddle",
            "model" to model.id,
            "version" to model.version,
            "replaced" to (current != null),
            "ms" to SystemClock.elapsedRealtime() - diagStarted,
            *Diag.memoryNow(),
        )
        store(Loaded(directory, model.version, value))
        return value
    }

    private fun detector(model: InstalledOcrModel): Detector {
        val properties = Properties()
        model.file(DETECTOR_FILE).takeIf { it.isFile }?.reader()?.use(properties::load)
        return Detector(MnnNetwork(model.file(MODEL_FILE).path, THREADS), DetectionThresholds.parse(properties))
    }

    private fun recognizer(model: InstalledOcrModel): Recognizer {
        val characters = model.file(DICTIONARY_FILE).takeIf { it.isFile }?.readLines()
            ?: throw IOException("The recognizer has no character list")
        val network = MnnNetwork(model.file(MODEL_FILE).path, THREADS)
        return Recognizer(network, PaddlePipeline.dictionary(characters), characters.any(::hasRightToLeftLetter))
    }

    // TEMPORARY DIAGNOSTICS BUILD: each network's last input shape, and how many models were loaded (ai/notes/diagnostics.md).
    private val diagShapes = HashMap<MnnNetwork, List<Int>?>()
    private var diagLoadCount = 0

    /** One of the pipeline's networks on MNN; closing it is left to the engine. */
    private class Mnn(private val network: MnnNetwork, private val runs: NetworkRuns? = null) : PaddleModel {
        // TEMPORARY DIAGNOSTICS BUILD: each run's time and shape (ai/notes/diagnostics.md).
        override fun run(input: Tensor): Tensor {
            val started = SystemClock.elapsedRealtime()
            val output = network.run(MnnTensor(input.data, input.shape)).let { Tensor(it.data, it.shape) }
            runs?.add(input.shape, SystemClock.elapsedRealtime() - started)
            return output
        }

        override fun close() = Unit
    }

    companion object {
        /** The engine of the Paddle models in the catalog. */
        const val ID = "paddle"
        private const val TAG = "PaddleOcr"
        private const val MODEL_FILE = "model.mnn"
        private const val DICTIONARY_FILE = "dict.txt"
        private const val DETECTOR_FILE = "detector.properties"

        /**
         * How long after a scan the memory of its largest input is kept. Taking it again costs a full-page scan
         * ~0.25 s (emulator); while scans follow each other, it stays.
         */
        private const val TRIM_DELAY_MS = 10_000L

        /** Small inputs the networks are sized for while scans pause: the detector's smallest, one recognizer step. */
        private val DETECTOR_TRIM_SHAPE = intArrayOf(1, 3, 32, 32)
        private val RECOGNIZER_TRIM_SHAPE = intArrayOf(1, 3, TextRecognition.HEIGHT, TextRecognition.WIDTH_STEP)

        /** As in the comparison's timings. */
        private const val THREADS = 4
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PaddleModule {
    @Binds
    @IntoSet
    abstract fun bindEngine(engine: PaddleOcrEngine): ModelOcrEngine
}
