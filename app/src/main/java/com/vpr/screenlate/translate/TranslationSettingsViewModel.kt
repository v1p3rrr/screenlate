package com.vpr.screenlate.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.translate.SentenceTranslator
import com.vpr.screenlate.core.translate.ServiceChoice
import com.vpr.screenlate.core.translate.ServiceTest
import com.vpr.screenlate.core.translate.TranslationLanguage
import com.vpr.screenlate.core.translate.TranslationService
import com.vpr.screenlate.core.translate.TranslationSettings
import com.vpr.screenlate.core.translate.TranslationSettingsRepository
import com.vpr.screenlate.settings.SettingsReset
import com.vpr.screenlate.settings.SettingsSection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A service's line in the test; [result] is null while the service is still asked. */
data class TestRow(val service: TranslationService, val result: ServiceTest? = null)

/** The Translation page: the 文A and note switches, the services, the language, and the test. */
@HiltViewModel
class TranslationSettingsViewModel @Inject constructor(
    private val repository: TranslationSettingsRepository,
    private val translator: SentenceTranslator,
    private val settingsReset: SettingsReset,
) : ViewModel() {
    val settings: StateFlow<TranslationSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, repository.cachedSettings)

    private val mutableTest = MutableStateFlow<List<TestRow>>(emptyList())

    /** The last test, a row per service that was on, in the services' order. */
    val test: StateFlow<List<TestRow>> = mutableTest

    private var testJob: Job? = null

    /** The language "the interface language" stands for; read each time, as the app's language may change. */
    fun interfaceLanguage(): TranslationLanguage = translator.interfaceTarget(LANGUAGE)

    fun setButton(on: Boolean) = launch { repository.setButton(on) }

    fun setAnkiField(on: Boolean) = launch { repository.setAnkiField(on) }

    fun setServices(services: List<ServiceChoice>) = launch { repository.setServices(services) }

    fun setServiceEnabled(service: TranslationService, enabled: Boolean) =
        launch { repository.setServiceEnabled(service, enabled) }

    /** Null follows the interface language. */
    fun setLanguage(tag: String?) = launch { repository.setLanguage(tag) }

    fun setFavorite(tag: String, favorite: Boolean) = launch { repository.setFavorite(tag, favorite) }

    /** Asks every service that is on at once; each row fills in when its answer comes. */
    fun runTest(text: String) {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            val services = repository.current().enabledServices
            mutableTest.value = services.map { TestRow(it) }
            services.forEach { service ->
                launch {
                    val result = translator.test(service, text, LANGUAGE)
                    mutableTest.update { rows -> rows.map { if (it.service == service) it.copy(result = result) else it } }
                }
            }
        }
    }

    fun resetSettings() {
        testJob?.cancel()
        mutableTest.value = emptyList()
        viewModelScope.launch { withContext(NonCancellable) { settingsReset.reset(SettingsSection.TRANSLATION) } }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        /** The language translated from; the only one the app reads so far. */
        val LANGUAGE = Language.JAPANESE
    }
}
