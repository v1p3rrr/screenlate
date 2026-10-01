package com.vpr.screenlate.settings

import android.app.LocaleConfig
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.Segments
import com.vpr.screenlate.ui.components.SettingsScaffold

/** Theme, e-ink mode, and on Android 13+ the app's own UI language. The popup's text has its own screen. */
@Composable
fun AppearanceScreen(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit, onBack: () -> Unit) {
    SettingsScaffold(
        stringResource(R.string.appearance_title),
        onBack,
        actions = { SectionResetButton(SettingsSection.APPEARANCE) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_theme_title)) {
                Segments(
                    options = ThemeMode.entries,
                    selected = themeMode,
                    label = {
                        stringResource(
                            when (it) {
                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                ThemeMode.DARK -> R.string.settings_theme_dark
                            },
                        )
                    },
                    onSelect = onThemeModeChange,
                )
            }
            EInkSection()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SectionCard(title = stringResource(R.string.settings_language)) { LanguageSelector() }
            }
        }
    }
}

/**
 * The same setting as the system's per-app language, as a drop-down: the phone's language, then every language the
 * app has strings for (the locale config generated from the resources), so a new translation shows up by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageSelector() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    val system = LanguageOption("", stringResource(R.string.settings_language_system), flag = null)
    val options = remember {
        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            LocaleConfig.fromContextIgnoringOverride(context)
        } else {
            LocaleConfig(context)
        }
        val locales = config.supportedLocales ?: LocaleList.getEmptyLocaleList()
        LanguageOptions.of((0 until locales.size()).map(locales::get))
    }
    var selected by remember { mutableStateOf(manager.applicationLocales.toLanguageTags().substringBefore(',')) }
    var expanded by remember { mutableStateOf(false) }
    val current = LanguageOptions.selected(options, selected) ?: system
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current.label(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf(system) + options).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        expanded = false
                        selected = option.tag
                        // The system recreates the activity with the new locale.
                        manager.applicationLocales = LocaleList.forLanguageTags(option.tag)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
    Hint(stringResource(R.string.settings_language_hint))
    Hint(stringResource(R.string.settings_language_translations))
}

private fun LanguageOption.label(): String = listOfNotNull(flag, name).joinToString("  ")
