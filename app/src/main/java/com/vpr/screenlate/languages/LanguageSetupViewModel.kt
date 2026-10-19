package com.vpr.screenlate.languages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.languageCode
import com.vpr.screenlate.core.ocr.mlkit.MlKitOcrEngine
import com.vpr.screenlate.core.ocr.model.OcrModelChoices
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.dictionaries.catalogTargetOrder
import com.vpr.screenlate.dictionary.api.catalog.Catalog
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.DictionaryStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property glosses the gloss language slots; the one at [choosable] is the user's to fill, null for none.
 * @property choices the user's ticks by entry id; unset items follow [SetupCategory.ticked].
 * @property mainCandidates the ticked models with a "main" switch, by model id; [mainModel] is the one switched on.
 */
data class LanguageSetupState(
    val language: Language,
    val glosses: List<String?> = emptyList(),
    val choosable: Int? = null,
    val glossChoices: List<String> = emptyList(),
    val categories: List<SetupCategory> = emptyList(),
    val choices: Map<String, Boolean> = emptyMap(),
    val missing: List<CatalogCategory> = emptyList(),
    val downloads: List<CatalogEntry> = emptyList(),
    val enableDictionaries: Set<Long> = emptySet(),
    val mainCandidates: List<String> = emptyList(),
    val mainModel: String? = null,
    val totals: SetupTotals = SetupTotals(0, 0, 0),
    val freeBytes: Long = Long.MAX_VALUE,
    val loaded: Boolean = false,
) {
    val enoughSpace: Boolean get() = totals.neededBytes <= freeBytes

    val canConfirm: Boolean get() = loaded && missing.isEmpty() && enoughSpace
}

/** The download screen of a language being turned on (route arguments `code` and `firstRun`). */
@HiltViewModel
class LanguageSetupViewModel @Inject constructor(
    savedState: SavedStateHandle,
    catalog: DictionaryCatalog,
    repository: DictionaryRepository,
    private val storage: DictionaryStorage,
    private val switch: LanguageSwitch,
    private val models: OcrModelStore,
    private val modelChoices: OcrModelChoices,
    private val mlKit: MlKitOcrEngine,
) : ViewModel() {
    val language: Language = requireNotNull(Language.of(savedState.get<String>(ARG_CODE)))
    private val firstRun: Boolean = savedState.get<Boolean>(ARG_FIRST_RUN) ?: false
    private val interfaceLanguage = Locale.getDefault().languageCode

    /** The user's pick for the slot they fill, [GlossPick.code] null for none; null until they pick. */
    private val chosenGloss = MutableStateFlow<GlossPick?>(null)
    private val picks = MutableStateFlow(Picks())

    /** The user's ticks by entry id and main model. */
    private data class Picks(val ticks: Map<String, Boolean> = emptyMap(), val main: MainModelPick? = null)

    val state: StateFlow<LanguageSetupState> = combine(
        catalog.catalog(),
        repository.dictionaries,
        models.models,
        chosenGloss,
        picks,
    ) { catalog, installed, _, chosen, picks ->
        build(catalog, installed, chosen, picks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LanguageSetupState(language))

    private fun build(
        catalog: Catalog,
        installed: List<DictionaryEntity>,
        chosen: GlossPick?,
        picks: Picks,
    ): LanguageSetupState {
        val choices = picks.ticks
        val order = catalogTargetOrder(language.dictionaryCode, Locale.getDefault())
        val available = glossChoices(catalog.entries, language.dictionaryCode, taken = emptyList(), order)
        val slots = glossSlots(language.dictionaryCode, interfaceLanguage, available)
        val choosable = slots.indexOf(null).takeIf { it >= 0 }
        val glosses = if (choosable == null || chosen == null) slots else slots.toMutableList().also { it[choosable] = chosen.code }
        val mlKitReads = mlKit.reads(language)
        val categories = setupCategories(catalog, language, glosses.filterNotNull(), installed, mlKitReads) { it.isInstalledIn(models) }
        val downloads = setupDownloads(categories, choices)
        val candidates = mainModelCandidates(categories, choices, catalog.entries.flatMapTo(hashSetOf()) { it.requires })
        return LanguageSetupState(
            language = language,
            glosses = glosses,
            choosable = choosable,
            glossChoices = choosable?.let { index -> available.filterNot { it in glosses.filterIndexed { i, _ -> i != index } } }
                .orEmpty(),
            categories = categories,
            choices = choices,
            missing = missingChoices(categories, choices),
            downloads = downloads,
            enableDictionaries = setupInstalled(categories, choices, installed),
            mainCandidates = candidates.mapNotNull { it.model },
            mainModel = mainModel(candidates, picks.main, mlKitReads, choices.filterValues { it }.keys),
            totals = setupTotals(downloads),
            freeBytes = freeBytes(),
            loaded = true,
        )
    }

    private fun freeBytes(): Long = generateSequence(storage.root) { it.parentFile }.first { it.exists() }.usableSpace

    /** Fills the user's gloss language slot; null leaves it empty. */
    fun chooseGloss(code: String?) {
        chosenGloss.value = GlossPick(code)
    }

    fun tick(item: SetupItem, ticked: Boolean) {
        picks.update { it.copy(ticks = it.ticks + (item.entry.id to ticked)) }
    }

    /** Switches model [model] on as the main one, or off, which leaves ML Kit where it reads the language. */
    fun setMain(model: String, main: Boolean) {
        picks.update { it.copy(main = MainModelPick(model.takeIf { main })) }
    }

    /** Makes ML Kit the main model, or, switched off, the first ticked model. */
    fun setMlKitMain(main: Boolean) {
        val model = if (main) null else state.value.mainCandidates.firstOrNull() ?: return
        picks.update { it.copy(main = MainModelPick(model)) }
    }

    private val confirmation = MutableStateFlow(Confirmation.NONE)

    /** How far the confirmation got; it outlives the screen's recreation, e.g. on rotation. */
    val confirmed: StateFlow<Confirmation> = confirmation

    /**
     * Enables ticked installed dictionaries, queues the other choices and makes the main model the language's, once;
     * later calls do nothing. With no main model, ML Kit stays the language's model where it reads it.
     */
    fun confirm() {
        if (!confirmation.compareAndSet(Confirmation.NONE, Confirmation.TURNING_ON)) return
        val selected = state.value
        viewModelScope.launch {
            switch.turnOn(language, selected.downloads, firstRun, selected.enableDictionaries)
            val main = selected.mainModel ?: OcrModelChoices.ML_KIT.takeIf { selected.mainCandidates.isNotEmpty() && mlKit.reads(language) }
            if (main != null) modelChoices.choose(language, main)
            confirmation.value = Confirmation.QUEUED
        }
    }

    enum class Confirmation { NONE, TURNING_ON, QUEUED }

    private data class GlossPick(val code: String?)

    companion object {
        const val ARG_CODE = "code"
        const val ARG_FIRST_RUN = "firstRun"
    }
}
