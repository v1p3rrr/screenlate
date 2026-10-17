package com.vpr.screenlate.core.ocr.paddle

import android.graphics.Bitmap
import android.util.Log
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.mnn.MnnNetwork
import com.vpr.screenlate.core.ocr.mnn.MnnTensor
import com.vpr.screenlate.core.ocr.model.InstalledOcrModel
import com.vpr.screenlate.core.ocr.model.ModelOcrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.IOException
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * PP-OCR on MNN. A recognizer's directory holds `model.mnn` and `dict.txt`, its characters one per line; the
 * detector it requires holds `model.mnn` and may hold `detector.properties` with its thresholds
 * ([DetectionThresholds.parse]). The detector and one recognizer stay loaded until [release] or a scan with
 * another model. A recognizer whose characters include right-to-left letters (Arabic) reads its crops from left to
 * right; its lines are turned into reading order ([readingOrder]).
 */
@Singleton
class PaddleOcrEngine @Inject constructor() : ModelOcrEngine {
    override val id = ID

    private val mutex = Mutex()
    private var detector: Loaded<Detector>? = null
    private var recognizer: Loaded<Recognizer>? = null

    private class Loaded<T : AutoCloseable>(val directory: String, val version: String, val value: T)

    private class Detector(val network: MnnNetwork, val thresholds: DetectionThresholds) : AutoCloseable {
        override fun close() = network.close()
    }

    private class Recognizer(val network: MnnNetwork, val dictionary: List<String>, val rightToLeft: Boolean) : AutoCloseable {
        override fun close() = network.close()
    }

    override suspend fun recognize(image: Bitmap, language: Language, model: InstalledOcrModel, required: List<InstalledOcrModel>): OcrPage =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val detectorModel = required.singleOrNull() ?: throw IOException("A recognizer needs one detector")
                val detector = loaded(detectorModel, detector, { detector = it }) { detector(it) }
                val recognizer = loaded(model, recognizer, { recognizer = it }) { recognizer(it) }
                val page = PaddlePipeline(
                    detector = Mnn(detector.network),
                    recognizer = Mnn(recognizer.network),
                    dictionary = recognizer.dictionary,
                    thresholds = detector.thresholds,
                ).recognize(image)
                if (recognizer.rightToLeft) readingOrder(page) else page
            }
        }

    override fun release() {
        // Not under the mutex: a scan in progress keeps its models and frees them at the next release or model change.
        if (!mutex.tryLock()) return
        try {
            detector?.value?.close()
            recognizer?.value?.close()
            detector = null
            recognizer = null
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
        val value = load(model)
        Log.i(TAG, "Loaded ${model.id} ${model.version}")
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

    /** One of the pipeline's networks on MNN; closing it is left to the engine. */
    private class Mnn(private val network: MnnNetwork) : PaddleModel {
        override fun run(input: Tensor): Tensor = network.run(MnnTensor(input.data, input.shape)).let { Tensor(it.data, it.shape) }

        override fun close() = Unit
    }

    companion object {
        /** The engine of the Paddle models in the catalog. */
        const val ID = "paddle"
        private const val TAG = "PaddleOcr"
        private const val MODEL_FILE = "model.mnn"
        private const val DICTIONARY_FILE = "dict.txt"
        private const val DETECTOR_FILE = "detector.properties"

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
