package com.vpr.screenlate.languages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import com.vpr.screenlate.core.ocr.mlkit.MlKitOcrEngine
import com.vpr.screenlate.core.ocr.model.ModelOcrEngine
import com.vpr.screenlate.core.ocr.model.OcrModelChoices
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The on-device model of each turned-on language: chosen, downloaded and deleted in the recognition settings. */
@HiltViewModel
class OcrModelsViewModel @Inject constructor(
    languageProfiles: LanguageProfiles,
    private val catalog: DictionaryCatalog,
    private val store: OcrModelStore,
    private val choices: OcrModelChoices,
    private val imports: DictionaryImports,
    mlKit: MlKitOcrEngine,
    engines: Set<@JvmSuppressWildcards ModelOcrEngine>,
) : ViewModel() {
    private val engineIds = engines.mapTo(hashSetOf()) { it.id }

    /** Null until the first read. */
    val lists: StateFlow<List<LanguageModelList>?> = combine(
        languageProfiles.state,
        catalog.entries(),
        store.models,
        choices.all,
        imports.tasks,
    ) { profiles, entries, installed, chosen, tasks ->
        ocrModelLists(profiles.turnedOn, entries, installed.associate { it.id to it.bytes }, chosen, mlKit::reads, engineIds, tasks)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The languages' flags ([LanguageProfilesState.flagCountry]); null until the first read. */
    val profiles: StateFlow<LanguageProfilesState?> =
        languageProfiles.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Makes [option] the model [language] reads with; only an installed one can be chosen. */
    fun choose(language: Language, option: OcrModelOption) {
        if (!option.installed) return
        viewModelScope.launch { choices.choose(language, option.id) }
    }

    /** Queues the model's download with the models it requires; it reads once chosen. */
    fun download(option: OcrModelOption) {
        viewModelScope.launch { option.downloads.forEach { imports.downloadModel(it) } }
    }

    /** Deletes the model, and the models it requires that no other installed model does; an active one stays. */
    fun delete(option: OcrModelOption) {
        if (option.deletion !is ModelDeletion.Allowed) return
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { catalog.local() }
            store.delete(modelsToDelete(option.id, entries, store.models.value.map { it.id }))
        }
    }
}
