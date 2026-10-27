package com.vpr.screenlate.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.languages.LanguageCard
import com.vpr.screenlate.settings.SectionResetButton
import com.vpr.screenlate.settings.SettingsSection
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.formContent

/** Auto play, volume, and the shown language's audio sources and regions, with their test. */
@Composable
fun AudioSettingsScreen(onBack: () -> Unit, viewModel: AudioSettingsViewModel = hiltViewModel()) {
    val profiles by viewModel.shown.profiles.collectAsStateWithLifecycle()
    val shown by viewModel.shown.language.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    SettingsScaffold(
        stringResource(R.string.audio_title),
        onBack,
        actions = { SectionResetButton(SettingsSection.AUDIO, shown, profiles.several) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .formContent(padding, focusManager)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (profiles.several) {
                LanguageCard(profiles, shown, { language ->
                    focusManager.clearFocus()
                    viewModel.show(language)
                }, hint = stringResource(R.string.audio_languages_hint))
            }
            AudioSettingsCards(shown, viewModel)
        }
    }
}
