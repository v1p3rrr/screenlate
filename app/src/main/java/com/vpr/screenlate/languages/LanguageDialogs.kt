package com.vpr.screenlate.languages

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName

/** Turning [language] off asks about its files, [filesBytes] on disk; null when it has none. */
data class TurnOffRequest(val language: Language, val filesBytes: Long?)

/** Turning [language] off keeps its settings; [filesBytes] is zero for downloads only, null for no files or downloads. */
@Composable
fun TurnOffLanguageDialog(
    language: Language,
    filesBytes: Long?,
    onTurnOff: (deleteFiles: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var delete by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.displayName()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.home_language_turn_off_text))
                if (filesBytes != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .toggleable(value = delete, role = Role.Checkbox, onValueChange = { delete = it }),
                    ) {
                        Checkbox(checked = delete, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                        Text(
                            stringResource(
                                R.string.home_language_delete_files,
                                Formatter.formatShortFileSize(LocalContext.current, filesBytes),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onTurnOff(filesBytes != null && delete) }) { Text(stringResource(R.string.home_language_turn_off_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
