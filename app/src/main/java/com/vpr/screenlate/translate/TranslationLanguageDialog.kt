package com.vpr.screenlate.translate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.translate.TranslationLanguage
import com.vpr.screenlate.core.translate.TranslationLanguages
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.theme.AccentDefaults
import java.text.Collator
import java.util.Locale

/** A language to pick, with its name in the interface language and what a search matches. */
private class LanguageItem(val language: TranslationLanguage, val name: String, private val searchText: String) {
    fun matches(query: String): Boolean = searchText.contains(query, ignoreCase = true)
}

/**
 * Picks the language translations go into: the interface language, then the starred languages, then all of them.
 * A search looks through the names in the interface language, in the language itself and in English, and the tags.
 * The dialog keeps its full height, so it does not jump while the list shrinks.
 *
 * @param selected the chosen tag; null for the interface language.
 */
@Composable
fun TranslationLanguageDialog(
    selected: String?,
    favorites: Set<String>,
    interfaceLanguage: TranslationLanguage,
    onSelect: (String?) -> Unit,
    onFavorite: (tag: String, favorite: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var query by rememberSaveable { mutableStateOf("") }
    val items = remember(locale) { languageItems(locale) }
    val trimmed = query.trim()
    val matches = if (trimmed.isEmpty()) items else items.filter { it.matches(trimmed) }
    val starred = items.filter { it.language.tag in favorites }
    val listState = rememberLazyListState()
    LaunchedEffect(trimmed) { listState.scrollToItem(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translation_language_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxHeight()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.translation_language_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    if (trimmed.isEmpty()) {
                        item(key = "interface") {
                            LanguageRow(
                                name = stringResource(R.string.translation_language_interface, interfaceLanguage.displayName(locale)),
                                note = supportNote(interfaceLanguage),
                                selected = selected == null,
                                onClick = { onSelect(null) },
                            )
                        }
                        if (starred.isNotEmpty()) {
                            item(key = "favorites") { Heading(stringResource(R.string.translation_language_favorites)) }
                            items(starred, key = { "favorite:" + it.language.tag }) { item ->
                                LanguageItemRow(item, selected, favorite = true, onSelect, onFavorite)
                            }
                        }
                        item(key = "all") { Heading(stringResource(R.string.translation_language_all)) }
                    }
                    items(matches, key = { it.language.tag }) { item ->
                        LanguageItemRow(item, selected, favorite = item.language.tag in favorites, onSelect, onFavorite)
                    }
                    if (matches.isEmpty()) {
                        item(key = "none") {
                            Hint(stringResource(R.string.translation_language_none_found), Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Every language, named in [locale] and sorted by that name. */
private fun languageItems(locale: Locale): List<LanguageItem> {
    val collator = Collator.getInstance(locale)
    return TranslationLanguages.all
        .map { language ->
            val name = language.displayName(locale)
            val own = Locale.forLanguageTag(language.tag).let { it.getDisplayName(it) }
            LanguageItem(language, name, listOf(name, own, language.englishName, language.tag).joinToString("\n"))
        }
        .sortedWith { a, b -> collator.compare(a.name, b.name) }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun LanguageItemRow(
    item: LanguageItem,
    selected: String?,
    favorite: Boolean,
    onSelect: (String?) -> Unit,
    onFavorite: (String, Boolean) -> Unit,
) {
    val tag = item.language.tag
    LanguageRow(
        name = item.name,
        note = supportNote(item.language),
        selected = selected.equals(tag, ignoreCase = true),
        onClick = { onSelect(tag) },
    ) {
        TooltipIconButton(
            if (favorite) R.drawable.ic_star else R.drawable.ic_star_border,
            stringResource(if (favorite) R.string.translation_language_favorite_remove else R.string.translation_language_favorite_add),
            onClick = { onFavorite(tag, !favorite) },
        )
    }
}

@Composable
private fun LanguageRow(
    name: String,
    note: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null, colors = AccentDefaults.radioButtonColors(), modifier = Modifier.padding(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            note?.let { Hint(stringResource(it)) }
        }
        trailing()
    }
}
