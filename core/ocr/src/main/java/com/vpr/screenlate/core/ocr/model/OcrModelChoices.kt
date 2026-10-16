package com.vpr.screenlate.core.ocr.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.preferenceKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A model a language may read with: one of the language's catalog entries.
 *
 * @property id the model's id in [OcrModelStore].
 * @property engine the [ModelOcrEngine] that reads it.
 * @property requires models it reads only together with, such as a text detector.
 * @property recommended the model the language reads with unless the user chose another.
 */
data class LanguageModel(
    val id: String,
    val engine: String,
    val requires: List<String> = emptyList(),
    val recommended: Boolean = false,
)

/** The models each language may read with, in the catalog's order; the app takes them from the download catalog. */
fun interface LanguageModels {
    fun of(language: Language): List<LanguageModel>
}

/** The on-device model the user chose per language: a [LanguageModel.id] or [ML_KIT]; none means the default. */
@Singleton
class OcrModelChoices @Inject constructor(private val dataStore: DataStore<Preferences>) {
    fun chosen(language: Language): Flow<String?> = dataStore.data.map { it[key(language)] }.distinctUntilChanged()

    suspend fun choose(language: Language, id: String) {
        dataStore.edit { it[key(language)] = id }
    }

    companion object {
        /** The choice of ML Kit's bundled model for the language's script. */
        const val ML_KIT = "mlkit"

        /** The preference name; another language than Japanese adds its code ([preferenceKey]). */
        const val KEY = "ocr_model"

        private fun key(language: Language) = stringPreferencesKey(language.preferenceKey(KEY))
    }
}

/**
 * What reads a language on the device: [chosen] when it can, else the recommended model, else ML Kit where it reads
 * the script, else the first listed model that can. A listed model can read when [usable] says so (it and the models
 * it requires are installed and its engine is in the app). Null when nothing can.
 */
internal fun selectDeviceModel(
    chosen: String?,
    listed: List<LanguageModel>,
    mlKitReads: Boolean,
    usable: (LanguageModel) -> Boolean,
): DeviceModel? {
    val candidates = listed.filter(usable)
    if (chosen == OcrModelChoices.ML_KIT && mlKitReads) return DeviceModel.MlKit
    candidates.firstOrNull { it.id == chosen }?.let { return DeviceModel.Listed(it) }
    candidates.firstOrNull { it.recommended }?.let { return DeviceModel.Listed(it) }
    if (mlKitReads) return DeviceModel.MlKit
    return candidates.firstOrNull()?.let(DeviceModel::Listed)
}

/** The model [selectDeviceModel] picked. */
sealed interface DeviceModel {
    /** The id stored by [OcrModelChoices] for this model. */
    val id: String

    data object MlKit : DeviceModel {
        override val id = OcrModelChoices.ML_KIT
    }

    data class Listed(val model: LanguageModel) : DeviceModel {
        override val id get() = model.id
    }
}
