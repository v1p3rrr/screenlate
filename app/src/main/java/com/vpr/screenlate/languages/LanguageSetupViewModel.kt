package com.vpr.screenlate.languages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
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
import kotlinx.coroutines.launch

/**
 * @property glosses the gloss language slots; the one at [choosable] is the user's to fill, null for none.
 * @property choices the user's ticks by entry id; unset items follow [SetupCategory.ticked].
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
) : ViewModel() {
    val language: Language = requireNotNull(Language.of(savedState.get<String>(ARG_CODE)))
    private val firstRun: Boolean = savedState.get<Boolean>(ARG_FIRST_RUN) ?: false
    private val interfaceLanguage = Locale.getDefault().language

    /** The user's pick for the slot they fill, [GlossPick.code] null for none; null until they pick. */
    private val chosenGloss = MutableStateFlow<GlossPick?>(null)
    private val choices = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val state: StateFlow<LanguageSetupState> = combine(
        catalog.catalog(),
        repository.dictionaries,
        models.models,
        chosenGloss,
        choices,
    ) { catalog, installed, _, chosen, choices ->
        build(catalog, installed, chosen, choices)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LanguageSetupState(language))

    private fun build(
        catalog: Catalog,
        installed: List<DictionaryEntity>,
        chosen: GlossPick?,
        choices: Map<String, Boolean>,
    ): LanguageSetupState {
        val order = catalogTargetOrder(language.code, Locale.getDefault())
        val available = glossChoices(catalog.entries, language.code, taken = emptyList(), order)
        val slots = glossSlots(language.code, interfaceLanguage, available)
        val choosable = slots.indexOf(null).takeIf { it >= 0 }
        val glosses = if (choosable == null || chosen == null) slots else slots.toMutableList().also { it[choosable] = chosen.code }
        val categories = setupCategories(catalog, language.code, glosses.filterNotNull(), installed) { it.isInstalledIn(models) }
        val downloads = setupDownloads(categories, choices)
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
        choices.value += item.entry.id to ticked
    }

    private val confirmation = MutableStateFlow(Confirmation.NONE)

    /** How far the confirmation got; it outlives the screen's recreation, e.g. on rotation. */
    val confirmed: StateFlow<Confirmation> = confirmation

    /** Turns the language on with the ticked downloads queued, once; later calls do nothing. */
    fun confirm() {
        if (!confirmation.compareAndSet(Confirmation.NONE, Confirmation.TURNING_ON)) return
        val downloads = state.value.downloads
        viewModelScope.launch {
            switch.turnOn(language, downloads, firstRun)
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
