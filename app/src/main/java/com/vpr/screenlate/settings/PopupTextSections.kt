package com.vpr.screenlate.settings

import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.overlay.fonts.CatalogFont
import com.vpr.screenlate.overlay.fonts.CssCheck
import com.vpr.screenlate.overlay.fonts.FontDownload
import com.vpr.screenlate.overlay.fonts.FontImport
import com.vpr.screenlate.overlay.fonts.InstalledFont
import com.vpr.screenlate.overlay.settings.PopupAppearance
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard

private val FONT_TYPES = arrayOf(
    "font/*",
    "application/font-sfnt",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream",
)

/** The lookup page's font and custom CSS, as two cards of the Appearance screen. */
@Composable
fun PopupTextSections(viewModel: PopupAppearanceViewModel = hiltViewModel()) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val current = appearance ?: return
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    FontCard(current, installed, viewModel)
    CssCard(current.customCss, installed, viewModel)
}

@Composable
private fun FontCard(appearance: PopupAppearance, installed: List<InstalledFont>, viewModel: PopupAppearanceViewModel) {
    val typefaces by viewModel.typefaces.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val lastImport by viewModel.lastImport.collectAsStateWithLifecycle()
    val systemFontMissing by viewModel.systemFontMissing.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val selected = installed.firstOrNull { it.id == appearance.fontId }
    SectionCard(title = stringResource(R.string.popup_font_title)) {
        Hint(stringResource(R.string.popup_font_hint))
        FontOption(
            title = stringResource(R.string.popup_font_system),
            subtitle = stringResource(R.string.popup_font_system_hint),
            selected = selected == null,
            typeface = typefaces[PopupAppearanceViewModel.SYSTEM],
            onSelect = { viewModel.selectFont(null) },
        )
        if (systemFontMissing) ErrorText(stringResource(R.string.popup_font_system_missing))
        installed.forEach { font ->
            FontOption(
                title = font.family,
                subtitle = stringResource(
                    if (font.catalogId != null) R.string.popup_font_downloaded else R.string.popup_font_own_file,
                ),
                selected = font == selected,
                typeface = typefaces[font.id],
                onSelect = { viewModel.selectFont(font.id) },
                onDelete = { viewModel.delete(font) },
            )
        }
        FontSize(appearance.fontSize, viewModel::setFontSize)
        Preview(
            typeface = typefaces[selected?.id ?: PopupAppearanceViewModel.SYSTEM],
            size = appearance.fontSize,
        )
        HorizontalDivider()
        Text(stringResource(R.string.popup_font_catalog), style = MaterialTheme.typography.labelLarge)
        val installedIds = installed.mapNotNull { it.catalogId }.toSet()
        viewModel.catalog.filter { it.id !in installedIds }.forEach { font ->
            CatalogRow(font, downloads[font.id]) { viewModel.download(font) }
        }
        Hint(stringResource(R.string.popup_font_proprietary))
        OutlinedButton(onClick = { picker.launch(FONT_TYPES) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.popup_font_add_file))
        }
        when (lastImport) {
            FontImport.NotAFont -> ErrorText(stringResource(R.string.popup_font_not_a_font))
            FontImport.TooLarge -> ErrorText(stringResource(R.string.popup_font_too_large))
            else -> Unit
        }
    }
}

@Composable
private fun FontOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    typeface: Typeface?,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = typeface?.let { FontFamily(it) })
            Hint(subtitle)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.popup_font_delete, title))
            }
        }
    }
}

/** The slider moves freely; the setting is written when the finger is lifted. */
@Composable
private fun FontSize(size: Int, onChange: (Int) -> Unit) {
    var value by remember(size) { mutableFloatStateOf(size.toFloat()) }
    Text(stringResource(R.string.popup_font_size, value.toInt()), style = MaterialTheme.typography.labelLarge)
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onChange(value.toInt()) },
        valueRange = PopupAppearance.MIN_FONT_SIZE.toFloat()..PopupAppearance.MAX_FONT_SIZE.toFloat(),
        steps = PopupAppearance.MAX_FONT_SIZE - PopupAppearance.MIN_FONT_SIZE - 1,
    )
}

@Composable
private fun Preview(typeface: Typeface?, size: Int) {
    val support = PopupAppearanceViewModel.LANGUAGE.support
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Text(
            support.fontSample,
            fontFamily = typeface?.let { FontFamily(it) },
            fontSize = size.sp,
            style = MaterialTheme.typography.bodyLarge.copy(localeList = LocaleList(support.languageTag)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        )
    }
}

@Composable
private fun CatalogRow(font: CatalogFont, download: FontDownload?, onDownload: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(font.family, style = MaterialTheme.typography.bodyLarge)
        Hint(font.description(locale.language))
        Hint(stringResource(R.string.popup_font_license, font.license))
        when (download) {
            is FontDownload.Running -> LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth())
            else -> {
                if (download == FontDownload.Failed) ErrorText(stringResource(R.string.popup_font_download_failed))
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.popup_font_download, String.format(locale, "%.1f", font.sizeMb)))
                }
            }
        }
    }
}

@Composable
private fun CssCard(saved: String, installed: List<InstalledFont>, viewModel: PopupAppearanceViewModel) {
    var css by remember { mutableStateOf(saved) }
    val issues = remember(css, installed) { PopupAppearanceViewModel.cssIssues(css, installed) }
    SectionCard(title = stringResource(R.string.popup_css_title)) {
        Hint(stringResource(R.string.popup_css_hint))
        issues.forEach { issue -> ErrorText("⚠ " + issueText(issue)) }
        OutlinedTextField(
            value = css,
            onValueChange = {
                css = it
                viewModel.setCustomCss(it)
            },
            placeholder = {
                Text(".gloss-content { font-size: 16px; }", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun issueText(issue: CssCheck.Issue): String = stringResource(
    when (issue.problem) {
        CssCheck.Problem.UNCLOSED_COMMENT -> R.string.css_problem_unclosed_comment
        CssCheck.Problem.UNCLOSED_STRING -> R.string.css_problem_unclosed_string
        CssCheck.Problem.UNEXPECTED_BRACE -> R.string.css_problem_unexpected_brace
        CssCheck.Problem.UNCLOSED_BLOCK -> R.string.css_problem_unclosed_block
        CssCheck.Problem.MISSING_COLON -> R.string.css_problem_missing_colon
        CssCheck.Problem.BAD_PROPERTY -> R.string.css_problem_bad_property
        CssCheck.Problem.EMPTY_VALUE -> R.string.css_problem_empty_value
        CssCheck.Problem.EMPTY_FONT_NAME -> R.string.css_problem_empty_font_name
        CssCheck.Problem.OUTSIDE_RULE -> R.string.css_problem_outside_rule
        CssCheck.Problem.UNKNOWN_FONT -> R.string.css_problem_unknown_font
    },
    issue.line,
    issue.detail,
)

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
