package com.vpr.screenlate.dictionaries

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.ui.components.ReorderableColumn

/** Installed dictionaries (order, enable, delete), running imports and the download catalog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionariesScreen(onBack: () -> Unit, viewModel: DictionariesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<DictionaryEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dictionaries_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = { picker.launch(ARCHIVE_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dictionaries_import_file))
            }

            importError?.let { error ->
                ErrorCard(stringResource(R.string.dictionaries_import_failed, error), viewModel::dismissImportError)
            }
            state.tasks.forEach { task ->
                TaskCard(task, onDismiss = viewModel::clearFinishedTasks)
            }

            SectionTitle(stringResource(R.string.dictionaries_installed))
            if (state.loaded && state.dictionaries.isEmpty()) {
                Text(stringResource(R.string.dictionaries_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    stringResource(R.string.dictionaries_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ReorderableColumn(
                items = state.dictionaries,
                key = { it.id },
                onReorder = viewModel::reorder,
                spacing = 8.dp,
            ) { dictionary, handle, dragging ->
                DictionaryCard(
                    dictionary = dictionary,
                    handle = handle,
                    dragging = dragging,
                    onEnabledChange = { viewModel.setEnabled(dictionary, it) },
                    onDelete = { pendingDelete = dictionary },
                )
            }

            SectionTitle(stringResource(R.string.dictionaries_catalog))
            state.catalog.forEach { item ->
                CatalogCard(item, onDownload = { viewModel.download(item.entry) })
            }
        }
    }

    pendingDelete?.let { dictionary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.dictionaries_delete_title)) },
            text = { Text(stringResource(R.string.dictionaries_delete_message, dictionary.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(dictionary)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun DictionaryCard(
    dictionary: DictionaryEntity,
    handle: Modifier,
    dragging: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(dictionary.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
            Icon(
                painterResource(R.drawable.ic_drag_handle),
                contentDescription = stringResource(R.string.dictionaries_reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = handle.padding(12.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 12.dp),
            ) {
                Text(dictionary.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle(dictionary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = dictionary.enabled, onCheckedChange = onEnabledChange)
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DetailLine(R.string.dictionaries_revision, dictionary.revision)
                DetailLine(R.string.dictionaries_author, dictionary.author)
                DetailLine(R.string.dictionaries_counts, counts(dictionary))
                dictionary.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                dictionary.attribution?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Text("${stringResource(label)}: $value", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun subtitle(dictionary: DictionaryEntity): String {
    val kind = stringResource(kindLabel(dictionary.kind))
    val languages = listOfNotNull(dictionary.sourceLanguage, dictionary.targetLanguage).joinToString(" → ")
    return listOf(kind, languages).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun counts(dictionary: DictionaryEntity): String = listOfNotNull(
    dictionary.termCount.takeIf { it > 0 }?.let { stringResource(R.string.dictionaries_count_terms, it) },
    dictionary.frequencyCount.takeIf { it > 0 }?.let { stringResource(R.string.dictionaries_count_frequencies, it) },
    dictionary.pitchCount.takeIf { it > 0 }?.let { stringResource(R.string.dictionaries_count_pitches, it) },
    dictionary.kanjiCount.takeIf { it > 0 }?.let { stringResource(R.string.dictionaries_count_kanji, it) },
    dictionary.mediaCount.takeIf { it > 0 }?.let { stringResource(R.string.dictionaries_count_media, it) },
).joinToString(", ")

private fun kindLabel(kind: DictionaryKind): Int = when (kind) {
    DictionaryKind.TERM -> R.string.dictionaries_kind_term
    DictionaryKind.FREQUENCY -> R.string.dictionaries_kind_frequency
    DictionaryKind.PITCH -> R.string.dictionaries_kind_pitch
    DictionaryKind.KANJI -> R.string.dictionaries_kind_kanji
}

@Composable
private fun TaskCard(task: ImportTask, onDismiss: () -> Unit) {
    if (task.state == ImportTask.State.FAILED) {
        ErrorCard(stringResource(R.string.dictionaries_import_failed, task.error ?: task.name), onDismiss)
        return
    }
    val name = task.name.ifEmpty { stringResource(R.string.dictionaries_bundled) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val status = when (task.state) {
                ImportTask.State.QUEUED -> stringResource(R.string.dictionaries_task_queued, name)
                ImportTask.State.DOWNLOADING -> stringResource(R.string.dictionaries_task_downloading, name)
                else -> stringResource(R.string.dictionaries_task_importing, name)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            val percent = task.downloadPercent
            if (percent != null) {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}

@Composable
private fun CatalogCard(item: CatalogItem, onDownload: () -> Unit) {
    val entry = item.entry
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                val languages = listOfNotNull(entry.sourceLanguage, entry.targetLanguage).joinToString(" → ")
                Text(
                    listOf(stringResource(kindLabel(entry.kind)), languages, "${entry.sizeMb} MB", entry.license)
                        .filter { it.isNotEmpty() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(entry.description(), style = MaterialTheme.typography.bodySmall)
            }
            when {
                item.inProgress -> Spacer(Modifier.size(48.dp))
                item.installed -> Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.dictionaries_installed_mark),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp),
                )
                else -> IconButton(onClick = onDownload) {
                    Icon(painterResource(R.drawable.ic_download), stringResource(R.string.dictionaries_download))
                }
            }
        }
    }
}

private val ARCHIVE_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
