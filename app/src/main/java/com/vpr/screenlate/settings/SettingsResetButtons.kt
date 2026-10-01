package com.vpr.screenlate.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.components.ResetButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsResetViewModel @Inject constructor(private val settingsReset: SettingsReset) : ViewModel() {
    // Leaving the screen right after the tap must not stop a reset halfway.
    fun resetAll() {
        viewModelScope.launch { withContext(NonCancellable) { settingsReset.resetAll() } }
    }

    fun reset(section: SettingsSection) {
        viewModelScope.launch { withContext(NonCancellable) { settingsReset.reset(section) } }
    }
}

/** The Settings list's icon that resets every setting. */
@Composable
fun ResetAllButton(viewModel: SettingsResetViewModel = hiltViewModel()) {
    ResetButton(
        tooltip = stringResource(R.string.reset_all_tooltip),
        title = stringResource(R.string.reset_all_title),
        text = stringResource(R.string.reset_all_text),
        onReset = viewModel::resetAll,
    )
}

/** A section page's icon that resets its settings; [onReset] replaces the plain reset where the page keeps edits. */
@Composable
fun SectionResetButton(
    section: SettingsSection,
    onReset: (() -> Unit)? = null,
    viewModel: SettingsResetViewModel = hiltViewModel(),
) {
    ResetButton(
        tooltip = stringResource(R.string.reset_section_tooltip),
        title = stringResource(R.string.reset_section_title),
        text = stringResource(section.text),
        onReset = onReset ?: { viewModel.reset(section) },
    )
}

private val SettingsSection.text: Int
    @StringRes get() = when (this) {
        SettingsSection.BUBBLE -> R.string.reset_bubble_text
        SettingsSection.LOOKUP -> R.string.reset_lookup_text
        SettingsSection.ANKI -> R.string.reset_anki_text
        SettingsSection.POPUP -> R.string.reset_popup_text
        SettingsSection.APPEARANCE -> R.string.reset_appearance_text
        SettingsSection.BACKGROUND -> R.string.reset_background_text
    }
