package com.vpr.screenlate.yomitan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsImportState {
    data object Idle : SettingsImportState

    data object Reading : SettingsImportState

    data object NotSettings : SettingsImportState

    data class Loaded(
        val settings: YomitanSettings,
        val profile: Int,
        val sections: Set<YomitanSection> = YomitanSection.entries.toSet(),
        val applying: Boolean = false,
    ) : SettingsImportState

    data class Done(val profile: String, val summary: ImportSummary) : SettingsImportState
}

@HiltViewModel
class YomitanImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: YomitanSettingsImporter,
) : ViewModel() {
    private val mutableState = MutableStateFlow<SettingsImportState>(SettingsImportState.Idle)
    val state: StateFlow<SettingsImportState> = mutableState

    fun load(uri: Uri) {
        mutableState.value = SettingsImportState.Reading
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    YomitanSettings.parse(text.orEmpty())
                }.getOrNull()
            }
            mutableState.value = settings?.let { SettingsImportState.Loaded(it, it.currentProfile) }
                ?: SettingsImportState.NotSettings
        }
    }

    fun selectProfile(index: Int) = updateLoaded { it.copy(profile = index) }

    fun toggleSection(section: YomitanSection) = updateLoaded {
        it.copy(sections = if (section in it.sections) it.sections - section else it.sections + section)
    }

    fun apply() {
        val loaded = mutableState.value as? SettingsImportState.Loaded ?: return
        if (loaded.applying || loaded.sections.isEmpty()) return
        mutableState.value = loaded.copy(applying = true)
        val profile = loaded.settings.profiles[loaded.profile]
        viewModelScope.launch {
            val summary = importer.apply(profile, loaded.sections)
            mutableState.value = SettingsImportState.Done(profile.name, summary)
        }
    }

    fun reset() {
        mutableState.value = SettingsImportState.Idle
    }

    private fun updateLoaded(transform: (SettingsImportState.Loaded) -> SettingsImportState.Loaded) {
        (mutableState.value as? SettingsImportState.Loaded)?.let { mutableState.value = transform(it) }
    }
}
