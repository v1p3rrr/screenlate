package com.vpr.screenlate.languages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard

/**
 * A page's settings that belong to a language, in a card named after the [shown] language with chips that switch it.
 * Shown only while several languages are turned on; with one, the page puts these settings where they always were.
 */
@Composable
fun LanguageCard(
    profiles: LanguageProfilesState,
    shown: Language,
    onShow: (Language) -> Unit,
    hint: String = stringResource(R.string.languages_settings_hint),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    SectionCard(title = shown.displayName()) {
        LanguageChips(profiles.turnedOn, shown, onShow)
        Hint(hint)
        content()
    }
}

/** A chip per turned-on language; the selected one is [shown]. Scrolls sideways when the chips do not fit. */
@Composable
fun LanguageChips(languages: List<Language>, shown: Language, onShow: (Language) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        languages.forEach { language ->
            FilterChip(
                selected = language == shown,
                onClick = { onShow(language) },
                label = { Text(language.displayName()) },
            )
        }
    }
}
