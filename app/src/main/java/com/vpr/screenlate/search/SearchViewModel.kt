package com.vpr.screenlate.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.audio.AudioFinder
import com.vpr.screenlate.core.anki.audio.AudioPlayer
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.dictionary.api.DictionaryLookup
import com.vpr.screenlate.dictionary.api.NoTermDictionary
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.DictionaryTagNotes
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.overlay.fonts.PageAppearance
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonObject

/**
 * Results for one search text. [kanji] is the first character's kanji entry when no word was found; [noTermDictionary]
 * is set when nothing was found because no dictionary with definitions is searched.
 */
data class SearchResults(
    val text: String,
    val results: List<LookupResult>,
    val noTermDictionary: NoTermDictionary?,
    val kanji: KanjiResult? = null,
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val lookup: DictionaryLookup,
    val ankiDroid: AnkiDroid,
    val notes: AnkiNotes,
    val audio: AudioFinder,
    val audioSettings: AudioSettingsRepository,
    val audioPlayer: AudioPlayer,
    appSettings: AppSettingsRepository,
    pageAppearance: PageAppearance,
) : ViewModel() {
    /** Fonts and custom CSS of the result page. */
    val appearance: Flow<JsonObject> = pageAppearance.json(LANGUAGE)

    val query = MutableStateFlow("")

    val themeMode: StateFlow<ThemeMode> =
        appSettings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, appSettings.cachedThemeMode ?: ThemeMode.SYSTEM)

    val results: StateFlow<SearchResults?> = query
        .debounce(SEARCH_DELAY_MS)
        .mapLatest { text -> if (text.isBlank()) null else search(text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dictionaryLookup: DictionaryLookup get() = lookup

    suspend fun search(text: String, primaryReading: String? = null): SearchResults {
        val trimmed = text.trim()
        val found = orElse(emptyList()) { lookup.lookupQuery(trimmed, LANGUAGE, primaryReading = primaryReading) }
        if (found.isNotEmpty()) return SearchResults(trimmed, found, noTermDictionary = null)
        val kanji = orElse(null) { lookup.characterEntry(trimmed, LANGUAGE) }
        val noTermDictionary = orElse(null) { lookup.noTermDictionary(LANGUAGE) }
        return SearchResults(trimmed, found, noTermDictionary = noTermDictionary, kanji = kanji)
    }

    suspend fun kanji(character: String): KanjiResult = orElse(KanjiResult(character)) { lookup.kanji(character, LANGUAGE) }

    suspend fun styles(): List<DictionaryStyle> = orElse(emptyList()) { lookup.styles(LANGUAGE) }

    suspend fun tagNotes(): List<DictionaryTagNotes> = orElse(emptyList()) { lookup.tagNotes() }

    /** [fallback] when [block] fails; a cancellation is passed on, so a replaced search does not show as empty. */
    private inline fun <T> orElse(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback
    }

    companion object {
        const val SEARCH_DELAY_MS = 150L
        val LANGUAGE = Language.JAPANESE
    }
}
