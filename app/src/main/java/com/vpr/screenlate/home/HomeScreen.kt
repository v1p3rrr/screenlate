package com.vpr.screenlate.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.audio.LocalNetworkAllowButton
import com.vpr.screenlate.audio.audioErrorText
import com.vpr.screenlate.audio.localNetworkMissing
import com.vpr.screenlate.background.rememberBackgroundTipBadge
import com.vpr.screenlate.core.anki.label
import com.vpr.screenlate.core.anki.message
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.dictionaries.rememberImportNotificationsAsk
import com.vpr.screenlate.overlay.OverlayServiceStatus
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.LabelWithInfo
import com.vpr.screenlate.settings.EInkHint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.theme.AccentDefaults
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
    onOpenAppText: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    updates: UpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val dictionaries by viewModel.dictionaries.collectAsStateWithLifecycle()
    val problems by viewModel.problems.collectAsStateWithLifecycle()
    var serviceEnabled by remember { mutableStateOf(OverlayServiceStatus.isEnabled(context)) }
    val serviceRunning by OverlayServiceStatus.running.collectAsStateWithLifecycle()
    val bubbleVisible by viewModel.bubbleVisible.collectAsStateWithLifecycle()
    val resetGaveUpUntold by viewModel.resetGaveUpUntold.collectAsStateWithLifecycle()
    val settingsBadge = rememberBackgroundTipBadge()
    val openAccessibilitySettings = rememberOpenAccessibilitySettings()
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
                    TooltipIconButton(stringResource(R.string.settings_title), onClick = onOpenSettings) {
                        BadgedBox(badge = { if (settingsBadge) Badge() }) {
                            Icon(
                                painterResource(R.drawable.ic_settings),
                                stringResource(R.string.settings_title) +
                                    if (settingsBadge) ", " + stringResource(R.string.settings_badge) else "",
                            )
                        }
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
            EInkHint(onOpenAppText)
            if (resetGaveUpUntold) {
                ResetGaveUpDialog(
                    onOpen = {
                        viewModel.markResetGaveUpTold()
                        onOpenDictionaries()
                    },
                    onClose = viewModel::markResetGaveUpTold,
                )
            }
            if (problems.isNotEmpty()) ProblemsCard(problems, viewModel, onOpenDictionaries, onOpenAnki)

            SectionCard(title = stringResource(R.string.home_search_title)) {
                Button(onClick = onOpenSearch, modifier = Modifier.fillMaxWidth(), colors = AccentDefaults.buttonColors()) {
                    Text(stringResource(R.string.home_search_open), textAlign = TextAlign.Center)
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
                    HorizontalDivider()
                    BubbleControls(serviceRunning, bubbleVisible, viewModel::setBubbleVisible, openAccessibilitySettings)
                    OutlinedButton(
                        onClick = openAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_open_accessibility_settings), textAlign = TextAlign.Center) }
                } else {
                    Button(
                        onClick = openAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = AccentDefaults.buttonColors(),
                    ) { Text(stringResource(R.string.onboarding_open_accessibility_settings), textAlign = TextAlign.Center) }
                }
                HorizontalDivider()
                LabelWithInfo(
                    stringResource(R.string.onboarding_device_title),
                    stringResource(R.string.onboarding_device_restricted_settings),
                )
            }

            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                BadgedBox(badge = { if (settingsBadge) Badge() }) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = null)
                }
                Text(stringResource(R.string.settings_title), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** What the home screen tells about the bubble. A service the phone stopped wins over the user's own hiding. */
internal enum class BubbleState { SHOWN, HIDDEN, STOPPED }

internal fun bubbleState(serviceRunning: Boolean, visible: Boolean): BubbleState = when {
    !serviceRunning -> BubbleState.STOPPED
    visible -> BubbleState.SHOWN
    else -> BubbleState.HIDDEN
}

/**
 * The bubble's state and the button that changes it. With the service stopped by the phone the button can only open
 * the accessibility settings: an app may not turn its own accessibility service back on.
 */
@Composable
private fun BubbleControls(
    serviceRunning: Boolean,
    visible: Boolean,
    onVisible: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val state = bubbleState(serviceRunning, visible)
    Text(
        text = stringResource(
            when (state) {
                BubbleState.STOPPED -> R.string.home_bubble_stopped
                BubbleState.SHOWN -> R.string.home_bubble_shown
                BubbleState.HIDDEN -> R.string.home_bubble_hidden
            },
        ),
        color = when (state) {
            BubbleState.STOPPED -> MaterialTheme.colorScheme.error
            BubbleState.SHOWN -> MaterialTheme.colorScheme.primary
            BubbleState.HIDDEN -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    if (state == BubbleState.STOPPED) Hint(stringResource(R.string.home_bubble_stopped_hint))
    if (state == BubbleState.SHOWN) {
        OutlinedButton(onClick = { onVisible(false) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_bubble_hide), textAlign = TextAlign.Center)
        }
    } else {
        Button(
            onClick = {
                if (state == BubbleState.HIDDEN) onVisible(true) else onOpenAccessibilitySettings()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = AccentDefaults.buttonColors(),
        ) { Text(stringResource(R.string.home_bubble_show), textAlign = TextAlign.Center) }
    }
}

@Composable
private fun ProblemsCard(
    problems: List<HomeProblem>,
    viewModel: HomeViewModel,
    onOpenDictionaries: () -> Unit,
    onOpenAnki: () -> Unit,
) {
    val askNotifications = rememberImportNotificationsAsk()
    val removeError by viewModel.removeError.collectAsStateWithLifecycle()
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
                        is HomeProblem.MissingDictionaries -> {
                            Text(stringResource(R.string.problems_dictionaries_missing))
                            problem.dictionaries.forEach { missing ->
                                Text("• " + missing.dictionary.title, style = MaterialTheme.typography.bodyMedium)
                                Row {
                                    if (missing.catalogEntry != null) {
                                        TextButton(onClick = {
                                            askNotifications()
                                            viewModel.downloadAgain(missing)
                                        }) {
                                            Text(stringResource(R.string.problems_download_again))
                                        }
                                    } else {
                                        Hint(stringResource(R.string.problems_import_again), modifier = Modifier.weight(1f))
                                    }
                                    TextButton(onClick = { viewModel.remove(missing) }) {
                                        Text(stringResource(R.string.problems_remove))
                                    }
                                }
                            }
                            removeError?.let { error ->
                                Text(
                                    stringResource(R.string.dictionaries_delete_failed, error),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        is HomeProblem.NoTermDictionaries -> {
                            if (problem.several) {
                                problem.languages.forEach { language ->
                                    Text(stringResource(R.string.problems_no_dictionaries_language, language.displayName()))
                                }
                            } else {
                                Text(stringResource(R.string.problems_no_dictionaries))
                            }
                            TextButton(onClick = onOpenDictionaries) { Text(stringResource(R.string.home_dictionaries_open)) }
                        }
                        is HomeProblem.AudioSources -> {
                            Text(stringResource(R.string.problems_audio_sources))
                            problem.failures.forEach { failure ->
                                val name = stringResource(failure.source.type.label)
                                Text(
                                    stringResource(
                                        R.string.problems_audio_source_line,
                                        failure.source.address?.let { "$name ($it)" } ?: name,
                                        audioErrorText(failure.error, failure.source),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (problem.failures.any { localNetworkMissing(LocalContext.current, it.source.url) }) LocalNetworkAllowButton()
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

/** Told once: a dictionary reset was given up; the Dictionaries page keeps a card until it is closed or resolved. */
@Composable
private fun ResetGaveUpDialog(onOpen: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.reset_dictionaries_gave_up_title)) },
        text = {
            Text(
                stringResource(R.string.reset_dictionaries_gave_up_text),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.reset_dictionaries_gave_up_open)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
    )
}
