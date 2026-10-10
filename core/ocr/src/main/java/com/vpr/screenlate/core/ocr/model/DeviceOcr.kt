package com.vpr.screenlate.core.ocr.model

import android.graphics.Bitmap
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.NoDeviceOcrException
import com.vpr.screenlate.core.ocr.OcrEngine
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.mlkit.MlKitOcrEngine
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An on-device engine whose code is in the APK and whose models are downloads ([OcrModelStore]). Its pages carry
 * [OcrEngineType.DEVICE_MODEL].
 */
interface ModelOcrEngine {
    /** The `engine` of its models in the catalog and the store. */
    val id: String

    /** Whether [models], this engine's installed models, read [language]. */
    fun reads(language: Language, models: List<InstalledOcrModel>): Boolean

    /** As [OcrEngine.recognize], with this engine's installed [models]. */
    suspend fun recognize(image: Bitmap, language: Language, models: List<InstalledOcrModel>): OcrPage

    fun release() = Unit
}

/**
 * On-device recognition: ML Kit for the scripts it has a model for, else the first [ModelOcrEngine] whose downloaded
 * models read the language.
 */
@Singleton
class DeviceOcr internal constructor(
    private val mlKit: OcrEngine,
    private val engines: Set<ModelOcrEngine>,
    private val models: () -> List<InstalledOcrModel>,
) : OcrEngine {
    @Inject
    constructor(mlKit: MlKitOcrEngine, engines: Set<@JvmSuppressWildcards ModelOcrEngine>, store: OcrModelStore) :
        this(mlKit, engines, { store.models.value })

    override val type = OcrEngineType.ML_KIT

    override fun reads(language: Language): Boolean = mlKit.reads(language) || modelEngine(language) != null

    override suspend fun recognize(image: Bitmap, language: Language): OcrPage {
        if (mlKit.reads(language)) return mlKit.recognize(image, language)
        val (engine, own) = modelEngine(language) ?: throw NoDeviceOcrException()
        return engine.recognize(image, language, own)
    }

    override fun release() {
        mlKit.release()
        engines.forEach { it.release() }
    }

    private fun modelEngine(language: Language): Pair<ModelOcrEngine, List<InstalledOcrModel>>? {
        val installed = models()
        return engines.firstNotNullOfOrNull { engine ->
            val own = installed.filter { it.engine == engine.id }
            (engine to own).takeIf { own.isNotEmpty() && engine.reads(language, own) }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModelOcrEngineModule {
    /** The engines with downloaded models; none yet. */
    @Multibinds
    abstract fun modelEngines(): Set<ModelOcrEngine>
}
