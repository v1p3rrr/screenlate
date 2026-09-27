package com.vpr.screenlate.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SettingsScaffold

const val SOURCE_URL = "https://github.com/v1p3rrr/screenlate"

/** Version, source code, license and developer tools. */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenOcrTest: () -> Unit) {
    val context = LocalContext.current
    SettingsScaffold(stringResource(R.string.about_title), onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.app_title)) {
                Text(stringResource(R.string.about_version, versionName(context)))
                Hint(stringResource(R.string.about_license))
                OutlinedButton(onClick = { openUrl(context, SOURCE_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_source))
                }
                OutlinedButton(onClick = { openUrl(context, "$SOURCE_URL/issues") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_report_bug))
                }
            }
            SectionCard(title = stringResource(R.string.home_tools_title)) {
                OutlinedButton(onClick = onOpenOcrTest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ocr_test_title))
                }
            }
        }
    }
}

fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
