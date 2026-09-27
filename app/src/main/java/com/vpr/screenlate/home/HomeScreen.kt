package com.vpr.screenlate.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.anki.label
import com.vpr.screenlate.core.anki.message
import com.vpr.screenlate.overlay.OverlayServiceStatus
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.LabelWithInfo
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.update.UpdateAnnouncementCard
import com.vpr.screenlate.update.UpdateViewModel

/** Search, the accessibility service, and whatever broke; everything else is under Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDictionaries: () -> Unit,
    onOpenAnki: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    updates: UpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val dictionaries by viewModel.dictionaries.collectAsStateWithLifecycle()
    val problems by viewModel.problems.collectAsStateWithLifecycle()
    var serviceEnabled by remember { mutableStateOf(OverlayServiceStatus.isEnabled(context)) }
    LaunchedEffect(Unit) { updates.checkIfDue() }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = OverlayServiceStatus.isEnabled(context)
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UpdateAnnouncementCard(updates)
            if (problems.isNotEmpty()) ProblemsCard(problems, viewModel, onOpenDictionaries, onOpenAnki)

            SectionCard(title = stringResource(R.string.home_search_title)) {
                Button(onClick = onOpenSearch, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_search_open))
                }
            }

            SectionCard(title = stringResource(R.string.onboarding_service_title)) {
                Text(
                    text = stringResource(
                        if (serviceEnabled) R.string.onboarding_service_enabled else R.string.onboarding_service_disabled,
                    ),
                    color = if (serviceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (!serviceEnabled) Text(stringResource(R.string.onboarding_service_explanation))
                if (dictionaries.importing) Hint(stringResource(R.string.home_dictionaries_installing))
                if (serviceEnabled) {
                    OutlinedButton(
                        onClick = { context.startActivity(OverlayServiceStatus.accessibilitySettingsIntent()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_open_accessibility_settings)) }
                } else {
                    Button(
                        onClick = { context.startActivity(OverlayServiceStatus.accessibilitySettingsIntent()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_open_accessibility_settings)) }
                }
                HorizontalDivider()
                LabelWithInfo(
                    stringResource(R.string.onboarding_device_title),
                    stringResource(R.string.onboarding_device_app_launch) + "\n\n" +
                        stringResource(R.string.onboarding_device_restricted_settings),
                )
            }

            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = null)
                Text(stringResource(R.string.settings_title), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ProblemsCard(
    problems: List<HomeProblem>,
    viewModel: HomeViewModel,
    onOpenDictionaries: () -> Unit,
    onOpenAnki: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.problems_title)) {
        problems.forEachIndexed { index, problem ->
            if (index > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painterResource(R.drawable.ic_warning),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (problem) {
                        is HomeProblem.Anki -> {
                            Text(stringResource(problem.problem.message))
                            TextButton(onClick = onOpenAnki) { Text(stringResource(R.string.problems_open_anki)) }
                        }
                        is HomeProblem.MissingDictionary -> {
                            Text(stringResource(R.string.problems_dictionary_missing, problem.dictionary.title))
                            Row {
                                if (problem.catalogEntry != null) {
                                    TextButton(onClick = { viewModel.downloadAgain(problem) }) {
                                        Text(stringResource(R.string.problems_download_again))
                                    }
                                } else {
                                    Hint(stringResource(R.string.problems_import_again), modifier = Modifier.weight(1f))
                                }
                                TextButton(onClick = { viewModel.remove(problem) }) {
                                    Text(stringResource(R.string.problems_remove))
                                }
                            }
                        }
                        HomeProblem.NoTermDictionaries -> {
                            Text(stringResource(R.string.problems_no_dictionaries))
                            TextButton(onClick = onOpenDictionaries) { Text(stringResource(R.string.home_dictionaries_open)) }
                        }
                        is HomeProblem.AudioSource -> {
                            Text(
                                stringResource(
                                    R.string.problems_audio_source,
                                    stringResource(problem.failure.source.type.label),
                                    problem.failure.reason,
                                ),
                            )
                            Row {
                                TextButton(onClick = onOpenAnki) { Text(stringResource(R.string.problems_open_audio)) }
                                TextButton(onClick = viewModel::dismissAudioFailures) {
                                    Text(stringResource(R.string.problems_dismiss))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
