package com.vpr.screenlate.audio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.network.LocalNetwork
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Android 17 needs a runtime permission for devices on the local network, e.g. an audio server at home. */
private const val LOCAL_NETWORK_SDK = 37

/** Whether the app may reach devices on the local network; always true before Android 17. */
fun localNetworkAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < LOCAL_NETWORK_SDK ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/** Whether [url] points to the local network while the permission for it is missing. */
fun localNetworkMissing(context: Context, url: String): Boolean = LocalNetwork.isLocalUrl(url) && !localNetworkAllowed(context)

@HiltViewModel
class LocalNetworkViewModel @Inject constructor(repository: AudioSettingsRepository) : ViewModel() {
    /** URL templates of every language's audio sources on the local network. */
    val localSources: StateFlow<List<String>?> = combine(Language.entries.map { repository.settings(it) }) { all ->
        all.flatMap { settings -> settings.sources.filter { it.type.hasUrl && LocalNetwork.isLocalUrl(it.url) }.map { it.url } }.distinct()
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Asks for the local network permission when an audio source on the local network appears while the permission is
 * missing: after adding one, importing Yomitan settings, restoring a backup, or on the first start after an update.
 * Each source is asked about once per process; the system stops showing the request after two refusals.
 */
@Composable
fun LocalNetworkAskEffect(viewModel: LocalNetworkViewModel = hiltViewModel()) {
    if (Build.VERSION.SDK_INT < LOCAL_NETWORK_SDK) return
    val context = LocalContext.current
    val urls by viewModel.localSources.collectAsStateWithLifecycle()
    var asked by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(urls) {
        val fresh = urls.orEmpty() - asked.toSet()
        if (fresh.isEmpty() || localNetworkAllowed(context)) return@LaunchedEffect
        asked = asked + fresh
        launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
}

/** Call before testing a source that is not saved yet: asks for the permission when [url] needs it. */
@Composable
fun rememberLocalNetworkAsk(): (url: String) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(launcher) {
        { url ->
            if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_SDK && localNetworkMissing(context, url)) {
                launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }
    }
}

/**
 * "Allow local network" while the permission is missing. When the system no longer shows its request (refused
 * twice), the button opens the app's system settings instead.
 */
@Composable
fun LocalNetworkAllowButton() {
    if (Build.VERSION.SDK_INT < LOCAL_NETWORK_SDK) return
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(localNetworkAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = localNetworkAllowed(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        val activity = context.findActivity()
        if (!granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_LOCAL_NETWORK)
        ) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }
    }
    if (allowed) return
    TextButton(onClick = { launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) {
        Text(stringResource(R.string.audio_local_network_allow))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
