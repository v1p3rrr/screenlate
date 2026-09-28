package com.vpr.screenlate.dictionaries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import java.util.Locale

/** Sets a dictionary's languages by hand; frequency and pitch dictionaries have only the language of their words. */
@Composable
fun DictionaryLanguagesDialog(
    dictionary: DictionaryEntity,
    onSave: (source: String?, target: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var source by remember(dictionary.id) { mutableStateOf(dictionary.sourceLanguage) }
    var target by remember(dictionary.id) { mutableStateOf(dictionary.targetLanguage) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dictionaries_languages_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(dictionary.title)
                LanguageField(stringResource(R.string.dictionaries_language_source), source) { source = it }
                if (dictionary.kind.hasTarget) {
                    LanguageField(stringResource(R.string.dictionaries_language_target), target) { target = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(source, target) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageField(label: String, value: String?, onChange: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(R.string.dictionaries_language_none)
    val options = listOf<String?>(null) + (LANGUAGES + listOfNotNull(value)).distinct()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value?.let(::languageLabel) ?: none,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option?.let(::languageLabel) ?: none) },
                    onClick = {
                        expanded = false
                        onChange(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** "English (en)" in the interface language. */
private fun languageLabel(code: String): String {
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return if (name.isEmpty() || name == code) code else "$name ($code)"
}

/** Languages of the catalog's dictionaries and of common Japanese dictionaries. */
private val LANGUAGES = listOf("ja", "en", "ru", "de", "fr", "es", "it", "pt", "nl", "sv", "hu", "sl", "zh", "ko")
