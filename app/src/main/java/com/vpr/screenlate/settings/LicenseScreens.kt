package com.vpr.screenlate.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.components.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Libraries the app is built with and their licenses, generated at build time. */
@Composable
fun LibrariesScreen(onBack: () -> Unit) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    SettingsScaffold(stringResource(R.string.about_libraries), onBack) { padding ->
        LibrariesContainer(libraries, Modifier.fillMaxSize().padding(padding))
    }
}

/** The NOTICE file: third-party code and bundled dictionaries with their licenses. */
@Composable
fun NoticesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val text by produceState(noticeText.orEmpty()) {
        value = noticeText ?: withContext(Dispatchers.IO) {
            runCatching { context.assets.open("NOTICE.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
        }.also { noticeText = it }
    }
    SettingsScaffold(stringResource(R.string.about_notices), onBack) { padding ->
        // NOTICE keeps its own line breaks; long lines scroll sideways instead of wrapping mid-list.
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

/** NOTICE as read once while the app runs; it ships with the app and does not change. */
@Volatile
private var noticeText: String? = null
