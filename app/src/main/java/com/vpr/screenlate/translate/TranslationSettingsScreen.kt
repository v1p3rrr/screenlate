package com.vpr.screenlate.translate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.translate.ServiceChoice
import com.vpr.screenlate.core.translate.TranslationLanguage
import com.vpr.screenlate.core.translate.TranslationLanguages
import com.vpr.screenlate.core.translate.TranslationService
import com.vpr.screenlate.core.translate.TranslationSettings
import com.vpr.screenlate.overlay.translate.SentenceTranslation
import com.vpr.screenlate.settings.SectionResetButton
import com.vpr.screenlate.settings.SettingsSection
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.InfoButton
import com.vpr.screenlate.ui.components.ReorderableColumn
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.components.formContent
import com.vpr.screenlate.ui.theme.AccentDefaults

/** Sentence translation: where it shows, the services in their order, the language, and a test of the services. */
@Composable
fun TranslationSettingsScreen(onBack: () -> Unit, viewModel: TranslationSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    SettingsScaffold(
        stringResource(R.string.translation_title),
        onBack,
        actions = { SectionResetButton(SettingsSection.TRANSLATION, onReset = viewModel::resetSettings) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .formContent(padding, focusManager)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val current = settings ?: return@Column
            SentenceCard(current, viewModel)
            ServicesCard(current, viewModel)
            LanguageCard(current, viewModel)
            TestCard(current, viewModel)
        }
    }
}

@Composable
private fun SentenceCard(settings: TranslationSettings, viewModel: TranslationSettingsViewModel) {
    val title = stringResource(R.string.translation_sentence_title)
    SectionCard(title = title) {
        Row {
            Hint(stringResource(R.string.translation_hint), Modifier.weight(1f))
            InfoButton(title, stringResource(R.string.translation_info))
        }
        SwitchRow(
            label = stringResource(R.string.translation_button),
            checked = settings.button,
            onChange = viewModel::setButton,
            hint = stringResource(R.string.translation_button_hint),
        )
        SwitchRow(
            label = stringResource(R.string.translation_anki),
            checked = settings.ankiField,
            onChange = viewModel::setAnkiField,
            hint = stringResource(R.string.translation_anki_hint),
        )
    }
}

@Composable
private fun ServicesCard(settings: TranslationSettings, viewModel: TranslationSettingsViewModel) {
    SectionCard(title = stringResource(R.string.translation_services_title)) {
        Hint(stringResource(R.string.translation_services_hint))
        ReorderableColumn(
            items = settings.services,
            key = { it.service },
            onReorder = viewModel::setServices,
        ) { choice, handle, dragging ->
            ServiceRow(choice, handle, dragging) { viewModel.setServiceEnabled(choice.service, it) }
        }
    }
}

// `handle` goes on the drag handle, not on the row, so it is not the conventional `modifier` parameter.
@Suppress("ModifierParameter")
@Composable
private fun ServiceRow(choice: ServiceChoice, handle: Modifier, dragging: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Surface(
        color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = if (dragging) 4.dp else 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painterResource(R.drawable.ic_drag_handle),
                contentDescription = stringResource(R.string.dictionaries_reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = handle.padding(12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(choice.service.label, style = MaterialTheme.typography.bodyLarge)
                if (choice.service == TranslationService.BING) Hint(stringResource(R.string.translation_service_bing_note))
            }
            Switch(checked = choice.enabled, onCheckedChange = onEnabledChange, colors = AccentDefaults.switchColors())
        }
    }
}

@Composable
private fun LanguageCard(settings: TranslationSettings, viewModel: TranslationSettingsViewModel) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val interfaceLanguage = viewModel.interfaceLanguage()
    val chosen = settings.language?.let(TranslationLanguages::of)
    SectionCard(title = stringResource(R.string.translation_language_title)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { picking = true }
                .padding(vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chosen?.displayName(locale)
                        ?: stringResource(R.string.translation_language_interface, interfaceLanguage.displayName(locale)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                supportNote(chosen ?: interfaceLanguage)?.let { Hint(stringResource(it)) }
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (picking) {
        TranslationLanguageDialog(
            selected = settings.language,
            favorites = settings.favorites,
            interfaceLanguage = interfaceLanguage,
            onSelect = {
                viewModel.setLanguage(it)
                picking = false
            },
            onFavorite = viewModel::setFavorite,
            onDismiss = { picking = false },
        )
    }
}

/** Which services lack [language], when some do. */
internal fun supportNote(language: TranslationLanguage): Int? = when {
    language.microsoft == null -> R.string.translation_language_google_only
    language.google == null -> R.string.translation_language_microsoft_only
    else -> null
}

@Composable
private fun TestCard(settings: TranslationSettings, viewModel: TranslationSettingsViewModel) {
    val rows by viewModel.test.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    var text by rememberSaveable(language) { mutableStateOf(language.support.translationSample) }
    val none = settings.enabledServices.isEmpty()
    SectionCard(title = stringResource(R.string.translation_test_title)) {
        Hint(stringResource(R.string.translation_test_hint))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.translation_test_text)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (none) Hint(stringResource(R.string.translation_test_none))
        OutlinedButton(
            onClick = {
                focusManager.clearFocus()
                viewModel.runTest(text)
            },
            enabled = !none && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.translation_test_run)) }
        rows.forEach { TestResult(it) }
    }
}

@Composable
private fun TestResult(row: TestRow) {
    val result = row.result
    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (result == null) {
            Text(row.service.label, style = MaterialTheme.typography.labelLarge)
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentDefaults.progress)
            return@Column
        }
        Text(
            stringResource(R.string.translation_test_time, row.service.label, result.timeMs),
            style = MaterialTheme.typography.labelLarge,
        )
        val translated = result.text
        val error = result.error
        when {
            translated != null -> SelectionContainer { Text(translated, style = MaterialTheme.typography.bodyMedium) }
            error != null -> Text(
                SentenceTranslation.errorText(LocalContext.current, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
