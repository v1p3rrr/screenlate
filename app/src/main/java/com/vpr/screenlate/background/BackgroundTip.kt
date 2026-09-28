package com.vpr.screenlate.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * From the first start, a badge on the settings button and on Background work points to the background settings,
 * until the user opens that screen or leaves the settings list with the badge shown. It appears only while there is
 * something to change: battery optimization is on, or the phone has its maker's startup settings.
 */

@HiltViewModel
class BackgroundTipViewModel @Inject constructor(private val settings: AppSettingsRepository) : ViewModel() {
    val seen: StateFlow<Boolean?> = settings.backgroundTipSeen.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Survives the screen closing right after the call. */
    fun markSeen() {
        viewModelScope.launch { withContext(NonCancellable) { settings.setBackgroundTipSeen() } }
    }
}

/** Whether the background work tip is badged now. */
@Composable
fun rememberBackgroundTipBadge(viewModel: BackgroundTipViewModel = hiltViewModel()): Boolean {
    val context = LocalContext.current
    val seen by viewModel.seen.collectAsStateWithLifecycle()
    val hasStartupScreen = remember { BackgroundSettings.startupScreen(context) != null }
    var batteryExempt by remember { mutableStateOf(BackgroundSettings.isBatteryExempt(context)) }
    LifecycleResumeEffect(Unit) {
        batteryExempt = BackgroundSettings.isBatteryExempt(context)
        onPauseOrDispose { }
    }
    return seen == false && (!batteryExempt || hasStartupScreen)
}
