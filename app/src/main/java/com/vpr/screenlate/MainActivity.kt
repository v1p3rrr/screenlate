package com.vpr.screenlate

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.audio.LocalNetworkAskEffect
import com.vpr.screenlate.camera.ShareImageActivity
import com.vpr.screenlate.camera.SharedImages
import com.vpr.screenlate.camera.isAnotherAppsContent
import com.vpr.screenlate.languages.LocalFlagCodes
import com.vpr.screenlate.navigation.LanguagePageRequest
import com.vpr.screenlate.navigation.ScreenlateNavHost
import com.vpr.screenlate.overlay.OverlayIntents
import com.vpr.screenlate.ui.theme.ScreenlateTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var sharedImages: SharedImages

    /** The last request to open the Anki settings, from the launch intent or a later one. */
    private var ankiSettingsRequest by mutableStateOf<LanguagePageRequest?>(null)

    /** The last request to open the dictionaries, from the launch intent or a later one. */
    private var dictionariesRequest by mutableStateOf<LanguagePageRequest?>(null)

    /** Counts images shared to the app; each opens the Camera page, which imports it from [sharedImages]. */
    private var cameraRequests by mutableIntStateOf(0)

    /** The last request to open a language's on-device models, from the overlay's message. */
    private var ocrModelsRequest by mutableStateOf<LanguagePageRequest?>(null)

    /**
     * Debug builds only: `--es debug_image <file>` opens a file from the app's internal files directory,
     * `--es debug_caption <text>` adds a line of text above it.
     */
    private fun debugImagePath(): String? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val name = intent.getStringExtra(EXTRA_DEBUG_IMAGE) ?: return null
        return filesDir.resolve(name).path
    }

    /**
     * Debug builds only: `--es debug_fullscreen default|swipe` hides the system bars, as fullscreen apps do, with the
     * default behavior (gestures and bars on an edge interaction) or bars shown only transiently by a swipe.
     */
    private fun debugFullscreen() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val mode = intent.getStringExtra(EXTRA_DEBUG_FULLSCREEN) ?: return
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = if (mode == "swipe") {
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash screen stays until the settings are read: an e-ink screen would flash a frame in the colour theme.
        installSplashScreen().setKeepOnScreenCondition { viewModel.theme.value == null || viewModel.firstRun.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleOpenRequest(intent)
        debugFullscreen()
        setContent {
            val theme = viewModel.theme.collectAsStateWithLifecycle().value ?: return@setContent
            val pending = viewModel.firstRun.collectAsStateWithLifecycle().value ?: return@setContent
            // Read once: answering the first run must not change the start screen under the restored back stack.
            val firstRun = rememberSaveable { pending }
            val flagCodes by viewModel.flagCodes.collectAsStateWithLifecycle()
            ScreenlateTheme(
                themeMode = theme.mode,
                eInk = theme.eInk,
                systemColors = theme.systemColors,
            ) {
                CompositionLocalProvider(LocalFlagCodes provides flagCodes) {
                    LocalNetworkAskEffect()
                    ScreenlateNavHost(
                        themeMode = theme.mode,
                        onThemeModeChange = viewModel::setThemeMode,
                        firstRun = firstRun,
                        debugImagePath = debugImagePath(),
                        debugImageCaption = intent.getStringExtra(EXTRA_DEBUG_CAPTION).orEmpty(),
                        ankiSettingsRequest = ankiSettingsRequest,
                        dictionariesRequest = dictionariesRequest,
                        cameraRequests = cameraRequests,
                        ocrModelsRequest = ocrModelsRequest,
                    )
                }
            }
        }
    }

    /** The request after this one, on the language in [intent]'s extra. */
    private fun LanguagePageRequest?.next(intent: Intent) =
        LanguagePageRequest(intent.getStringExtra(OverlayIntents.EXTRA_LANGUAGE), (this?.number ?: 0) + 1)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenRequest(intent)
    }

    private fun handleOpenRequest(intent: Intent) {
        when (intent.getStringExtra(OverlayIntents.EXTRA_OPEN)) {
            OverlayIntents.OPEN_ANKI_SETTINGS -> ankiSettingsRequest = ankiSettingsRequest.next(intent)
            OverlayIntents.OPEN_DICTIONARIES -> dictionariesRequest = dictionariesRequest.next(intent)
            OverlayIntents.OPEN_OCR_MODELS -> ocrModelsRequest = ocrModelsRequest.next(intent)
            // A task restarted from the recent apps gets its first intent again, without the access to the image. Any
            // app can start this screen, so the link is checked again as the share screen does.
            ShareImageActivity.OPEN_SHARED_IMAGE -> if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
                intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                    ?.takeIf { it.isAnotherAppsContent(packageName) }
                    ?.let { uri ->
                        sharedImages.offer(uri.toString())
                        cameraRequests++
                    }
            }
        }
    }

    private companion object {
        const val EXTRA_DEBUG_IMAGE = "debug_image"
        const val EXTRA_DEBUG_CAPTION = "debug_caption"
        const val EXTRA_DEBUG_FULLSCREEN = "debug_fullscreen"
    }
}
