package com.vpr.screenlate.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** Theme and, on Android 13+, the app's own UI language. */
@Composable
fun AppearanceScreen(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit, onBack: () -> Unit) {
    SettingsScaffold(stringResource(R.string.appearance_title), onBack) { padding ->
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SectionCard(title = stringResource(R.string.settings_language)) { LanguageSelector() }
            }
        }
    }
}

/** The same setting as the system's per-app language. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageSelector() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    var selected by remember { mutableStateOf(manager.applicationLocales.toLanguageTags().substringBefore('-')) }
    val labels = mapOf(
        "" to stringResource(R.string.settings_language_system),
        "en" to stringResource(R.string.settings_language_en),
        "ru" to stringResource(R.string.settings_language_ru),
    )
    Segments(
        options = labels.keys.toList(),
        selected = selected,
        label = { labels.getValue(it) },
        onSelect = { tag ->
            selected = tag
            // The system recreates the activity with the new locale.
            manager.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        },
    )
    Hint(stringResource(R.string.settings_language_hint))
}
