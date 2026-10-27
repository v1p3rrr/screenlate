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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
 * On-device recognition with the model each language reads with ([selectDeviceModel]): the user's choice, ML Kit's
 * bundled one for its scripts, or the recommended downloaded model.
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
    /** Emits whenever what reads the language may have changed: its choice or the installed models. */
    private val changes: (Language) -> Flow<Any?> = { emptyFlow() },
) : OcrEngine {
    @Inject
    constructor(
        mlKit: MlKitOcrEngine,
        engines: Set<@JvmSuppressWildcards ModelOcrEngine>,
        store: OcrModelStore,
        listed: LanguageModels,
        choices: OcrModelChoices,
    ) : this(
        mlKit,
        engines,
        { store.models.value },
        listed,
        { choices.chosen(it).first() },
        store::hold,
        { store.verify(it) },
        { combine(choices.chosen(it), store.models) { chosen, models -> chosen to models } },
    )

    private val engines = engines.associateBy { it.id }
    private val pinLock = Any()
    private var pins = 0
    private var releasePending = false

    override val type = OcrEngineType.ML_KIT

    override fun reads(language: Language): Boolean {
        if (mlKit.reads(language)) return true
        val models = installed().associateBy { it.id }
        return listed.of(language).any { usable(it, models) }
    }

    override suspend fun recognize(image: Bitmap, language: Language): OcrPage {
        // A scan needs the engines again: a release asked for while an attempt was pinned no longer applies.
        synchronized(pinLock) { releasePending = false }
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

    private suspend fun read(
        image: Bitmap,
        language: Language,
        model: LanguageModel,
        models: Map<String, InstalledOcrModel>,
        shouldVerify: () -> Boolean = { true },
    ): OcrPage =
        try {
            engines.getValue(model.engine).recognize(image, language, models.getValue(model.id), model.requires.map(models::getValue))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Damaged files take the model out of the installed ones; the next scan reads with another.
            if (shouldVerify()) withContext(NonCancellable) { verify(listOf(model.id) + model.requires) }
            throw e
        }

    /** What reads [language] now; null when nothing on the device can. */
    suspend fun active(language: Language): DeviceModel? = select(language, installed().associateBy { it.id })

    /**
     * Holds [modelId] for a multi-image attempt, never another model; null when it cannot read [language]. The model
     * and its installed files stay fixed until close, even if settings change or the model is deleted meanwhile.
     * Its files are verified once, on the attempt's first failure.
     */
    suspend fun pin(language: Language, modelId: String): PinnedDeviceOcr? {
        repeat(MAX_SELECTIONS) {
            val models = installed().associateBy { it.id }
            val selected = when {
                modelId == OcrModelChoices.ML_KIT && mlKit.reads(language) -> DeviceModel.MlKit
                else -> listed.of(language).firstOrNull { it.id == modelId && usable(it, models) }?.let(DeviceModel::Listed)
            }
            if (selected == null) return null
            val held = when (selected) {
                DeviceModel.MlKit -> AutoCloseable {}
                is DeviceModel.Listed -> hold(listOf(selected.model.id) + selected.model.requires) ?: return@repeat
            }
            val closed = AtomicBoolean(false)
            val verified = AtomicBoolean(false)
            val boundLanguage = language
            synchronized(pinLock) { pins++ }
            val boundEngine = object : OcrEngine {
                override val type = if (selected == DeviceModel.MlKit) OcrEngineType.ML_KIT else OcrEngineType.DEVICE_MODEL
                override fun reads(language: Language) = language == boundLanguage
                override suspend fun recognize(image: Bitmap, language: Language): OcrPage {
                    check(!closed.get()) { "Recognition model already released" }
                    require(language == boundLanguage)
                    return when (selected) {
                        DeviceModel.MlKit -> mlKit.recognize(image, language)
                        is DeviceModel.Listed -> read(image, language, selected.model, models) { verified.compareAndSet(false, true) }
                    }
                }
            }
            return PinnedDeviceOcr(selected.id, boundEngine) {
                if (closed.compareAndSet(false, true)) {
                    try { held.close() } finally {
                        synchronized(pinLock) {
                            pins--
                            if (pins == 0 && releasePending) {
                                releasePending = false
                                releaseEngines()
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    /** What reads [language], now and whenever another model takes over: chosen, installed or deleted. */
    fun activeUpdates(language: Language): Flow<DeviceModel?> = changes(language).map { active(language) }.distinctUntilChanged()

    override fun release() = synchronized(pinLock) {
        if (pins > 0) releasePending = true else releaseEngines()
    }

    private fun releaseEngines() {
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

/** An attempt's model hold. Close only after all readers have terminated. */
class PinnedDeviceOcr internal constructor(val modelId: String, val engine: OcrEngine, private val release: () -> Unit) : AutoCloseable {
    override fun close() = release()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModelOcrEngineModule {
    /** The engines with downloaded models. */
    @Multibinds
    abstract fun modelEngines(): Set<ModelOcrEngine>
}
