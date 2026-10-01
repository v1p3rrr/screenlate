package com.vpr.screenlate.update

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.InfoDialog
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SwitchRow
import com.vpr.screenlate.ui.theme.AccentDefaults

/** The announcement of a new version on the home screen; shown once per version. */
@Composable
fun UpdateAnnouncementCard(viewModel: UpdateViewModel) {
    val release by viewModel.announcement.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shown = release ?: return
    SectionCard(title = stringResource(R.string.update_available_title, shown.tag.removePrefix("v"))) {
        ReleaseNotes(shown)
        UpdateProgress(shown, state, viewModel)
        if (state !is UpdateState.Downloading && state !is UpdateState.Installing) {
            TextButton(onClick = viewModel::dismissAnnouncement) { Text(stringResource(R.string.update_skip)) }
        }
    }
}

/** Updates in About: current state, manual check and the announcement setting. */
@Composable
fun AboutUpdateCard(viewModel: UpdateViewModel, currentVersion: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SectionCard(title = stringResource(R.string.update_title)) {
        if (!viewModel.supported) {
            Hint(stringResource(R.string.update_debug_build))
            return@SectionCard
        }
        Hint(stringResource(R.string.update_current, currentVersion))
        when (val current = state) {
            UpdateState.Idle -> Unit
            UpdateState.Checking -> Text(stringResource(R.string.update_checking))
            UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date, currentVersion))
            is UpdateState.Available -> {
                Text(stringResource(R.string.update_available_title, current.release.tag.removePrefix("v")))
                ReleaseNotes(current.release)
            }
            else -> Unit
        }
        val release = when (val current = state) {
            is UpdateState.Available -> current.release
            is UpdateState.Downloading -> current.release
            is UpdateState.Installing -> current.release
            is UpdateState.Failed -> current.release
            else -> null
        }
        if (release != null) {
            UpdateProgress(release, state, viewModel)
        } else if (state is UpdateState.Failed) {
            ErrorText(errorText((state as UpdateState.Failed).error))
        }
        if (release == null || state is UpdateState.Available) {
            OutlinedButton(
                onClick = viewModel::check,
                enabled = state != UpdateState.Checking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.update_check), textAlign = TextAlign.Center) }
        }
        settings?.let {
            SwitchRow(
                stringResource(R.string.update_announce),
                it.announce,
                viewModel::setAnnounce,
                hint = stringResource(R.string.update_announce_hint),
            )
        }
    }
}

/** "What's new" as a button: release notes can be long. */
@Composable
private fun ReleaseNotes(release: Release) {
    val notes = remember(release) { Releases.notes(release) }
    if (notes.isEmpty()) return
    var open by rememberSaveable(release.tag) { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.update_whats_new), textAlign = TextAlign.Center)
    }
    if (open) {
        InfoDialog(stringResource(R.string.update_available_title, release.tag.removePrefix("v")), onDismiss = { open = false }) {
            Text(notes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The update button, the permission to install, download progress and errors for [release]. */
@Composable
private fun UpdateProgress(release: Release, state: UpdateState, viewModel: UpdateViewModel) {
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(viewModel.canInstall()) }
    // The user may come back from the system setting that allows installs.
    LifecycleResumeEffect(Unit) {
        canInstall = viewModel.canInstall()
        onPauseOrDispose { }
    }
    when (state) {
        is UpdateState.Downloading -> {
            Text(stringResource(R.string.update_downloading, (state.fraction * 100).toInt()))
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth(), color = AccentDefaults.progress)
        }
        is UpdateState.Installing -> {
            Text(stringResource(R.string.update_installing))
            state.confirmation?.let { prompt ->
                OutlinedButton(onClick = { viewModel.confirm(prompt) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_confirm_install), textAlign = TextAlign.Center)
                }
            }
        }
        else -> {
            if (state is UpdateState.Failed && state.release == release) ErrorText(errorText(state.error))
            if (!canInstall) {
                Hint(stringResource(R.string.update_allow_installs_hint))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.update_allow_installs), textAlign = TextAlign.Center) }
            }
            Button(onClick = { viewModel.update(release) }, enabled = canInstall, modifier = Modifier.fillMaxWidth(), colors = AccentDefaults.buttonColors()) {
                Text(
                    stringResource(if (state is UpdateState.Failed) R.string.update_retry else R.string.update_install),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun errorText(error: UpdateError): String = stringResource(
    when (error) {
        UpdateError.NETWORK -> R.string.update_error_network
        UpdateError.NO_APK -> R.string.update_error_no_apk
        UpdateError.BAD_APK -> R.string.update_error_bad_apk
        UpdateError.INSTALL_FAILED -> R.string.update_error_install
    },
)

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
