package com.vpr.screenlate.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.background.BackgroundTipViewModel
import com.vpr.screenlate.background.rememberBackgroundTipBadge
import com.vpr.screenlate.home.HomeViewModel
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.theme.AccentDefaults

/** Destinations of the settings list. */
enum class SettingsPage {
    BUBBLE, RECOGNITION, LOOKUP, DICTIONARIES, ANKI, AUDIO, POPUP, TRANSLATION, CAMERA, APPEARANCE, BACKGROUND, BACKUP, ABOUT,
}

/**
 * One list of every settings section; each opens its own screen. Leaving the list for another tab while Background
 * work is badged counts as having seen the tip (the navigation host marks it).
 */
@Composable
fun SettingsScreen(
    onOpen: (SettingsPage) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    tip: BackgroundTipViewModel = hiltViewModel(),
) {
    val dictionaries by viewModel.dictionaries.collectAsStateWithLifecycle()
    val anki by viewModel.anki.collectAsStateWithLifecycle()
    val backgroundBadge = rememberBackgroundTipBadge(tip)
    val offlineMode by settingsViewModel.offlineMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    val searchResults = remember(searchQuery, context) {
        if (searchQuery.isBlank()) SettingsIndex.items else SettingsIndex.search(searchQuery, context)
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = null,
        navigationIcon = if (searchActive) {
            {
                TooltipIconButton(
                    icon = R.drawable.ic_arrow_back,
                    tooltip = stringResource(R.string.search_back),
                    onClick = {
                        searchActive = false
                        searchQuery = ""
                    },
                )
            }
        } else {
            null
        },
        titleContent = if (searchActive) {
            {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                stringResource(R.string.search_settings),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        innerTextField()
                    },
                )
            }
        } else {
            null
        },
        actions = {
            if (searchActive) {
                IconButton(onClick = {
                    if (searchQuery.isNotEmpty()) {
                        searchQuery = ""
                    } else {
                        searchActive = false
                    }
                }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            } else {
                IconButton(onClick = { searchActive = true }) {
                    Icon(
                        painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.search_settings),
                    )
                }
                ResetAllButton()
            }
        },
    ) { padding ->
        if (searchActive) {
            if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_search),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Text(
                            stringResource(R.string.search_settings_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.search_settings_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(vertical = 8.dp),
                ) {
                    items(searchResults) { item ->
                        SearchResultEntry(item) {
                            searchActive = false
                            searchQuery = ""
                            onOpen(item.page)
                        }
                    }
                }
            }
        } else {
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
                SettingsEntry(R.drawable.ic_document_scanner, stringResource(R.string.recognition_title), stringResource(R.string.settings_recognition_summary)) {
                    onOpen(SettingsPage.RECOGNITION)
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
                SettingsEntry(R.drawable.ic_volume_up, stringResource(R.string.audio_title), stringResource(R.string.settings_audio_summary)) {
                    onOpen(SettingsPage.AUDIO)
                }
                SettingsEntry(R.drawable.ic_web_asset, stringResource(R.string.popup_settings_title), stringResource(R.string.settings_popup_summary)) {
                    onOpen(SettingsPage.POPUP)
                }
                SettingsEntry(R.drawable.ic_translate, stringResource(R.string.translation_title), stringResource(R.string.settings_translation_summary)) {
                    onOpen(SettingsPage.TRANSLATION)
                }
                SettingsEntry(R.drawable.ic_photo_camera, stringResource(R.string.camera_title), stringResource(R.string.settings_camera_summary)) {
                    onOpen(SettingsPage.CAMERA)
                }
                SettingsEntry(R.drawable.ic_palette, stringResource(R.string.appearance_title), stringResource(R.string.settings_appearance_summary)) {
                    onOpen(SettingsPage.APPEARANCE)
                }
                SettingsEntry(
                    R.drawable.ic_battery,
                    stringResource(R.string.background_title),
                    stringResource(R.string.background_summary),
                    badge = backgroundBadge,
                ) { onOpen(SettingsPage.BACKGROUND) }
                SettingsEntry(R.drawable.ic_backup, stringResource(R.string.backup_title), stringResource(R.string.settings_backup_summary)) {
                    onOpen(SettingsPage.BACKUP)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsSwitchEntry(
                    icon = R.drawable.ic_cloud_off,
                    title = stringResource(R.string.settings_offline_mode),
                    summary = stringResource(R.string.settings_offline_mode_hint),
                    checked = offlineMode,
                    onCheckedChange = { settingsViewModel.setOfflineMode(it) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsEntry(R.drawable.ic_info, stringResource(R.string.about_title), stringResource(R.string.settings_about_summary)) {
                    onOpen(SettingsPage.ABOUT)
                }
            }
        }
    }
}

@Composable
private fun SearchResultEntry(
    item: SettingsIndexItem,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            painterResource(item.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
        ) {
            Text(
                stringResource(item.sectionRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(item.titleRes), style = MaterialTheme.typography.titleMedium)
            item.summaryRes?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsEntry(
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    badge: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BadgedBox(badge = { if (badge) Badge() }) {
            Icon(
                painterResource(icon),
                contentDescription = if (badge) stringResource(R.string.settings_badge) else null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
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

@Composable
private fun SettingsSwitchEntry(
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = AccentDefaults.switchColors(),
        )
    }
}
