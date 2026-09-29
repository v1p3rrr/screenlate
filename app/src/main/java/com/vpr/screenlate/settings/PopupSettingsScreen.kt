package com.vpr.screenlate.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.overlay.settings.DefinitionCopyMode
import com.vpr.screenlate.overlay.settings.PopupAppearance
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.Segments
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.SwitchRow

/** The lookup page (popup and search): its font, text size and weight, custom CSS, and definition copying. */
@Composable
fun PopupSettingsScreen(onBack: () -> Unit, viewModel: PopupAppearanceViewModel = hiltViewModel()) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.popup_settings_title), onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PopupTextSections(viewModel)
            appearance?.let { DefinitionCopyCard(it, viewModel) }
        }
    }
}

@Composable
private fun DefinitionCopyCard(appearance: PopupAppearance, viewModel: PopupAppearanceViewModel) {
    SectionCard(title = stringResource(R.string.popup_copy_title)) {
        SwitchRow(
            label = stringResource(R.string.popup_copy_switch),
            checked = appearance.copyDefinitions,
            onChange = viewModel::setCopyDefinitions,
            hint = stringResource(R.string.popup_copy_switch_hint),
        )
        Segments(
            options = DefinitionCopyMode.entries,
            selected = appearance.copyMode,
            label = {
                stringResource(
                    when (it) {
                        DefinitionCopyMode.ALL -> R.string.popup_copy_mode_all
                        DefinitionCopyMode.MEANINGS -> R.string.popup_copy_mode_meanings
                    },
                )
            },
            onSelect = viewModel::setCopyMode,
            enabled = appearance.copyDefinitions,
        )
        Hint(
            stringResource(
                when (appearance.copyMode) {
                    DefinitionCopyMode.ALL -> R.string.popup_copy_mode_all_hint
                    DefinitionCopyMode.MEANINGS -> R.string.popup_copy_mode_meanings_hint
                },
            ),
        )
    }
}
