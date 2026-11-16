package com.vpr.screenlate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.R

/**
 * A card with a [title]; [info] adds an ⓘ next to it with a longer explanation,
 * and optional [leadingContent] is placed before the title.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val titleContent = @Composable {
                if (info == null) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                } else {
                    LabelWithInfo(title, info, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (leadingContent == null) {
                titleContent()
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    leadingContent()
                    titleContent()
                }
            }
            content()
        }
    }
}

/** Notice banner shown when a section's remote functionality is disabled by Forced Offline Mode. */
@Composable
fun OfflineNoticeCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cloud_off),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
