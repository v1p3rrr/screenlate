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
import androidx.lifecycle.compose.LifecycleResumeEffect
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
) {
    val context = LocalContext.current
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

            SectionCard(title = stringResource(R.string.onboarding_device_title)) {
                Text(stringResource(R.string.onboarding_device_app_launch))
                Text(stringResource(R.string.onboarding_device_restricted_settings))
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
