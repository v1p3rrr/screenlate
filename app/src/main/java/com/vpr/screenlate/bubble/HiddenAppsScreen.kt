package com.vpr.screenlate.bubble

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SettingsScaffold
import com.vpr.screenlate.ui.components.doneClearsFocus
import com.vpr.screenlate.ui.components.formContent
import com.vpr.screenlate.ui.theme.AccentDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LaunchableApp(val packageName: String, val label: String, val icon: ImageBitmap?)

/** The launcher apps with a search; a checked app hides the bubble while it is open. Hidden apps come first. */
@Composable
fun HiddenAppsScreen(onBack: () -> Unit, viewModel: BubbleSettingsViewModel = hiltViewModel()) {
    val stored by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Opens with the list read last while the app runs, then reads it again for apps installed meanwhile.
    val apps by produceState(lastLaunchableApps) { value = launchableApps(context).also { lastLaunchableApps = it } }
    var filter by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    SettingsScaffold(stringResource(R.string.bubble_hidden_apps), onBack) { padding ->
        val hiddenPackages = stored?.hiddenPackages ?: return@SettingsScaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .formContent(padding, focusManager),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint(stringResource(R.string.bubble_hidden_apps_hint))
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = { Text(stringResource(R.string.bubble_filter_apps)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = doneClearsFocus(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val visible = apps.orEmpty()
                .filter { filter.isBlank() || it.label.contains(filter, ignoreCase = true) }
                .sortedWith(compareByDescending<LaunchableApp> { it.packageName in hiddenPackages }.thenBy { it.label.lowercase() })
            items(visible, key = { it.packageName }) { app ->
                val hidden = app.packageName in hiddenPackages
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setHidden(app.packageName, !hidden) },
                ) {
                    if (app.icon != null) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                    } else {
                        Spacer(Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, modifier = Modifier.weight(1f))
                    Checkbox(checked = hidden, onCheckedChange = { viewModel.setHidden(app.packageName, it) }, colors = AccentDefaults.checkboxColors())
                }
            }
            if (apps == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = AccentDefaults.progress)
                    }
                }
            }
        }
    }
}

/** The launcher apps as read last while the app runs; reading them with their icons takes a moment. */
@Volatile
private var lastLaunchableApps: List<LaunchableApp>? = null

private suspend fun launchableApps(context: Context): List<LaunchableApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .distinctBy { it.activityInfo.packageName }
        .map { info ->
            LaunchableApp(
                packageName = info.activityInfo.packageName,
                label = info.loadLabel(pm).toString(),
                icon = runCatching { info.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull(),
            )
        }
}

private const val ICON_PX = 96
