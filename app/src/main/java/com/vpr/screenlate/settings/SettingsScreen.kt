package com.vpr.screenlate.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.home.HomeViewModel
import com.vpr.screenlate.ui.components.SettingsScaffold

/** Destinations of the settings list. */
enum class SettingsPage { BUBBLE, LOOKUP, DICTIONARIES, ANKI, APPEARANCE, YOMITAN_IMPORT, ABOUT }

/** One list of every settings section; each opens its own screen. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val dictionaries by viewModel.dictionaries.collectAsStateWithLifecycle()
    val anki by viewModel.anki.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.settings_title), onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SettingsEntry(R.drawable.ic_touch_app, stringResource(R.string.bubble_title), stringResource(R.string.settings_bubble_summary)) {
                onOpen(SettingsPage.BUBBLE)
            }
            SettingsEntry(R.drawable.ic_manage_search, stringResource(R.string.lookup_title), stringResource(R.string.settings_lookup_summary)) {
                onOpen(SettingsPage.LOOKUP)
            }
            SettingsEntry(
                R.drawable.ic_library_books,
                stringResource(R.string.home_dictionaries_title),
                if (dictionaries.importing) {
                    stringResource(R.string.home_dictionaries_installing)
                } else {
                    stringResource(R.string.home_dictionaries_summary, dictionaries.installed, dictionaries.enabled)
                },
            ) { onOpen(SettingsPage.DICTIONARIES) }
            SettingsEntry(
                R.drawable.ic_style,
                stringResource(R.string.anki_title),
                anki?.let { stringResource(R.string.home_anki_ready, it.deck, it.model) }
                    ?: stringResource(R.string.home_anki_not_configured),
            ) { onOpen(SettingsPage.ANKI) }
            SettingsEntry(R.drawable.ic_palette, stringResource(R.string.appearance_title), stringResource(R.string.settings_appearance_summary)) {
                onOpen(SettingsPage.APPEARANCE)
            }
            SettingsEntry(R.drawable.ic_upload_file, stringResource(R.string.yomitan_import_title), stringResource(R.string.settings_yomitan_summary)) {
                onOpen(SettingsPage.YOMITAN_IMPORT)
            }
            SettingsEntry(R.drawable.ic_info, stringResource(R.string.about_title), stringResource(R.string.settings_about_summary)) {
                onOpen(SettingsPage.ABOUT)
            }
        }
    }
}

@Composable
private fun SettingsEntry(@DrawableRes icon: Int, title: String, summary: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
