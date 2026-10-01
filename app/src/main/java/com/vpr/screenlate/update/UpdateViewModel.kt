package com.vpr.screenlate.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Update state for the home screen's announcement and the About screen. */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: AppUpdates,
    private val settingsRepository: UpdateSettingsRepository,
) : ViewModel() {
    val supported: Boolean get() = updates.supported
    val state: StateFlow<UpdateState> = updates.state
    val settings: StateFlow<UpdateSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.cachedSettings)

    /** The release announced in this session; it stays until dismissed, but is not announced again later. */
    private val announcedHere = MutableStateFlow<Release?>(null)
    val announcement: StateFlow<Release?> = announcedHere

    init {
        viewModelScope.launch {
            combine(updates.state, settingsRepository.settings) { state, settings -> state to settings }.collect { (state, settings) ->
                val release = (state as? UpdateState.Available)?.release ?: return@collect
                if (settings.announce && settings.announcedTag != release.tag && announcedHere.value == null) {
                    announcedHere.value = release
                    settingsRepository.setAnnounced(release.tag)
                }
            }
        }
    }

    /** The daily check when the home screen opens. */
    fun checkIfDue() {
        viewModelScope.launch { updates.checkIfDue() }
    }

    fun check() {
        viewModelScope.launch { updates.check() }
    }

    fun update(release: Release) = updates.update(release)

    fun confirm(prompt: Intent) = updates.confirm(prompt)

    fun canInstall(): Boolean = updates.canInstall()

    fun dismissAnnouncement() {
        announcedHere.value = null
    }

    fun setAnnounce(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAnnounce(enabled) }
    }
}
