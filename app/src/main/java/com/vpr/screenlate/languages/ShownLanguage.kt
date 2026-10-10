package com.vpr.screenlate.languages

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The language whose settings a page shows: the active one until the user picks another on the page. A language
 * turned off meanwhile gives way to the active one again.
 */
class ShownLanguage(profiles: LanguageProfiles, scope: CoroutineScope) {
    private val chosen = MutableStateFlow<Language?>(null)

    val profiles: StateFlow<LanguageProfilesState> =
        profiles.state.stateIn(scope, SharingStarted.Eagerly, profiles.cachedState ?: LanguageProfilesState.DEFAULT)

    val language: StateFlow<Language> = combine(chosen, this.profiles, ::shown)
        .stateIn(scope, SharingStarted.Eagerly, shown(null, this.profiles.value))

    fun show(language: Language) {
        chosen.value = language
    }

    private fun shown(chosen: Language?, state: LanguageProfilesState): Language =
        chosen?.takeIf { it in state.turnedOn } ?: state.active
}
