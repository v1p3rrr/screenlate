package com.vpr.screenlate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vpr.screenlate.R

/** The interface language's mark between the items of a list: a comma, the Arabic comma, or the Japanese and Chinese 、. */
@Composable
fun listSeparator(): String = stringResource(R.string.list_separator)
