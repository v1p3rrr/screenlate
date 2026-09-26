package com.vpr.screenlate.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.MainViewModel
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import dagger.hilt.android.AndroidEntryPoint

/** "Look up in Screenlate" in the text selection menu of other apps. */
@AndroidEntryPoint
class ProcessTextActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        setContent {
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            ScreenlateTheme(themeMode = themeMode) {
                SearchScreen(onBack = ::finish, initialQuery = text)
            }
        }
    }
}
