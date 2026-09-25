package com.vpr.screenlate

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.navigation.ScreenlateNavHost
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Debug builds only: `--es debug_image <file>` opens a file from the app's internal files directory. */
    private fun debugImagePath(): String? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val name = intent.getStringExtra(EXTRA_DEBUG_IMAGE) ?: return null
        return filesDir.resolve(name).path
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            ScreenlateTheme(themeMode = themeMode) {
                ScreenlateNavHost(
                    themeMode = themeMode,
                    onThemeModeChange = viewModel::setThemeMode,
                    debugImagePath = debugImagePath(),
                )
            }
        }
    }

    private companion object {
        const val EXTRA_DEBUG_IMAGE = "debug_image"
    }
}
