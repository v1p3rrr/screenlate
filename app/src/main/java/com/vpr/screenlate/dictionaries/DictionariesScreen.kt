package com.vpr.screenlate.dictionaries

import android.os.SystemClock
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.dictionary.api.imports.DictionaryReset
import com.vpr.screenlate.dictionary.api.imports.ImportTask
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import com.vpr.screenlate.dictionary.api.registry.DictionaryUpdate
import com.vpr.screenlate.dictionary.api.registry.isLastTermDictionary
import com.vpr.screenlate.ui.components.BackButton
import com.vpr.screenlate.ui.components.ReorderableColumn
import com.vpr.screenlate.ui.components.IconButtonProgress
import com.vpr.screenlate.ui.components.ResetButton
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.theme.AccentDefaults
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Installed dictionaries (order, enable, delete), running imports and the download catalog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionariesScreen(
    onBack: () -> Unit,
    onOpenYomitanImport: () -> Unit,
    viewModel: DictionariesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateState.collectAsStateWithLifecycle()
    val resetPhase by viewModel.resetPhase.collectAsStateWithLifecycle()
    val resetError by viewModel.resetError.collectAsStateWithLifecycle()
    val resetGaveUp by viewModel.resetGaveUp.collectAsStateWithLifecycle()
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<DictionaryEntity?>(null) }
    var editingLanguages by remember { mutableStateOf<DictionaryEntity?>(null) }
    val askNotifications = rememberImportNotificationsAsk()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            askNotifications()
            viewModel.importFrom(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dictionaries_title)) },
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    if (resetPhase != null) {
                        IconButtonProgress(stringResource(R.string.reset_dictionaries_busy))
                    } else {
                        ResetButton(
                            tooltip = stringResource(R.string.reset_dictionaries_tooltip),
                            title = stringResource(R.string.reset_dictionaries_title),
                            text = stringResource(R.string.reset_dictionaries_text),
                            onReset = viewModel::resetDictionaries,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val scroll = rememberScrollState()
        val anchors = remember(scroll) { ScrollAnchors(scroll) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The installs that follow show as an import task.
            if (resetPhase == DictionaryReset.Phase.DELETING) {
                Text(stringResource(R.string.reset_dictionaries_running))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentDefaults.progress)
            }
            if (resetGaveUp && resetPhase == null) {
                NoticeCard(stringResource(R.string.reset_dictionaries_gave_up_text), viewModel::dismissResetGaveUp)
            }
            resetError?.let { error ->
                ErrorCard(stringResource(R.string.reset_dictionaries_failed, error), viewModel::dismissResetError)
            }
            deleteError?.let { error ->
                ErrorCard(stringResource(R.string.dictionaries_delete_failed, error), viewModel::dismissDeleteError)
            }
            OutlinedButton(onClick = { picker.launch(ARCHIVE_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dictionaries_import_file))
            }

            OutlinedButton(onClick = onOpenYomitanImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dictionaries_import_yomitan_backup))
            }
            OutlinedButton(
                onClick = viewModel::checkUpdates,
                enabled = updateCheck?.updates != null || updateCheck == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dictionaries_check_updates))
            }
            updateCheck?.let { check ->
                UpdatesCard(
                    check,
                    onUpdate = { items ->
                        askNotifications()
                        viewModel.update(items)
                    },
                )
            }

            importError?.let { error ->
                ErrorCard(stringResource(R.string.dictionaries_import_failed, error), viewModel::dismissImportError)
            }
            state.tasks.forEach { task ->
                TaskCard(
                    task,
                    onDismiss = viewModel::clearFinishedTasks,
                    onCancel = { viewModel.cancel(task) },
                    onRetryBundled = {
                        askNotifications()
                        viewModel.retryBundledInstall()
                    },
                )
            }
            ImportNotificationsCard()

            SectionTitle(stringResource(R.string.dictionaries_installed), anchors.at(0))
            if (state.loaded && state.installed.isEmpty()) {
                Text(stringResource(R.string.dictionaries_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    stringResource(R.string.dictionaries_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val allDictionaries = state.installed.flatMap { it.dictionaries }
            state.installed.forEach { section ->
                SubsectionTitle(stringResource(sectionLabel(section.kind)))
                ReorderableColumn(
                    items = section.dictionaries,
                    key = { it.id },
                    onReorder = { viewModel.reorder(section.kind, it) },
                    spacing = 8.dp,
                ) { dictionary, handle, dragging ->
                    DictionaryCard(
                        dictionary = dictionary,
                        handle = handle,
                        dragging = dragging,
                        onEnabledChange = { on ->
                            if (!on && dictionary.isLastTermDictionary(allDictionaries) { it.id !in state.withoutFiles }) {
                                Toast.makeText(context, R.string.dictionaries_last_term, Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.setEnabled(dictionary, on)
                            }
                        },
                        onDelete = {
                            if (dictionary.isLastTermDictionary(allDictionaries) { it.id !in state.withoutFiles }) {
                                Toast.makeText(context, R.string.dictionaries_last_term, Toast.LENGTH_LONG).show()
                            } else {
                                pendingDelete = dictionary
                            }
                        },
                        onEditLanguages = { editingLanguages = dictionary },
                        links = state.links[dictionary.id],
                        remoteHosts = state.remoteCss[dictionary.title].orEmpty(),
                        sort = if (section.kind == DictionaryKind.FREQUENCY) {
                            SortChoice(dictionary.id == state.sortDictionaryId) { viewModel.setSortDictionary(dictionary) }
                        } else {
                            null
                        },
                    )
                }
            }

            SectionTitle(stringResource(R.string.dictionaries_catalog), anchors.at(1))
            state.catalog.forEach { group ->
                SubsectionTitle(languageName(group.sourceLanguage))
                group.sections.forEach { section ->
                    Text(
                        catalogSectionLabel(section),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    section.items.forEach { item ->
                        key(item.entry.title) {
                            CatalogCard(
                                item,
                                onDownload = {
                                    askNotifications()
                                    viewModel.download(item.entry)
                                },
                                onCancel = viewModel::cancel,
                            )
                        }
                    }
                }
            }
        }
    }

    editingLanguages?.let { dictionary ->
        DictionaryLanguagesDialog(
            dictionary = dictionary,
            onSave = { source, target ->
                viewModel.setLanguages(dictionary, source, target)
                editingLanguages = null
            },
            onDismiss = { editingLanguages = null },
        )
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
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 8.dp))
}

/**
 * Keeps the part of the page the user looks at in place when something above it appears or goes away, such as a task
 * card at the top after a tap on Download far below, or a dictionary added to the installed list above the catalog.
 * Each anchor marks the top of a part, numbered down the page; the lowest one above the view moves the scroll by as
 * much as it moved itself.
 */
private class ScrollAnchors(private val scroll: ScrollState) {
    private val tops = HashMap<Int, Int>()

    fun at(index: Int): Modifier = Modifier.onGloballyPositioned { coordinates ->
        val top = coordinates.positionInParent().y.roundToInt()
        val previous = tops.put(index, top) ?: return@onGloballyPositioned
        if (top == previous || previous >= scroll.value) return@onGloballyPositioned
        if (tops.any { (other, otherTop) -> other > index && otherTop < scroll.value }) return@onGloballyPositioned
        scroll.dispatchRawDelta((top - previous).toFloat())
    }
}

@Composable
private fun SubsectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

private fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
private fun catalogSectionLabel(section: CatalogSection): String = when (section.kind) {
    DictionaryKind.TERM -> section.targetLanguage
        ?.let { stringResource(R.string.dictionaries_catalog_translations, languageName(it)) }
        ?: stringResource(R.string.dictionaries_kind_term)
    else -> stringResource(kindLabel(section.kind))
}

private fun sectionLabel(kind: DictionaryKind): Int = when (kind) {
    DictionaryKind.TERM -> R.string.dictionaries_section_terms
    DictionaryKind.FREQUENCY -> R.string.dictionaries_kind_frequency
    DictionaryKind.PITCH -> R.string.dictionaries_kind_pitch
    DictionaryKind.KANJI -> R.string.dictionaries_kind_kanji
}

// `handle` goes on the drag handle, not on the card, so it is not the conventional `modifier` parameter.
@Suppress("ModifierParameter")
@Composable
private fun DictionaryCard(
    dictionary: DictionaryEntity,
    handle: Modifier,
    dragging: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEditLanguages: () -> Unit,
    links: DictionaryLinks?,
    remoteHosts: List<String> = emptyList(),
    sort: SortChoice? = null,
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
                if (remoteHosts.isNotEmpty()) {
                    Text(
                        stringResource(R.string.dictionaries_remote_css, remoteHosts.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (sort != null && dictionary.enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sort.selected, onClick = sort.onSelect, colors = AccentDefaults.radioButtonColors())
                        Text(stringResource(R.string.dictionaries_sort_by), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Switch(checked = dictionary.enabled, onCheckedChange = onEnabledChange, colors = AccentDefaults.switchColors())
            TooltipIconButton(R.drawable.ic_delete, stringResource(R.string.action_delete), onClick = onDelete)
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stringResource(R.string.dictionaries_languages)}: " +
                            subtitle(dictionary).ifEmpty { stringResource(R.string.dictionaries_language_none) },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onEditLanguages) { Text(stringResource(R.string.dictionaries_languages_edit)) }
                }
                DetailLine(R.string.dictionaries_version, dictionary.revision)
                DetailLine(R.string.dictionaries_author, dictionary.author)
                DetailLine(R.string.dictionaries_counts, counts(dictionary))
                dictionary.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                dictionary.attribution?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (links != null && !links.isEmpty) LinkButtons(links)
            }
        }
    }
}

/** Opens the dictionary's website or its download in the browser. */
@Composable
private fun LinkButtons(links: DictionaryLinks) {
    val uriHandler = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        links.website?.let { url ->
            OutlinedButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                Text(stringResource(R.string.dictionaries_website))
            }
        }
        links.download?.let { url ->
            OutlinedButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                Text(stringResource(R.string.dictionaries_download_link))
            }
        }
    }
}

/** The "sort by this dictionary" radio of a frequency dictionary. */
private class SortChoice(val selected: Boolean, val onSelect: () -> Unit)

@Composable
private fun UpdatesCard(check: UpdateCheck, onUpdate: (List<DictionaryUpdate>) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val updates = check.updates
            when {
                updates == null -> {
                    Text(stringResource(R.string.dictionaries_checking_updates))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentDefaults.progress)
                }
                updates.isEmpty() -> Text(stringResource(R.string.dictionaries_up_to_date))
                else -> {
                    updates.forEach { update ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(update.dictionary.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${update.dictionary.revision} → ${update.revision}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onUpdate(listOf(update)) }) {
                                Text(stringResource(R.string.dictionaries_update))
                            }
                        }
                    }
                    if (updates.size > 1) {
                        Button(onClick = { onUpdate(updates) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.dictionaries_update_all))
                        }
                    }
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

private fun subtitle(dictionary: DictionaryEntity): String {
    val languages = listOfNotNull(dictionary.sourceLanguage, dictionary.targetLanguage).joinToString(" → ")
    return languages
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
private fun TaskCard(task: ImportTask, onDismiss: () -> Unit, onCancel: () -> Unit, onRetryBundled: () -> Unit) {
    if (task.state == ImportTask.State.FAILED) {
        if (task.paused) {
            ErrorCard(stringResource(R.string.dictionaries_bundled_paused), onDismiss, onRetry = onRetryBundled)
            return
        }
        val shortage = task.shortage
        val message = if (task.interrupted) {
            stringResource(R.string.dictionaries_import_interrupted, task.name.ifEmpty { stringResource(R.string.dictionaries_bundled) })
        } else if (shortage != null) {
            val context = LocalContext.current
            stringResource(
                R.string.dictionaries_import_no_space,
                task.name,
                Formatter.formatShortFileSize(context, shortage.neededBytes),
                Formatter.formatShortFileSize(context, shortage.freeBytes),
            )
        } else {
            val name = task.name.ifEmpty { stringResource(R.string.dictionaries_bundled) }
            val error = task.error
            // The name tells which of several queued imports failed, e.g. after "Update all".
            if (error != null) {
                stringResource(R.string.dictionaries_import_failed_named, name, error)
            } else {
                stringResource(R.string.dictionaries_import_failed, name)
            }
        }
        ErrorCard(message, onDismiss)
        return
    }
    val name = task.name.ifEmpty { stringResource(R.string.dictionaries_bundled) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val status = when (task.state) {
                ImportTask.State.QUEUED -> stringResource(R.string.dictionaries_task_queued, name)
                ImportTask.State.DOWNLOADING -> stringResource(R.string.dictionaries_task_downloading, name)
                ImportTask.State.CHECKING_SPACE -> stringResource(R.string.dictionaries_task_checking_space, name)
                ImportTask.State.CONVERTING -> stringResource(R.string.dictionaries_task_converting, name)
                else -> stringResource(R.string.dictionaries_task_importing, name)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            val percent = task.percent
            if (percent != null) {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth(), color = AccentDefaults.progress)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentDefaults.progress)
            }
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

/** A problem that stays until the user closes it with ✕ or it is resolved. */
@Composable
private fun NoticeCard(message: String, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 16.dp)) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            TooltipIconButton(R.drawable.ic_close, stringResource(R.string.action_close), onClick = onClose)
        }
    }
}

/** An error with a dismiss button; with [onRetry], a "Try again" button too, both below the text. */
@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        if (onRetry == null) {
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
        } else {
            Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp)) {
                Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(end = 8.dp))
                FlowRow(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.update_retry)) }
                }
            }
        }
    }
}

@Composable
private fun CatalogCard(item: CatalogItem, onDownload: () -> Unit, onCancel: (ImportTask) -> Unit) {
    val entry = item.entry
    val task = item.task
    // The ring replaces Download at the tap, before the queued task shows up, so a double tap cannot queue it twice.
    var requested by remember { mutableStateOf(false) }
    LaunchedEffect(requested, task != null) {
        if (task != null) {
            requested = false
        } else if (requested) {
            delay(REQUEST_SHOWN_MS)
            requested = false
        }
    }
    // ✕ takes the place of Download, so the second tap of a double tap would cancel at once; it waits a moment first.
    var cancelFrom by remember { mutableLongStateOf(0L) }
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
                    listOf(languages, "${entry.sizeMb} MB", entry.license)
                        .filter { it.isNotEmpty() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(entry.description(), style = MaterialTheme.typography.bodySmall)
                if (task != null || requested) {
                    Text(taskStage(task), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            when {
                task != null || requested -> TaskRing(
                    task,
                    onCancel = { if (task != null && SystemClock.uptimeMillis() >= cancelFrom) onCancel(task) },
                )
                item.installed -> Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.dictionaries_installed_mark),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp),
                )
                else -> TooltipIconButton(
                    R.drawable.ic_download,
                    stringResource(R.string.dictionaries_download),
                    onClick = {
                        cancelFrom = SystemClock.uptimeMillis() + CANCEL_GUARD_MS
                        requested = true
                        onDownload()
                    },
                )
            }
        }
    }
}

/** Taps on a catalog card's ✕ within this time after its Download was tapped are ignored. */
private const val CANCEL_GUARD_MS = 1_000L

/** How long a tapped Download shows the ring while its task has not shown up; then Download comes back. */
private const val REQUEST_SHOWN_MS = 5_000L

/** What a catalog card's import is doing, with the percent when it is known; null is a download just requested. */
@Composable
private fun taskStage(task: ImportTask?): String {
    if (task == null) return stringResource(R.string.dictionaries_stage_queued)
    val stage = stringResource(
        when (task.state) {
            ImportTask.State.QUEUED -> R.string.dictionaries_stage_queued
            ImportTask.State.DOWNLOADING -> R.string.dictionaries_stage_downloading
            ImportTask.State.CHECKING_SPACE -> R.string.dictionaries_stage_checking_space
            ImportTask.State.CONVERTING -> R.string.dictionaries_stage_converting
            else -> R.string.dictionaries_stage_importing
        },
    )
    val percent = task.percent
    return if (percent != null && task.state != ImportTask.State.QUEUED) {
        stringResource(R.string.dictionaries_stage_percent, stage, percent)
    } else {
        stage
    }
}

/** The import's progress around a ✕ that cancels it; see [ringProgress]. Empty for a download just requested. */
@Composable
private fun TaskRing(task: ImportTask?, onCancel: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
        val progress = if (task == null) 0f else ringProgress(task)
        val ring = Modifier.size(40.dp)
        val track = MaterialTheme.colorScheme.outlineVariant
        if (progress != null) {
            CircularProgressIndicator(progress = { progress }, modifier = ring, strokeWidth = 3.dp, trackColor = track, color = AccentDefaults.progress)
        } else {
            CircularProgressIndicator(modifier = ring, strokeWidth = 3.dp, trackColor = track, color = AccentDefaults.progress)
        }
        TooltipIconButton(stringResource(R.string.action_cancel), onClick = onCancel) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_cancel), modifier = Modifier.size(18.dp))
        }
    }
}

/** The filled part of an import's ring: empty while queued, the percent when known, null (spinning) otherwise. */
internal fun ringProgress(task: ImportTask): Float? =
    if (task.state == ImportTask.State.QUEUED) 0f else task.percent?.let { it.coerceIn(0, 100) / 100f }

private val ARCHIVE_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
