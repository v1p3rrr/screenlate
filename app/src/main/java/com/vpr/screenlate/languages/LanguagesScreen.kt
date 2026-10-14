package com.vpr.screenlate.languages

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import com.vpr.screenlate.dictionaries.DictionaryLanguagesDialog
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.overlay.install.InstallationStatus
import com.vpr.screenlate.overlay.ui.Flags
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.Segments
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.components.WithTooltip
import com.vpr.screenlate.ui.theme.AccentDefaults
import java.util.Locale

/**
 * Every language: the turned-on ones (active, quick-switch star, flag, turn off), those turned off that left files
 * (their dictionaries and models, delete, turn on) and the rest (turn on).
 */
@Composable
fun LanguagesScreen(
    onBack: () -> Unit,
    onOpenLanguageSetup: (Language) -> Unit,
    viewModel: LanguagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val turnOffRequest by viewModel.turnOffRequest.collectAsStateWithLifecycle()
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    var choosingFlag by remember { mutableStateOf<Language?>(null) }
    var deletingFiles by remember { mutableStateOf<LanguageLeftovers?>(null) }
    var deletingDictionary by remember { mutableStateOf<DictionaryEntity?>(null) }
    var editingLanguages by remember { mutableStateOf<DictionaryEntity?>(null) }
    val openLanguageSetup by rememberUpdatedState(onOpenLanguageSetup)
    LaunchedEffect(viewModel) { viewModel.languageSetup.collect { openLanguageSetup(it) } }
    val profiles = state.profiles

    SettingsScaffold(title = stringResource(R.string.home_languages_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint(stringResource(R.string.home_languages_hint))
            Hint(stringResource(R.string.languages_hint))
            SectionTitle(stringResource(R.string.languages_turned_on))
            profiles.turnedOn.forEach { language ->
                TurnedOnCard(
                    language = language,
                    profiles = profiles,
                    status = state.statuses[language],
                    onActivate = { viewModel.setActive(language) },
                    onStar = { viewModel.setStarred(language, it) },
                    onChooseFlag = { choosingFlag = language },
                    onTurnOff = { viewModel.requestTurnOff(language) },
                    script = state.scripts[language],
                    onScript = { viewModel.setScript(language, it) },
                    onFixedScript = { viewModel.setFixedScript(language, it) },
                )
            }

            val leftovers = state.leftovers.orEmpty()
            if (leftovers.isNotEmpty()) {
                SectionTitle(stringResource(R.string.languages_turned_off))
                Hint(stringResource(R.string.languages_turned_off_hint))
                leftovers.forEach { leftover ->
                    LeftoverCard(
                        leftover = leftover,
                        onTurnOn = leftover.language?.let { language -> { viewModel.turnOn(language) } },
                        onDeleteAll = { deletingFiles = leftover },
                        onDeleteDictionary = { deletingDictionary = it },
                        onEditLanguages = { editingLanguages = it },
                    )
                }
            }
            deleteError?.let { error ->
                Text(stringResource(R.string.dictionaries_delete_failed, error), color = MaterialTheme.colorScheme.error)
            }

            // Until the files are measured a language with files would show here first and then move up.
            val rest = if (state.leftovers == null) emptyList() else state.rest
            if (rest.isNotEmpty()) {
                SectionTitle(stringResource(R.string.languages_not_turned_on))
                Card(modifier = Modifier.fillMaxWidth()) {
                    rest.forEachIndexed { index, language ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            LanguageFlag(language, profiles.flagCountry(language), size = 32.dp)
                            Text(
                                language.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            )
                            TextButton(onClick = { viewModel.turnOn(language) }) {
                                Text(stringResource(R.string.languages_turn_on))
                            }
                        }
                    }
                }
            }
        }
    }

    choosingFlag?.let { language ->
        FlagDialog(
            language = language,
            chosen = profiles.flagCountry(language),
            onChoose = { country ->
                viewModel.setFlag(language, country)
                choosingFlag = null
            },
            onDismiss = { choosingFlag = null },
        )
    }
    turnOffRequest?.let { request ->
        TurnOffLanguageDialog(
            language = request.language,
            filesBytes = request.filesBytes,
            onTurnOff = { delete -> viewModel.turnOff(request.language, delete) },
            onDismiss = viewModel::cancelTurnOff,
        )
    }
    deletingFiles?.let { leftover ->
        val size = leftover.bytes?.let { Formatter.formatShortFileSize(LocalContext.current, it) } ?: "…"
        ConfirmDelete(
            title = leftoverName(leftover.code),
            text = stringResource(R.string.languages_delete_files_text, size),
            onDelete = { viewModel.deleteFiles(leftover.code) },
            onDismiss = { deletingFiles = null },
        )
    }
    deletingDictionary?.let { dictionary ->
        ConfirmDelete(
            title = stringResource(R.string.dictionaries_delete_title),
            text = stringResource(R.string.dictionaries_delete_message, dictionary.title),
            onDelete = { viewModel.deleteDictionary(dictionary) },
            onDismiss = { deletingDictionary = null },
        )
    }
    editingLanguages?.let { dictionary ->
        DictionaryLanguagesDialog(
            dictionary = dictionary,
            onSave = { source, target ->
                viewModel.setLanguages(dictionary, source, target)
                editingLanguages = null
            },
            onDismiss = { editingLanguages = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
}

/**
 * A turned-on language: active (radio), quick-switch star, its status, its flag's country, turning it off and, for a
 * language written in several scripts, the script; [script] holds it, null for a language with one.
 */
@Composable
private fun TurnedOnCard(
    language: Language,
    profiles: LanguageProfilesState,
    status: InstallationStatus?,
    onActivate: () -> Unit,
    onStar: (Boolean) -> Unit,
    onChooseFlag: () -> Unit,
    onTurnOff: () -> Unit,
    script: LookupSettings?,
    onScript: (String) -> Unit,
    onFixedScript: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val active = language == profiles.active
    val starred = language in profiles.starred
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = active, role = Role.RadioButton, onClick = onActivate)
                    .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            ) {
                RadioButton(selected = active, onClick = null, colors = AccentDefaults.radioButtonColors(), modifier = Modifier.padding(12.dp))
                LanguageFlag(language, profiles.flagCountry(language), size = 32.dp)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(language.displayName(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            stringResource(R.string.languages_active).takeIf { active },
                            status?.message(context, scanning = true),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StarButton(active = active, starred = starred, canStar = profiles.canStar, onStar = onStar)
        }
        val countries = language.support.flagCountries
        if (countries.size > 1 || profiles.several) {
            FlowRow(modifier = Modifier.padding(start = 56.dp, end = 8.dp, bottom = 4.dp)) {
                if (countries.size > 1) {
                    TextButton(onClick = onChooseFlag) {
                        Text(stringResource(R.string.languages_flag, Flags.countryName(profiles.flagCountry(language))))
                    }
                }
                if (profiles.several) {
                    TextButton(onClick = onTurnOff) { Text(stringResource(R.string.home_language_turn_off_confirm)) }
                }
            }
        }
        val tag = script?.script
        if (tag != null) {
            HorizontalDivider()
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Text(stringResource(R.string.lookup_script), style = MaterialTheme.typography.labelLarge)
                Segments(
                    options = language.support.scriptTags,
                    selected = tag,
                    label = { stringResource(scriptName(it)) },
                    onSelect = onScript,
                )
                Hint(stringResource(R.string.lookup_script_hint))
                SwitchRow(
                    stringResource(R.string.lookup_fixed_script),
                    script.fixedScript,
                    onFixedScript,
                    hint = stringResource(R.string.lookup_fixed_script_hint),
                )
            }
        }
    }
}

/**
 * The quick-switch star. The active language always has one, which cannot be taken away; another language gets one
 * while fewer than the most are given.
 */
@Composable
private fun StarButton(active: Boolean, starred: Boolean, canStar: Boolean, onStar: (Boolean) -> Unit) {
    val label = stringResource(if (active) R.string.languages_star_active else R.string.languages_star)
    WithTooltip(label) {
        IconToggleButton(
            checked = active || starred,
            onCheckedChange = onStar,
            enabled = !active && (starred || canStar),
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Icon(
                painterResource(if (active || starred) R.drawable.ic_star else R.drawable.ic_star_border),
                contentDescription = null,
                tint = if (active || starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A language that is off with the files it left: a tap shows them; delete them all or turn the language on. */
@Composable
private fun LeftoverCard(
    leftover: LanguageLeftovers,
    onTurnOn: (() -> Unit)?,
    onDeleteAll: () -> Unit,
    onDeleteDictionary: (DictionaryEntity) -> Unit,
    onEditLanguages: (DictionaryEntity) -> Unit,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(leftover.code) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(leftoverName(leftover.code), style = MaterialTheme.typography.titleMedium)
            Hint(
                listOfNotNull(
                    leftover.dictionaries.size.takeIf { it > 0 }?.let {
                        pluralStringResource(R.plurals.dictionaries_other_language_count, it, it)
                    },
                    leftover.models.size.takeIf { it > 0 }?.let {
                        pluralStringResource(R.plurals.languages_model_count, it, it)
                    },
                    leftover.bytes?.let { Formatter.formatShortFileSize(context, it) },
                ).joinToString(" · "),
            )
            if (leftover.language == null) Hint(stringResource(R.string.languages_not_supported))
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                leftover.dictionaries.forEach { dictionary ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(dictionary.title, style = MaterialTheme.typography.bodyLarge)
                            leftover.sizes[dictionary.id]?.let { Hint(Formatter.formatShortFileSize(context, it)) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Hint(
                                    "${stringResource(R.string.dictionaries_languages)}: " +
                                        languages(dictionary).ifEmpty { stringResource(R.string.dictionaries_language_none) },
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                TextButton(onClick = { onEditLanguages(dictionary) }) {
                                    Text(stringResource(R.string.dictionaries_languages_edit))
                                }
                            }
                        }
                        TooltipIconButton(R.drawable.ic_delete, stringResource(R.string.action_delete), onClick = { onDeleteDictionary(dictionary) })
                    }
                }
                leftover.models.forEach { model ->
                    HorizontalDivider()
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(model.title, style = MaterialTheme.typography.bodyLarge)
                        Hint(stringResource(R.string.languages_model_size, Formatter.formatShortFileSize(context, model.bytes)))
                    }
                }
            }
        }
        FlowRow(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)) {
            TextButton(onClick = onDeleteAll) { Text(stringResource(R.string.dictionaries_delete_language)) }
            if (onTurnOn != null) TextButton(onClick = onTurnOn) { Text(stringResource(R.string.languages_turn_on)) }
        }
    }
}

@Composable
private fun FlagDialog(language: Language, chosen: String, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.languages_flag_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                language.support.flagCountries.forEach { country ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = country == chosen, role = Role.RadioButton, onClick = { onChoose(country) })
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = country == chosen, onClick = null, colors = AccentDefaults.radioButtonColors())
                        LanguageFlag(language, country, size = 28.dp, modifier = Modifier.padding(horizontal = 8.dp))
                        Text(Flags.countryName(country), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ConfirmDelete(title: String, text: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismiss()
            }) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The name of the language with [code], also for one this version does not support. */
private fun leftoverName(code: String): String {
    val locale = Locale.getDefault()
    return Language.of(code)?.displayName()
        ?: Locale.forLanguageTag(code).getDisplayLanguage(locale).ifEmpty { code }.replaceFirstChar { it.titlecase(locale) }
}

private fun languages(dictionary: DictionaryEntity): String =
    listOfNotNull(dictionary.sourceLanguage, dictionary.targetLanguage).joinToString(" → ") { leftoverName(it) }
