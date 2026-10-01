package com.vpr.screenlate.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.bubble.BubbleSettingsViewModel
import com.vpr.screenlate.dictionaries.rememberImportNotificationsAsk
import com.vpr.screenlate.dictionaries.rememberNotificationsPermission
import com.vpr.screenlate.overlay.BubbleKeepAliveService
import com.vpr.screenlate.settings.SectionResetButton
import com.vpr.screenlate.settings.SettingsSection
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.SwitchRow

/** Battery optimization, the phone's own startup settings and the keep-alive notification, which all keep the bubble alive. */
@Composable
fun BackgroundWorkScreen(
    onBack: () -> Unit,
    tip: BackgroundTipViewModel = hiltViewModel(),
    bubble: BubbleSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val bubbleSettings by bubble.settings.collectAsStateWithLifecycle()
    // The service posts its notification when it starts, and Android does not show one posted before the permission
    // was granted, so the switch is written only after the answer.
    val askNotifications = rememberImportNotificationsAsk(onAnswered = { bubble.setKeepAlive(true) })
    val notifications = rememberNotificationsPermission(
        onGranted = { if (bubbleSettings.keepAlive) BubbleKeepAliveService.repost(context) },
    )
    var batteryExempt by remember { mutableStateOf(BackgroundSettings.isBatteryExempt(context)) }
    val startupScreen = remember { BackgroundSettings.startupScreen(context) }
    LaunchedEffect(Unit) { tip.markSeen() }
    LifecycleResumeEffect(Unit) {
        batteryExempt = BackgroundSettings.isBatteryExempt(context)
        onPauseOrDispose { }
    }

    SettingsScaffold(
        stringResource(R.string.background_title),
        onBack,
        actions = { SectionResetButton(SettingsSection.BACKGROUND) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hint(stringResource(R.string.background_intro))
            SectionCard(title = stringResource(R.string.background_battery_title)) {
                Text(
                    stringResource(if (batteryExempt) R.string.background_battery_off else R.string.background_battery_on),
                    color = if (batteryExempt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (!batteryExempt) {
                    Button(
                        onClick = {
                            BackgroundSettings.open(
                                context,
                                BackgroundSettings.batteryDialog(context),
                                BackgroundSettings.batteryList(),
                                BackgroundSettings.appInfo(context),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.background_battery_allow), textAlign = TextAlign.Center) }
                }
                OutlinedButton(
                    onClick = {
                        BackgroundSettings.open(context, BackgroundSettings.batteryList(), BackgroundSettings.appInfo(context))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.background_battery_list), textAlign = TextAlign.Center) }
                Hint(stringResource(R.string.background_battery_list_hint))
            }
            SectionCard(title = stringResource(R.string.background_launch_title)) {
                Text(stringResource(R.string.background_launch_text))
                val component = startupScreen?.component
                if (component != null && StartupScreens.isAppLaunch(component.packageName, component.className)) {
                    Text(stringResource(R.string.background_launch_path))
                }
                OutlinedButton(
                    onClick = { BackgroundSettings.open(context, startupScreen, BackgroundSettings.appInfo(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (startupScreen != null) R.string.background_launch_open else R.string.background_launch_app_info,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
                if (startupScreen == null) Hint(stringResource(R.string.background_launch_app_info_hint))
            }
            SectionCard(title = stringResource(R.string.background_keep_alive_title)) {
                Text(stringResource(R.string.background_keep_alive_text))
                SwitchRow(
                    label = stringResource(R.string.background_keep_alive_switch),
                    checked = bubbleSettings.keepAlive,
                    onChange = { enabled ->
                        if (!enabled || !askNotifications()) bubble.setKeepAlive(enabled)
                        // The running service stops it too, but after the phone killed the app the system may restart
                        // the notification without the service.
                        if (!enabled) BubbleKeepAliveService.keepAlive(context, false)
                    },
                    hint = stringResource(R.string.background_keep_alive_hint),
                )
                if (bubbleSettings.keepAlive && !notifications.allowed) {
                    Text(
                        stringResource(R.string.background_keep_alive_notifications_off),
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = notifications.request, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.background_keep_alive_notifications_allow), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
