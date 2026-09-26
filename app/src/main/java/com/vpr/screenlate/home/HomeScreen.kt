package com.vpr.screenlate.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.overlay.OverlayServiceStatus
import com.vpr.screenlate.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenOcrTest: () -> Unit,
    onOpenDictionaries: () -> Unit,
    onOpenAnki: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenBubble: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val dictionaries by viewModel.dictionaries.collectAsStateWithLifecycle()
    val anki by viewModel.anki.collectAsStateWithLifecycle()
    var serviceEnabled by remember { mutableStateOf(OverlayServiceStatus.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = OverlayServiceStatus.isEnabled(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.home_search_title)) {
                Button(onClick = onOpenSearch, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_search_open))
                }
            }

            SectionCard(title = stringResource(R.string.onboarding_service_title)) {
                Text(
                    text = stringResource(
                        if (serviceEnabled) R.string.onboarding_service_enabled else R.string.onboarding_service_disabled,
                    ),
                    color = if (serviceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.onboarding_service_explanation))
                Button(
                    onClick = { context.startActivity(OverlayServiceStatus.accessibilitySettingsIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_open_accessibility_settings))
                }
            }

            SectionCard(title = stringResource(R.string.home_dictionaries_title)) {
                Text(
                    if (dictionaries.importing) {
                        stringResource(R.string.home_dictionaries_installing)
                    } else {
                        stringResource(R.string.home_dictionaries_summary, dictionaries.installed, dictionaries.enabled)
                    },
                )
                Button(onClick = onOpenDictionaries, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_dictionaries_open))
                }
            }

            SectionCard(title = stringResource(R.string.home_anki_title)) {
                Text(
                    anki?.let { stringResource(R.string.home_anki_ready, it.deck, it.model) }
                        ?: stringResource(R.string.home_anki_not_configured),
                )
                OutlinedButton(onClick = onOpenAnki, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_anki_open))
                }
            }

            SectionCard(title = stringResource(R.string.onboarding_device_title)) {
                Text(stringResource(R.string.onboarding_device_app_launch))
                Text(stringResource(R.string.onboarding_device_restricted_settings))
            }

            SectionCard(title = stringResource(R.string.bubble_title)) {
                OutlinedButton(onClick = onOpenBubble, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_bubble_open))
                }
            }

            SectionCard(title = stringResource(R.string.settings_theme_title)) {
                ThemeModeSelector(selected = themeMode, onSelect = onThemeModeChange)
            }

            SectionCard(title = stringResource(R.string.home_tools_title)) {
                OutlinedButton(onClick = onOpenOcrTest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ocr_test_title))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val labels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
            ) {
                Text(labels.getValue(mode))
            }
        }
    }
}
