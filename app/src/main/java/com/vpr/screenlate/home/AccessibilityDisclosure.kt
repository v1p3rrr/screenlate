package com.vpr.screenlate.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.overlay.OverlayServiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Before the accessibility settings open, a dialog says what the service reads and where it goes. "Agree" saves the
 * agreement and opens them; once agreed, the settings open at once, until a full settings reset. About shows the same
 * text without the agreement.
 */

@HiltViewModel
class AccessibilityDisclosureViewModel @Inject constructor(private val settings: AppSettingsRepository) : ViewModel() {
    val agreed: StateFlow<Boolean?> =
        settings.accessibilityAgreed.stateIn(viewModelScope, SharingStarted.Eagerly, settings.cachedAccessibilityAgreed)

    /** Survives the screen going to the background as the settings open. */
    fun agree() {
        viewModelScope.launch { withContext(NonCancellable) { settings.setAccessibilityAgreed() } }
    }
}

/** Opens the accessibility settings, after [AccessibilityDisclosureDialog] while the user has not agreed to it. */
@Composable
fun rememberOpenAccessibilitySettings(viewModel: AccessibilityDisclosureViewModel = hiltViewModel()): () -> Unit {
    val context = LocalContext.current
    val agreed by viewModel.agreed.collectAsStateWithLifecycle()
    val currentAgreed by rememberUpdatedState(agreed)
    var asking by rememberSaveable { mutableStateOf(false) }
    if (asking) {
        AccessibilityDisclosureDialog(
            onDismiss = { asking = false },
            onAgree = {
                asking = false
                viewModel.agree()
                OverlayServiceStatus.openAccessibilitySettings(context)
            },
        )
    }
    return remember(context) {
        {
            if (currentAgreed == true) OverlayServiceStatus.openAccessibilitySettings(context) else asking = true
        }
    }
}

/**
 * What the accessibility service reads and where it goes. With [onAgree] it asks for the agreement ("Not now" and a
 * tap outside only close it); without, it only shows the text.
 */
@Composable
fun AccessibilityDisclosureDialog(onDismiss: () -> Unit, onAgree: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accessibility_disclosure_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.accessibility_disclosure_intro))
                Text(stringResource(R.string.accessibility_disclosure_reads_title), style = MaterialTheme.typography.titleSmall)
                Bullets(stringResource(R.string.accessibility_disclosure_reads))
                Text(stringResource(R.string.accessibility_disclosure_sends_title), style = MaterialTheme.typography.titleSmall)
                Bullets(stringResource(R.string.accessibility_disclosure_sends))
                Text(stringResource(R.string.accessibility_disclosure_outro))
            }
        },
        confirmButton = {
            if (onAgree != null) {
                TextButton(onClick = onAgree) { Text(stringResource(R.string.accessibility_disclosure_agree)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = onAgree?.let {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.accessibility_disclosure_later)) } }
        },
    )
}

/** Lines of [text] that start with "• ", each wrapped under its own text rather than under the bullet. */
@Composable
private fun Bullets(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.lines().forEach { line ->
            Row {
                Text(BULLET)
                Text(line.removePrefix(BULLET))
            }
        }
    }
}

private const val BULLET = "• "
