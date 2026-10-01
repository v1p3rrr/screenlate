package com.vpr.screenlate.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.MainActivity
import com.vpr.screenlate.MainViewModel
import com.vpr.screenlate.overlay.OverlayIntents
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import dagger.hilt.android.AndroidEntryPoint

/** "Look up in Screenlate" in the text selection menu of other apps. */
@AndroidEntryPoint
class ProcessTextActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { mainViewModel.theme.value == null }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        setContent {
            val theme = mainViewModel.theme.collectAsStateWithLifecycle().value ?: return@setContent
            ScreenlateTheme(themeMode = theme.mode, eInk = theme.eInk, wallpaperColors = theme.wallpaperColors) {
                SearchScreen(
                    onBack = ::finish,
                    onOpenAnkiSettings = { openApp(OverlayIntents.OPEN_ANKI_SETTINGS) },
                    onOpenDictionaries = { openApp(OverlayIntents.OPEN_DICTIONARIES) },
                    initialQuery = text,
                )
            }
        }
    }

    /** [screen] is one of the `OverlayIntents.OPEN_*` values. */
    private fun openApp(screen: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(OverlayIntents.EXTRA_OPEN, screen)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}
