package com.vpr.screenlate.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.theme.AccentDefaults

/**
 * An icon-only button whose long press shows [tooltip], its short name. [description] is what screen readers say,
 * when it needs more than the name.
 */
@Composable
fun TooltipIconButton(
    @DrawableRes icon: Int,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = tooltip,
    enabled: Boolean = true,
) {
    TooltipIconButton(tooltip, onClick, modifier, enabled) { Icon(painterResource(icon), description) }
}

/** An icon-only button with its own [content] (e.g. a badged icon) and a [tooltip] on long press. */
@Composable
fun TooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    WithTooltip(tooltip) { IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content) }
}

/** Shows [tooltip] on a long press on [content], a button without a text label. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(tooltip: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/** A spinner in the place of an icon button while its action runs; holding it shows [description]. */
@Composable
fun IconButtonProgress(description: String) {
    WithTooltip(description) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AccentDefaults.progress)
        }
    }
}

/** The top bar's back arrow. */
@Composable
fun BackButton(onBack: () -> Unit) {
    TooltipIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
}

/** A reset icon for a top bar that asks with [title] and [text] before it calls [onReset]. */
@Composable
fun ResetButton(tooltip: String, title: String, text: String, onReset: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    TooltipIconButton(R.drawable.ic_reset, tooltip, onClick = { asking = true })
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(title) },
            text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        // A focused field would keep its text and could write it back after the reset.
                        focusManager.clearFocus()
                        onReset()
                    },
                ) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
