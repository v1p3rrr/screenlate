package com.vpr.screenlate.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vpr.screenlate.anki.AnkiSettingsScreen
import com.vpr.screenlate.background.BackgroundWorkScreen
import com.vpr.screenlate.backup.BackupScreen
import com.vpr.screenlate.bubble.BubbleSettingsScreen
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.debug.ImageViewerScreen
import com.vpr.screenlate.debug.OcrTestScreen
import com.vpr.screenlate.dictionaries.DictionariesScreen
import com.vpr.screenlate.home.HomeScreen
import com.vpr.screenlate.lookup.LookupSettingsScreen
import com.vpr.screenlate.search.SearchScreen
import com.vpr.screenlate.settings.AboutScreen
import com.vpr.screenlate.settings.AppearanceScreen
import com.vpr.screenlate.settings.LibrariesScreen
import com.vpr.screenlate.settings.NoticesScreen
import com.vpr.screenlate.settings.PopupSettingsScreen
import com.vpr.screenlate.settings.SettingsPage
import com.vpr.screenlate.settings.SettingsScreen
import com.vpr.screenlate.translate.TranslationSettingsScreen
import com.vpr.screenlate.yomitan.YomitanImportScreen
import kotlinx.serialization.Serializable

@Serializable
private object HomeRoute

@Serializable
private object SettingsRoute

@Serializable
private object OcrTestRoute

@Serializable
private object DictionariesRoute

@Serializable
private object AnkiRoute

/** @property showAppText scrolls to the app text switches. */
@Serializable
private data class BubbleRoute(val showAppText: Boolean = false)

@Serializable
private object LookupRoute

@Serializable
private object PopupRoute

@Serializable
private object TranslationRoute

@Serializable
private object BackgroundRoute

@Serializable
private object AppearanceRoute

@Serializable
private object YomitanImportRoute

@Serializable
private object BackupRoute

@Serializable
private object AboutRoute

@Serializable
private object LibrariesRoute

@Serializable
private object NoticesRoute

@Serializable
private data class SearchRoute(val query: String = "")

@Serializable
private data class ImageViewerRoute(val path: String, val caption: String = "")

/**
 * @param debugImagePath when set, starts on the debug image viewer instead of the home screen.
 * @param debugImageCaption text shown above the debug image, to test app text next to text in an image.
 * @param ankiSettingsRequests each increment opens the Anki settings (from the overlay's grey ➕).
 * @param dictionariesRequests each increment opens the Dictionaries screen (from search without a dictionary).
 */
@Composable
fun ScreenlateNavHost(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    debugImagePath: String? = null,
    debugImageCaption: String = "",
    ankiSettingsRequests: Int = 0,
    dictionariesRequests: Int = 0,
) {
    val navController = rememberNavController()
    val start: Any = debugImagePath?.let { ImageViewerRoute(it, debugImageCaption) } ?: HomeRoute
    val back: () -> Unit = { navController.fromResumed { popBackStack() } }
    val go: (Any) -> Unit = { route -> navController.fromResumed { navigate(route) } }
    LaunchedEffect(ankiSettingsRequests) {
        if (ankiSettingsRequests > 0) navController.navigate(AnkiRoute) { launchSingleTop = true }
    }
    LaunchedEffect(dictionariesRequests) {
        if (dictionariesRequests > 0) navController.navigate(DictionariesRoute) { launchSingleTop = true }
    }
    // Screens switch at once. A cross-fade let the window's background shine through both half-transparent screens
    // (a white flash in the dark theme), showed the old screen over the new one, and held taps until it ended, as the
    // screen is not resumed before (see fromResumed).
    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSearch = { go(SearchRoute()) },
                onOpenSettings = { go(SettingsRoute) },
                onOpenDictionaries = { go(DictionariesRoute) },
                onOpenAnki = { go(AnkiRoute) },
                onOpenAppText = { go(BubbleRoute(showAppText = true)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = back,
                onOpen = { page ->
                    go(
                        when (page) {
                            SettingsPage.BUBBLE -> BubbleRoute()
                            SettingsPage.BACKGROUND -> BackgroundRoute
                            SettingsPage.LOOKUP -> LookupRoute
                            SettingsPage.POPUP -> PopupRoute
                            SettingsPage.TRANSLATION -> TranslationRoute
                            SettingsPage.DICTIONARIES -> DictionariesRoute
                            SettingsPage.ANKI -> AnkiRoute
                            SettingsPage.APPEARANCE -> AppearanceRoute
                            SettingsPage.YOMITAN_IMPORT -> YomitanImportRoute
                            SettingsPage.BACKUP -> BackupRoute
                            SettingsPage.ABOUT -> AboutRoute
                        },
                    )
                },
            )
        }
        composable<BubbleRoute> { entry ->
            BubbleSettingsScreen(onBack = back, showAppText = entry.toRoute<BubbleRoute>().showAppText)
        }
        composable<LookupRoute> { LookupSettingsScreen(onBack = back) }
        composable<PopupRoute> { PopupSettingsScreen(onBack = back) }
        composable<TranslationRoute> { TranslationSettingsScreen(onBack = back) }
        composable<BackgroundRoute> { BackgroundWorkScreen(onBack = back) }
        composable<AppearanceRoute> { AppearanceScreen(themeMode, onThemeModeChange, onBack = back) }
        composable<YomitanImportRoute> {
            YomitanImportScreen(onBack = back, onOpenDictionaries = { go(DictionariesRoute) })
        }
        composable<BackupRoute> { BackupScreen(onBack = back) }
        composable<AboutRoute> {
            AboutScreen(
                onBack = back,
                onOpenOcrTest = { go(OcrTestRoute) },
                onOpenLibraries = { go(LibrariesRoute) },
                onOpenNotices = { go(NoticesRoute) },
            )
        }
        composable<LibrariesRoute> { LibrariesScreen(onBack = back) }
        composable<NoticesRoute> { NoticesScreen(onBack = back) }
        composable<SearchRoute> { entry ->
            SearchScreen(
                onBack = back,
                onOpenAnkiSettings = { go(AnkiRoute) },
                onOpenDictionaries = { go(DictionariesRoute) },
                initialQuery = entry.toRoute<SearchRoute>().query,
            )
        }
        composable<AnkiRoute> { AnkiSettingsScreen(onBack = back) }
        composable<DictionariesRoute> {
            DictionariesScreen(onBack = back, onOpenYomitanImport = { go(YomitanImportRoute) })
        }
        composable<OcrTestRoute> { OcrTestScreen(onBack = back) }
        composable<ImageViewerRoute> { entry ->
            entry.toRoute<ImageViewerRoute>().let { ImageViewerScreen(it.path, it.caption) }
        }
    }
}

/**
 * Runs [action] only while the current screen is resumed. During a transition it is not, so a second tap on back or
 * on a button does nothing: two backs would leave the start screen too, and the app would show an empty window.
 */
private fun NavController.fromResumed(action: NavController.() -> Unit) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) action()
}

