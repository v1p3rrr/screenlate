package com.vpr.screenlate.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.AnkiProblem
import com.vpr.screenlate.core.anki.AnkiStatus
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.AudioSourceFailure
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.DictionaryRepair
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.isFor
import com.vpr.screenlate.search.SearchViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DictionarySummary(val installed: Int = 0, val enabled: Int = 0, val importing: Boolean = false)

/** Deck and note type when Anki export is configured, null otherwise. */
data class AnkiSummary(val deck: String, val model: String)

/** Files of a downloaded or imported dictionary are gone; [catalogEntry] allows downloading it again. */
data class MissingDictionary(val dictionary: DictionaryEntity, val catalogEntry: CatalogEntry?)

/**
 * Failures of the configured [sources] in their order; those of sources edited or removed since, or only tested in
 * the source editor, are left out.
 */
internal fun currentFailures(failures: List<AudioSourceFailure>, sources: List<AudioSource>): List<AudioSourceFailure> =
    failures.filter { it.source in sources }.sortedBy { sources.indexOf(it.source) }

/**
 * Whether lookups would find no term dictionary: none of [dictionaries] is on, has terms and is for [language]. Missing
 * files are a card of their own.
 */
internal fun noTermDictionaries(dictionaries: List<DictionaryEntity>, language: Language): Boolean =
    dictionaries.none { it.enabled && it.termCount > 0 && it.isFor(language) }

/** Something that broke without the user changing Screenlate's settings. */
sealed interface HomeProblem {
    data class Anki(val problem: AnkiProblem) : HomeProblem

    /** Downloaded or imported dictionaries whose files are gone, in one card. */
    data class MissingDictionaries(val dictionaries: List<MissingDictionary>) : HomeProblem

    data object NoTermDictionaries : HomeProblem

    /** Every audio source that failed recently, in one card. */
    data class AudioSources(val failures: List<AudioSourceFailure>) : HomeProblem
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    private val imports: DictionaryImports,
    private val repair: DictionaryRepair,
    private val bundled: BundledDictionaries,
    private val catalog: DictionaryCatalog,
    private val notes: AnkiNotes,
    private val audio: AudioFinder,
    private val audioSettings: AudioSettingsRepository,
    ankiSettings: AnkiSettingsRepository,
) : ViewModel() {
    val anki: StateFlow<AnkiSummary?> = ankiSettings.settings
        .map { if (it.configured) AnkiSummary(it.deckName.orEmpty(), it.modelName.orEmpty()) else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dictionaries: StateFlow<DictionarySummary> =
        combine(repository.dictionaries, imports.tasks) { dictionaries, tasks ->
            DictionarySummary(
                installed = dictionaries.size,
                enabled = dictionaries.count { it.enabled },
                importing = tasks.any { !it.finished },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DictionarySummary())

    private val checked = MutableStateFlow<List<HomeProblem>>(emptyList())
    private var refreshJob: Job? = null

    /** Dictionaries downloaded again from the card; their files stay missing until the download ends. */
    private val downloading = mutableSetOf<Long>()

    /** Problems found by the last [refresh], plus "no dictionary" while nothing is being installed. */
    val problems: StateFlow<List<HomeProblem>> = combine(checked, repository.dictionaries, imports.tasks) { found, all, tasks ->
        val noTerms = tasks.none { !it.finished } && noTermDictionaries(all, SearchViewModel.LANGUAGE)
        found.filterNot { it is HomeProblem.NoTermDictionaries } + listOfNotNull(HomeProblem.NoTermDictionaries.takeIf { noTerms })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Checks AnkiDroid, dictionary files and audio sources again; call when the screen is shown. */
    fun refresh() {
        // A check started earlier would otherwise finish last and bring back what a newer one found fixed.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val found = mutableListOf<HomeProblem>()
            (notes.status() as? AnkiStatus.Broken)?.let { found += HomeProblem.Anki(it.problem) }
            if (imports.tasks.first().all { it.finished }) downloading.clear()
            val missing = try {
                repair.repair()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Dictionary check failed: ${e.javaClass.simpleName}")
                emptyList()
            }.filterNot { it.id in downloading }
            if (missing.isNotEmpty()) {
                val entries = try {
                    catalog.entries().first()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                found += HomeProblem.MissingDictionaries(
                    missing.map { dictionary -> MissingDictionary(dictionary, entries.firstOrNull { it.matches(dictionary) }) },
                )
            }
            currentFailures(audio.recentFailures(), audioSettings.current().sources)
                .takeIf { it.isNotEmpty() }
                ?.let { found += HomeProblem.AudioSources(it) }
            checked.value = found
        }
    }

    fun downloadAgain(missing: MissingDictionary) {
        val entry = missing.catalogEntry ?: return
        imports.download(
            entry.downloadUrl,
            entry.title,
            indexUrl = entry.indexUrl.takeIf { entry.resolveLatest },
            replaces = missing.dictionary.id,
        )
        downloading += missing.dictionary.id
        resolved(missing)
    }

    fun remove(missing: MissingDictionary) {
        viewModelScope.launch {
            if (missing.dictionary.bundled) bundled.markDeleted(missing.dictionary.title)
            repository.delete(missing.dictionary.id)
            resolved(missing)
        }
    }

    private fun resolved(missing: MissingDictionary) {
        checked.value = checked.value.mapNotNull { problem ->
            if (problem !is HomeProblem.MissingDictionaries) return@mapNotNull problem
            HomeProblem.MissingDictionaries(problem.dictionaries - missing).takeIf { it.dictionaries.isNotEmpty() }
        }
    }

    fun dismissAudioFailures() {
        audio.clearFailures()
        checked.value = checked.value.filterNot { it is HomeProblem.AudioSources }
    }

    private companion object {
        const val TAG = "Home"
    }
}
