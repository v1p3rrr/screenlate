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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SettingsScaffold

/** Battery optimization and the phone maker's startup settings, which can stop the bubble in the background. */
@Composable
fun BackgroundWorkScreen(onBack: () -> Unit, tip: BackgroundTipViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var batteryExempt by remember { mutableStateOf(BackgroundSettings.isBatteryExempt(context)) }
    val startupScreen = remember { BackgroundSettings.startupScreen(context) }
    LaunchedEffect(Unit) { tip.markSeen() }
    LifecycleResumeEffect(Unit) {
        batteryExempt = BackgroundSettings.isBatteryExempt(context)
        onPauseOrDispose { }
    }

    SettingsScaffold(stringResource(R.string.background_title), onBack) { padding ->
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
                    ) { Text(stringResource(R.string.background_battery_allow)) }
                }
                OutlinedButton(
                    onClick = {
                        BackgroundSettings.open(context, BackgroundSettings.batteryList(), BackgroundSettings.appInfo(context))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.background_battery_list)) }
                Hint(stringResource(R.string.background_battery_list_hint))
            }
            SectionCard(title = stringResource(R.string.background_launch_title)) {
                Text(stringResource(R.string.background_launch_text))
                OutlinedButton(
                    onClick = { BackgroundSettings.open(context, startupScreen, BackgroundSettings.appInfo(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (startupScreen != null) R.string.background_launch_open else R.string.background_launch_app_info,
                        ),
                    )
                }
                if (startupScreen == null) Hint(stringResource(R.string.background_launch_app_info_hint))
            }
        }
    }
}
