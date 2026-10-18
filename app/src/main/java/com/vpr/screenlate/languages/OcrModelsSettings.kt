package com.vpr.screenlate.languages

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.ui.components.ConfirmDelete
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.InfoButton
import com.vpr.screenlate.ui.components.InfoDialog
import com.vpr.screenlate.ui.components.LabelWithInfo

/**
 * The on-device model of each turned-on language, under a subheading; a row opens the language's models, and so does
 * [openLanguage] (a language code) at first. Disabled rows stay visible while on-device recognition is off.
 */
@Composable
fun OcrModelRows(enabled: Boolean, openLanguage: String? = null, viewModel: OcrModelsViewModel = hiltViewModel()) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    var opened by rememberSaveable { mutableStateOf(openLanguage) }
    LabelWithInfo(
        stringResource(R.string.ocr_models_heading),
        stringResource(R.string.ocr_models_info),
        style = MaterialTheme.typography.titleMedium,
    )
    lists.orEmpty().forEach { list ->
        val color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { opened = list.language.code }
                .padding(vertical = 6.dp),
        ) {
            Text(list.language.displayName(), color = color)
            Hint(list.active?.let { modelTitle(it) } ?: stringResource(R.string.ocr_models_none))
        }
    }
    val shown = lists?.firstOrNull { it.language.code == opened }
    if (enabled && shown != null) {
        OcrModelDialog(
            shown,
            onChoose = { viewModel.choose(shown.language, it) },
            onDownload = viewModel::download,
            onDelete = viewModel::delete,
            onDismiss = { opened = null },
        )
    }
}

/** A language's models: ML Kit where it reads the language, then the catalog's, each to choose, download or delete. */
@Composable
private fun OcrModelDialog(
    list: LanguageModelList,
    onChoose: (OcrModelOption) -> Unit,
    onDownload: (OcrModelOption) -> Unit,
    onDelete: (OcrModelOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleting by remember { mutableStateOf<OcrModelOption?>(null) }
    InfoDialog(stringResource(R.string.ocr_models_dialog_title, list.language.displayName()), onDismiss) {
        if (list.options.isEmpty()) Text(stringResource(R.string.ocr_models_empty))
        list.options.forEach { option ->
            OcrModelRow(list.language, option, onChoose = { onChoose(option) }, onDownload = { onDownload(option) }, onDelete = { deleting = option })
        }
        if (list.options.isNotEmpty()) Hint(stringResource(R.string.ocr_models_dialog_hint))
    }
    deleting?.let { option ->
        val context = LocalContext.current
        val title = modelTitle(option)
        val text = buildList {
            add(stringResource(R.string.ocr_models_delete_text, title, Formatter.formatShortFileSize(context, option.bytes)))
            (option.deletion as? ModelDeletion.Allowed)?.alsoFor?.takeIf { it.isNotEmpty() }?.let { languages ->
                add(stringResource(R.string.ocr_models_delete_shared, languages.joinToString(", ") { it.displayName() }))
            }
        }.joinToString(" ")
        ConfirmDelete(
            title = stringResource(R.string.ocr_models_delete_title),
            text = text,
            onDelete = { onDelete(option) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun OcrModelRow(
    language: Language,
    option: OcrModelOption,
    onChoose: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val title = modelTitle(option)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = option.active, enabled = option.installed, role = Role.RadioButton, onClick = onChoose),
    ) {
        RadioButton(selected = option.active, onClick = null, enabled = option.installed)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                val mlKitInfo = stringResource(R.string.ocr_models_mlkit_info)
                InfoButton(title, option.entry?.description()?.takeIf { it.isNotEmpty() } ?: mlKitInfo)
            }
            val details = buildList {
                if (option.recommended) add(stringResource(R.string.ocr_models_recommended))
                if (option.entry != null) {
                    val size = Formatter.formatShortFileSize(context, option.bytes)
                    add(if (option.installed) stringResource(R.string.ocr_models_installed_size, size) else size)
                }
            }
            if (details.isNotEmpty()) Hint(details.joinToString(" · "))
            val users = (option.deletion as? ModelDeletion.InUse)?.languages?.filter { it != language }.orEmpty()
            if (users.isNotEmpty()) Hint(stringResource(R.string.ocr_models_in_use, users.joinToString(", ") { it.displayName() }))
        }
        when {
            option.downloading -> Box(modifier = Modifier.padding(12.dp)) {
                val percent = option.percent ?: -1
                if (percent >= 0) {
                    CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.size(24.dp))
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            !option.installed -> TextButton(onClick = onDownload) { Text(stringResource(R.string.dictionaries_download)) }
            option.deletion is ModelDeletion.Allowed -> TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
        }
    }
}

@Composable
private fun modelTitle(option: OcrModelOption): String =
    option.entry?.displayTitle() ?: stringResource(R.string.ocr_models_mlkit)
