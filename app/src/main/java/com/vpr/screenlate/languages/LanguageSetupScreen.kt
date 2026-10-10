package com.vpr.screenlate.languages

import android.net.ConnectivityManager
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionaries.rememberImportNotificationsAsk
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.ui.components.BackButton
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.theme.AccentDefaults
import java.util.Locale

@get:StringRes
val CatalogCategory.label: Int
    get() = when (this) {
        CatalogCategory.MAIN -> R.string.catalog_category_main
        CatalogCategory.FORMS -> R.string.catalog_category_forms
        CatalogCategory.FREQUENCY -> R.string.catalog_category_frequency
        CatalogCategory.PRONUNCIATION -> R.string.catalog_category_pronunciation
        CatalogCategory.GLOSSARY -> R.string.catalog_category_glossary
        CatalogCategory.CHARACTERS -> R.string.catalog_category_characters
        CatalogCategory.OCR_MODEL -> R.string.catalog_category_ocr_model
    }

/**
 * The dictionaries to download for a language being turned on: gloss languages, a card per category with the
 * recommended ones ticked, and the totals at the bottom. [onDone] runs once the language is on and the downloads are
 * queued.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSetupScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: LanguageSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var askMobileData by rememberSaveable { mutableStateOf(false) }
    val confirmation by viewModel.confirmed.collectAsStateWithLifecycle()
    // The screen closes once the downloads are queued and the notification question, if asked, is answered. A
    // recreated screen has lost the question's explanation dialog, so it does not wait for it.
    var asking by remember { mutableStateOf(false) }
    val askNotifications = rememberImportNotificationsAsk(onAnswered = { asking = false })
    val confirm = {
        asking = askNotifications()
        viewModel.confirm()
    }
    // The permission's answer arrives before the screen is resumed, and navigation waits for that.
    if (confirmation == LanguageSetupViewModel.Confirmation.QUEUED && !asking) {
        LifecycleResumeEffect(Unit) {
            onDone()
            onPauseOrDispose {}
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.language.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = {
            ConfirmBar(
                state = state,
                enabled = state.canConfirm && confirmation == LanguageSetupViewModel.Confirmation.NONE,
                onConfirm = {
                    val metered = context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true
                    if (metered && state.totals.downloadBytes > MOBILE_DATA_ASK_BYTES) askMobileData = true else confirm()
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hint(stringResource(R.string.language_setup_hint))
            GlossCard(state, viewModel::chooseGloss)
            state.categories.forEach { category ->
                CategoryCard(state, category, onTick = viewModel::tick)
            }
        }
    }
    if (askMobileData) {
        AlertDialog(
            onDismissRequest = { askMobileData = false },
            title = { Text(stringResource(R.string.language_setup_mobile_title)) },
            text = { Text(stringResource(R.string.language_setup_mobile_text, Formatter.formatShortFileSize(context, state.totals.downloadBytes))) },
            confirmButton = {
                TextButton(onClick = {
                    askMobileData = false
                    confirm()
                }) { Text(stringResource(R.string.dictionaries_download)) }
            },
            dismissButton = { TextButton(onClick = { askMobileData = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun GlossCard(state: LanguageSetupState, onChoose: (String?) -> Unit) {
    SectionCard(title = stringResource(R.string.language_setup_glosses)) {
        Hint(stringResource(R.string.language_setup_glosses_hint))
        state.glosses.forEachIndexed { index, code ->
            if (index == state.choosable) {
                GlossPicker(code, state.glossChoices, onChoose)
            } else if (code != null) {
                Text(languageName(code), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun GlossPicker(chosen: String?, choices: List<String>, onChoose: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                chosen?.let { languageName(it) } ?: stringResource(R.string.language_setup_gloss_add),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_setup_gloss_none)) },
                onClick = {
                    open = false
                    onChoose(null)
                },
            )
            choices.forEach { code ->
                DropdownMenuItem(
                    text = { Text(languageName(code)) },
                    onClick = {
                        open = false
                        onChoose(code)
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(state: LanguageSetupState, category: SetupCategory, onTick: (SetupItem, Boolean) -> Unit) {
    val title = stringResource(category.category.label)
    val sample = state.language.support.pronunciationSample
    SectionCard(
        title = title,
        info = if (category.category == CatalogCategory.PRONUNCIATION && sample != null) {
            stringResource(R.string.language_setup_pronunciation_info, sample)
        } else {
            null
        },
    ) {
        category.items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            SetupItemRow(item, ticked = category.ticked(item, state.choices), radio = category.fixed, onTick = { onTick(item, it) })
        }
        when {
            category.items.isEmpty() -> Text(
                stringResource(R.string.language_setup_none_available),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            category.category in state.missing -> Text(
                stringResource(R.string.language_setup_choose_one),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SetupItemRow(item: SetupItem, ticked: Boolean, radio: Boolean, onTick: (Boolean) -> Unit) {
    val entry = item.entry
    val changeable = !item.installed && !radio
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = ticked, enabled = changeable, role = if (radio) Role.RadioButton else Role.Checkbox, onValueChange = onTick),
    ) {
        if (radio) {
            RadioButton(selected = true, onClick = null, modifier = Modifier.padding(12.dp))
        } else {
            Checkbox(checked = ticked, onCheckedChange = null, enabled = changeable, modifier = Modifier.padding(12.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.displayTitle(), style = MaterialTheme.typography.bodyLarge)
            val size = if (item.installed) {
                stringResource(R.string.dictionaries_installed_mark)
            } else {
                Formatter.formatShortFileSize(LocalContext.current, entry.downloadBytes)
            }
            Text(
                listOf(size, entry.license).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.description().takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** The confirm button with the download total, the space after import, and why it is disabled. */
@Composable
private fun ConfirmBar(state: LanguageSetupState, enabled: Boolean, onConfirm: () -> Unit) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(onClick = onConfirm, enabled = enabled, modifier = Modifier.fillMaxWidth(), colors = AccentDefaults.buttonColors()) {
                Text(
                    if (state.downloads.isEmpty()) {
                        stringResource(R.string.language_setup_turn_on)
                    } else {
                        stringResource(R.string.language_setup_download, size(state.totals.downloadBytes))
                    },
                    textAlign = TextAlign.Center,
                )
            }
            when {
                state.missing.isNotEmpty() -> Hint(
                    stringResource(
                        R.string.language_setup_missing,
                        state.missing.map { stringResource(it.label) }.joinToString(", "),
                    ),
                )
                !state.enoughSpace -> Text(
                    stringResource(R.string.language_setup_no_space, size(state.totals.neededBytes), size(state.freeBytes)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                state.downloads.isNotEmpty() -> Hint(stringResource(R.string.language_setup_after_import, size(state.totals.installedBytes)))
            }
        }
    }
}

/** A gloss language's name in the interface language, capitalized as a list item. */
private fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
