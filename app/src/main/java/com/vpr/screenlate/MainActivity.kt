package com.vpr.screenlate

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.navigation.ScreenlateNavHost
import com.vpr.screenlate.overlay.OverlayIntents
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Counts requests to open the Anki settings, from the launch intent and later ones. */
    private var ankiSettingsRequests by mutableIntStateOf(0)

    /**
     * Debug builds only: `--es debug_image <file>` opens a file from the app's internal files directory,
     * `--es debug_caption <text>` adds a line of text above it.
     */
    private fun debugImagePath(): String? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val name = intent.getStringExtra(EXTRA_DEBUG_IMAGE) ?: return null
        return filesDir.resolve(name).path
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleOpenRequest(intent)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val eInk by viewModel.eInk.collectAsStateWithLifecycle()
            ScreenlateTheme(themeMode = themeMode, eInk = eInk) {
                ScreenlateNavHost(
                    themeMode = themeMode,
                    onThemeModeChange = viewModel::setThemeMode,
                    debugImagePath = debugImagePath(),
                    debugImageCaption = intent.getStringExtra(EXTRA_DEBUG_CAPTION).orEmpty(),
                    ankiSettingsRequests = ankiSettingsRequests,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenRequest(intent)
    }

    private fun handleOpenRequest(intent: Intent) {
        if (intent.getStringExtra(OverlayIntents.EXTRA_OPEN) == OverlayIntents.OPEN_ANKI_SETTINGS) ankiSettingsRequests++
    }

    private companion object {
        const val EXTRA_DEBUG_IMAGE = "debug_image"
        const val EXTRA_DEBUG_CAPTION = "debug_caption"
    }
}
