// TEMPORARY DIAGNOSTICS BUILD: this file is removed with the diagnostics (ai/notes/diagnostics.md).
package com.vpr.screenlate.settings

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.vpr.screenlate.diagnostics.Diag
import com.vpr.screenlate.diagnostics.R
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the temporary diagnostics build collects, how much it holds since when, and deleting it. */
@Composable
fun DiagnosticsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val status by produceState<Pair<Long?, Long>?>(null, refresh) {
        value = withContext(Dispatchers.IO) { Diag.since() to Diag.sizeBytes() }
    }
    SectionCard(title = stringResource(R.string.diag_title)) {
        Hint(stringResource(R.string.diag_hint))
        status?.let { (since, size) ->
            val date = since?.let {
                DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
            } ?: "–"
            Text(stringResource(R.string.diag_status, date, Formatter.formatShortFileSize(context, size)))
        }
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.diag_clear), textAlign = TextAlign.Center)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(R.string.diag_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) { Diag.clear() }
                            refresh++
                        }
                    },
                ) { Text(stringResource(R.string.diag_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
