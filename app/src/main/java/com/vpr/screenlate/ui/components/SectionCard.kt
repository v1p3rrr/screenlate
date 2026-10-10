package com.vpr.screenlate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A card with a [title]; [info] adds an ⓘ next to it with a longer explanation. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (info == null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            } else {
                LabelWithInfo(title, info, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
