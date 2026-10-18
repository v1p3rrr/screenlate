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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * An on-device engine whose code is in the APK and whose models are downloads ([OcrModelStore]). Its pages carry
 * [OcrEngineType.DEVICE_MODEL].
 */
interface ModelOcrEngine {
    /** The `engine` of its models in the catalog and the store. */
    val id: String

    /**
     * As [OcrEngine.recognize], with [model], one of this engine's installed models, and the installed models it
     * [requires][LanguageModel.requires] in the listed order.
     */
    suspend fun recognize(image: Bitmap, language: Language, model: InstalledOcrModel, required: List<InstalledOcrModel>): OcrPage

    fun release() = Unit
}

/**
 * On-device recognition with the model each language reads with ([selectDeviceModel]): the user's choice, the
 * recommended downloaded model, or ML Kit's bundled one for its scripts.
 *
 * A downloaded model is [held][OcrModelStore.hold] while it reads, so that deleting it waits for the scan; one whose
 * engine fails is [verified][OcrModelStore.verify].
 */
@Singleton
class DeviceOcr internal constructor(
    private val mlKit: OcrEngine,
    engines: Set<ModelOcrEngine>,
    private val installed: () -> List<InstalledOcrModel>,
    private val listed: LanguageModels,
    private val chosen: suspend (Language) -> String?,
    private val hold: (List<String>) -> AutoCloseable? = { AutoCloseable {} },
    private val verify: suspend (List<String>) -> Unit = {},
) : OcrEngine {
    @Inject
    constructor(
        mlKit: MlKitOcrEngine,
        engines: Set<@JvmSuppressWildcards ModelOcrEngine>,
        store: OcrModelStore,
        listed: LanguageModels,
        choices: OcrModelChoices,
    ) : this(mlKit, engines, { store.models.value }, listed, { choices.chosen(it).first() }, store::hold, { store.verify(it) })

    private val engines = engines.associateBy { it.id }

    override val type = OcrEngineType.ML_KIT

    override fun reads(language: Language): Boolean {
        if (mlKit.reads(language)) return true
        val models = installed().associateBy { it.id }
        return listed.of(language).any { usable(it, models) }
    }

    override suspend fun recognize(image: Bitmap, language: Language): OcrPage {
        // A model deleted between its selection and its hold is no longer installed: the next selection skips it.
        repeat(MAX_SELECTIONS) {
            val models = installed().associateBy { it.id }
            when (val selected = select(language, models)) {
                null -> throw NoDeviceOcrException()
                DeviceModel.MlKit -> return mlKit.recognize(image, language)
                is DeviceModel.Listed -> {
                    val model = selected.model
                    val ids = listOf(model.id) + model.requires
                    val held = hold(ids) ?: return@repeat
                    return held.use { read(image, language, model, models) }
                }
            }
        }
        throw NoDeviceOcrException()
    }

    private suspend fun read(image: Bitmap, language: Language, model: LanguageModel, models: Map<String, InstalledOcrModel>): OcrPage =
        try {
            engines.getValue(model.engine).recognize(image, language, models.getValue(model.id), model.requires.map(models::getValue))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Damaged files take the model out of the installed ones; the next scan reads with another.
            withContext(NonCancellable) { verify(listOf(model.id) + model.requires) }
            throw e
        }

    /** What reads [language] now; null when nothing on the device can. */
    suspend fun active(language: Language): DeviceModel? = select(language, installed().associateBy { it.id })

    override fun release() {
        mlKit.release()
        engines.values.forEach { it.release() }
    }

    private suspend fun select(language: Language, models: Map<String, InstalledOcrModel>): DeviceModel? =
        selectDeviceModel(chosen(language), listed.of(language), mlKit.reads(language)) { usable(it, models) }

    /** The model, the models it requires and its engine are all here. */
    private fun usable(model: LanguageModel, installed: Map<String, InstalledOcrModel>): Boolean =
        model.engine in engines && model.id in installed && model.requires.all { it in installed }

    private companion object {
        /** Selections tried while models are deleted under the scan; each failed one has one model fewer. */
        const val MAX_SELECTIONS = 3
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModelOcrEngineModule {
    /** The engines with downloaded models. */
    @Multibinds
    abstract fun modelEngines(): Set<ModelOcrEngine>
}
