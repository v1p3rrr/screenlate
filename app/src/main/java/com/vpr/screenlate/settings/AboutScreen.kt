package com.vpr.screenlate.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.logs.LogExport
import com.vpr.screenlate.logs.SavedLog
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.InfoButton
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.update.AboutUpdateCard
import com.vpr.screenlate.update.UpdateViewModel
import kotlinx.coroutines.launch

const val SOURCE_URL = "https://github.com/v1p3rrr/screenlate"

/** Version and updates, source code, licenses, dictionary attributions, logs and developer tools. */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenOcrTest: () -> Unit,
    onOpenLibraries: () -> Unit,
    onOpenNotices: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
    updates: UpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictionaries by viewModel.dictionaries.collectAsStateWithLifecycle()
    var sharing by remember { mutableStateOf(false) }
    var savedLog by remember { mutableStateOf<SavedLog?>(null) }
    var saveFailed by remember { mutableStateOf(false) }
    var showDictionaries by rememberSaveable { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.about_logs_share)
    SettingsScaffold(stringResource(R.string.about_title), onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.app_title)) {
                Text(stringResource(R.string.about_version, versionName(context)))
                Hint(stringResource(R.string.about_license))
                OutlinedButton(onClick = { openUrl(context, SOURCE_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_source))
                }
                OutlinedButton(onClick = { openUrl(context, "$SOURCE_URL/issues") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_report_bug))
                }
            }
            AboutUpdateCard(updates, versionName(context))
            SectionCard(title = stringResource(R.string.about_licenses)) {
                Hint(stringResource(R.string.about_licenses_hint))
                OutlinedButton(onClick = onOpenLibraries, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_libraries))
                }
                OutlinedButton(onClick = onOpenNotices, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_notices))
                }
            }
            if (dictionaries.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.about_dictionaries)) {
                    Hint(stringResource(R.string.about_dictionaries_hint))
                    if (showDictionaries) {
                        dictionaries.forEachIndexed { index, dictionary ->
                            if (index > 0) HorizontalDivider()
                            DictionaryAttribution(dictionary)
                        }
                    }
                    TextButton(onClick = { showDictionaries = !showDictionaries }) {
                        Text(
                            if (showDictionaries) {
                                stringResource(R.string.about_dictionaries_hide)
                            } else {
                                stringResource(R.string.about_dictionaries_show, dictionaries.size)
                            },
                        )
                    }
                }
            }
            SectionCard(title = stringResource(R.string.about_logs)) {
                Hint(stringResource(R.string.about_logs_hint))
                OutlinedButton(
                    onClick = {
                        sharing = true
                        scope.launch {
                            runCatching { viewModel.logShareIntent() }.onSuccess { intent ->
                                context.startActivity(Intent.createChooser(intent, shareTitle))
                            }
                            sharing = false
                        }
                    },
                    enabled = !sharing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.about_logs_share)) }
                OutlinedButton(
                    onClick = {
                        sharing = true
                        scope.launch {
                            val result = runCatching { viewModel.saveLog() }
                            savedLog = result.getOrNull()
                            saveFailed = result.isFailure
                            sharing = false
                        }
                    },
                    enabled = !sharing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.about_logs_save)) }
                savedLog?.let { log ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Hint(stringResource(R.string.about_logs_saved, log.path), modifier = Modifier.weight(1f))
                        TextButton(onClick = { runCatching { context.startActivity(LogExport.viewIntent(log)) } }) {
                            Text(stringResource(R.string.about_logs_open))
                        }
                    }
                }
                if (saveFailed) Hint(stringResource(R.string.about_logs_save_failed))
            }
            SectionCard(title = stringResource(R.string.home_tools_title)) {
                OutlinedButton(onClick = onOpenOcrTest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ocr_test_title))
                }
            }
        }
    }
}

/** The dictionary's title; author, license text and link from its index.json open from the ⓘ button. */
@Composable
private fun DictionaryAttribution(dictionary: DictionaryEntity) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(dictionary.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        InfoButton(dictionary.title) {
            listOfNotNull(
                dictionary.revision.takeIf { it.isNotBlank() }?.let { stringResource(R.string.about_dictionary_revision, it) },
                dictionary.author?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.about_dictionary_author, it) },
                dictionary.attribution?.takeIf { it.isNotBlank() }?.let(::plainText),
                dictionary.description?.takeIf { it.isNotBlank() && it != dictionary.attribution }?.let(::plainText),
            ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            dictionary.url?.takeIf { it.startsWith("http") }?.let { url ->
                Text(
                    url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { openUrl(context, url) },
                )
            }
        }
    }
}

/** Some dictionaries write line breaks in index.json as a literal backslash and n. */
private fun plainText(text: String): String = text.replace("\\n", "\n").trim()

fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
