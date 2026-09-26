package com.vpr.screenlate.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.LookupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** Results for one search text. */
data class SearchResults(val text: String, val results: List<LookupResult>, val noDictionaries: Boolean)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val lookup: DictionaryLookup,
    val ankiDroid: AnkiDroid,
    val notes: AnkiNotes,
    val audio: AudioFinder,
    val audioSettings: AudioSettingsRepository,
    appSettings: AppSettingsRepository,
) : ViewModel() {
    val query = MutableStateFlow("")

    val themeMode: StateFlow<ThemeMode> =
        appSettings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val results: StateFlow<SearchResults?> = query
        .debounce(SEARCH_DELAY_MS)
        .mapLatest { text -> if (text.isBlank()) null else search(text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dictionaryLookup: DictionaryLookup get() = lookup

    suspend fun search(text: String, primaryReading: String? = null): SearchResults {
        val trimmed = text.trim()
        val found = runCatching {
            lookup.lookup(trimmed, LANGUAGE, scanLength = trimmed.length.coerceAtLeast(1), primaryReading = primaryReading)
        }.getOrDefault(emptyList())
        return SearchResults(trimmed, found, noDictionaries = found.isEmpty() && !lookup.hasTermDictionaries())
    }

    suspend fun styles(): List<DictionaryStyle> = runCatching { lookup.styles(LANGUAGE) }.getOrDefault(emptyList())

    private companion object {
        const val SEARCH_DELAY_MS = 150L
        val LANGUAGE = Language.JAPANESE
    }
}
