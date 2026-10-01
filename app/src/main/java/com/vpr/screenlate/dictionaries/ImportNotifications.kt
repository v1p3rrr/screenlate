package com.vpr.screenlate.dictionaries

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
 * Imports and downloads show their progress in a foreground notification, which Android 13+ shows only with the
 * notification permission; so does the bubble's keep-alive service. The first import, download or keep-alive switch
 * asks for it once; the Dictionaries screen and the keep-alive card offer it again while it is missing. Every request
 * comes after a dialog that says what the permission is for.
 */

@HiltViewModel
class ImportNotificationsViewModel @Inject constructor(private val settings: AppSettingsRepository) : ViewModel() {
    val asked: StateFlow<Boolean?> = settings.notificationPermissionAsked
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markAsked() {
        viewModelScope.launch { settings.setNotificationPermissionAsked() }
    }
}

/**
 * Call when something that shows a notification starts: asks for the notification permission the first time. The
 * returned function tells whether it asked; [onAnswered] runs once the user has answered.
 */
@Composable
fun rememberImportNotificationsAsk(
    viewModel: ImportNotificationsViewModel = hiltViewModel(),
    onAnswered: () -> Unit = {},
): () -> Boolean {
    val context = LocalContext.current
    val asked by viewModel.asked.collectAsStateWithLifecycle()
    val currentAsked by rememberUpdatedState(asked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAnswered() }
    val explain = rememberNotificationsExplanation()
    return remember(launcher, explain) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && currentAsked == false && !notificationsAllowed(context)) {
                explain {
                    viewModel.markAsked()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                true
            } else {
                false
            }
        }
    }
}

/** Whether notifications are allowed, re-read when the screen resumes, and a way to ask for them. */
class NotificationsPermission(val allowed: Boolean, val request: () -> Unit)

/**
 * The notification permission for a screen that offers it. Once the system no longer shows its request (declined
 * twice), [NotificationsPermission.request] opens the app's notification settings instead. [onGranted] runs when the
 * permission turns on while the screen is shown, from the request or from the settings.
 */
@Composable
fun rememberNotificationsPermission(
    viewModel: ImportNotificationsViewModel = hiltViewModel(),
    onGranted: () -> Unit = {},
): NotificationsPermission {
    val context = LocalContext.current
    val asked by viewModel.asked.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    val currentOnGranted by rememberUpdatedState(onGranted)
    val update = { now: Boolean ->
        if (now && !allowed) currentOnGranted()
        allowed = now
    }
    LifecycleResumeEffect(Unit) {
        update(notificationsAllowed(context))
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { update(it) }
    val currentAsked by rememberUpdatedState(asked)
    val explain = rememberNotificationsExplanation()
    return NotificationsPermission(allowed) request@{
        // Before Android 13 notifications need no permission, so nothing offers this request there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@request
        explain {
            val activity = context.findActivity()
            val canAsk = currentAsked != true || (
                activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
                )
            viewModel.markAsked()
            if (canAsk) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
    }
}

/**
 * A function that shows what the notification permission is for and then runs the action given to it, the request.
 * The dialog has one button; Back and a tap outside close it the same way, so the request always follows.
 */
@Composable
private fun rememberNotificationsExplanation(): (() -> Unit) -> Unit {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    pending?.let { action ->
        val done = {
            pending = null
            action()
        }
        AlertDialog(
            onDismissRequest = done,
            title = { Text(stringResource(R.string.notifications_explain_title)) },
            text = {
                Text(
                    stringResource(R.string.notifications_explain_text, stringResource(R.string.background_title)),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = done) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    return remember { { action -> pending = action } }
}

/** Offers the notification permission while it is missing. */
@Composable
fun ImportNotificationsCard(viewModel: ImportNotificationsViewModel = hiltViewModel()) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val notifications = rememberNotificationsPermission(viewModel)
    if (notifications.allowed) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.dictionaries_notifications_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.dictionaries_notifications_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            TextButton(onClick = notifications.request, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.dictionaries_notifications_allow))
            }
        }
    }
}

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
