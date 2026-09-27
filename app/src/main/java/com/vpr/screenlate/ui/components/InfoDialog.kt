package com.vpr.screenlate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R

/** Details the user rarely needs, in a scrollable dialog. */
@Composable
fun InfoDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** An ⓘ button that opens [InfoDialog] with [text]. */
@Composable
fun InfoButton(title: String, text: String) {
    InfoButton(title) { Text(text, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun InfoButton(title: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.ic_info), stringResource(R.string.action_more_info, title))
    }
    if (open) InfoDialog(title, onDismiss = { open = false }, content = content)
}

/** A setting's label with an ⓘ button for its longer explanation. */
@Composable
fun LabelWithInfo(
    label: String,
    info: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(label, style = style, modifier = Modifier.weight(1f, fill = false))
        InfoButton(label, info)
    }
}
