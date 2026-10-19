package com.vpr.screenlate.languages

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import com.vpr.screenlate.core.ocr.model.OcrModelStore
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import com.vpr.screenlate.overlay.install.InstallationStatus
import com.vpr.screenlate.overlay.install.LanguageInstallation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the Languages screen lists: the turned-on languages with their installation status and, for a language written
 * in several scripts, the settings that hold its script ([scripts]); the languages that are off but left files
 * ([leftovers], null while they are measured) and the [rest].
 */
data class LanguagesState(
    val profiles: LanguageProfilesState,
    val statuses: Map<Language, InstallationStatus> = emptyMap(),
    val leftovers: List<LanguageLeftovers>? = null,
    val scripts: Map<Language, LookupSettings> = emptyMap(),
) {
    /** Supported languages that are off and have no files. */
    val rest: List<Language>
        get() = Language.entries.filter { language ->
            language !in profiles.turnedOn && leftovers.orEmpty().none { language in it.languages }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LanguagesViewModel @Inject constructor(
    private val profiles: LanguageProfiles,
    private val switch: LanguageSwitch,
    private val files: LanguageFiles,
    private val installs: LanguageInstallation,
    private val repository: DictionaryRepository,
    private val bundled: BundledDictionaries,
    private val lookupSettings: LookupSettingsRepository,
    models: OcrModelStore,
) : ViewModel() {
    private val statuses = profiles.state.map { it.turnedOn }.distinctUntilChanged().flatMapLatest { turnedOn ->
        if (turnedOn.isEmpty()) flowOf(emptyMap())
        else combine(turnedOn.map(installs::observe)) { list -> list.associateBy { it.language } }
    }

    private val leftovers = combine(
        repository.dictionaries,
        profiles.state.map { it.turnedOn }.distinctUntilChanged(),
        models.models,
        ::Triple,
    ).mapLatest { (_, turnedOn, _) ->
        try {
            files.leftovers(turnedOn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cannot list the files of turned-off languages: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    private val scripts = profiles.state.map { it.turnedOn }.distinctUntilChanged().flatMapLatest { turnedOn ->
        val written = turnedOn.filter { it.support.scriptTags.isNotEmpty() }
        if (written.isEmpty()) flowOf(emptyMap())
        else combine(written.map { language -> lookupSettings.settings(language).map { language to it } }) { it.toMap() }
    }

    val state: StateFlow<LanguagesState> =
        combine(profiles.state, statuses, leftovers, scripts) { state, statuses, leftovers, scripts ->
            LanguagesState(state, statuses, leftovers, scripts)
        }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LanguagesState(profiles.cachedState ?: LanguageProfilesState.DEFAULT),
    )

    private val setupRequests = Channel<Language>(Channel.BUFFERED)

    /** Languages whose download screen should open, each delivered once (see `HomeViewModel.languageSetup`). */
    val languageSetup: Flow<Language> = setupRequests.receiveAsFlow()

    /** Turns [language] on and makes it active at once when its dictionaries are installed; otherwise asks for its download screen. */
    fun turnOn(language: Language) {
        viewModelScope.launch {
            if (switch.needsDownloads(language)) setupRequests.send(language) else switch.turnOn(language)
        }
    }

    fun setActive(language: Language) {
        viewModelScope.launch { profiles.setActive(language) }
    }

    fun setStarred(language: Language, starred: Boolean) {
        viewModelScope.launch { profiles.setStarred(language, starred) }
    }

    fun setFlag(language: Language, country: String) {
        viewModelScope.launch { profiles.setFlag(language, country) }
    }

    /** [tag] is one of [language]'s script tags. */
    fun setScript(language: Language, tag: String) {
        viewModelScope.launch { lookupSettings.setScript(language, tag) }
    }

    fun setFixedScript(language: Language, enabled: Boolean) {
        viewModelScope.launch { lookupSettings.setFixedScript(language, enabled) }
    }

    private val mutableTurnOff = MutableStateFlow<TurnOffRequest?>(null)

    /** The language whose turn-off dialog is open, once its files are measured. */
    val turnOffRequest: StateFlow<TurnOffRequest?> = mutableTurnOff

    fun requestTurnOff(language: Language) {
        viewModelScope.launch { mutableTurnOff.value = TurnOffRequest(language, switch.filesSize(language)) }
    }

    fun cancelTurnOff() {
        mutableTurnOff.value = null
    }

    fun turnOff(language: Language, deleteFiles: Boolean) {
        mutableTurnOff.value = null
        viewModelScope.launch { switch.turnOff(language, deleteFiles) }
    }

    private val mutableDeleteError = MutableStateFlow<String?>(null)

    /** Why the last delete failed; cleared by the next one. */
    val deleteError: StateFlow<String?> = mutableDeleteError

    /** Deletes every file of a language that is off; leaving the screen does not stop it. */
    fun deleteFiles(code: String) = delete("Deleting the files of a language failed") { files.delete(code) }

    fun deleteDictionary(dictionary: DictionaryEntity) = delete("Deleting a dictionary failed") {
        if (dictionary.bundled) bundled.markDeleted(dictionary.title)
        repository.delete(dictionary.id)
    }

    fun setLanguages(dictionary: DictionaryEntity, source: String?, target: String?) {
        viewModelScope.launch { repository.setLanguages(dictionary.id, source, target) }
    }

    private fun delete(failure: String, action: suspend () -> Unit) {
        mutableDeleteError.value = null
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // E.g. a full storage, where the settings or the registry cannot be written.
                    Log.w(TAG, failure, e)
                    mutableDeleteError.value = e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    private companion object {
        const val TAG = "Languages"
    }
}
