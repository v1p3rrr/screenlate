package com.vpr.screenlate.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.vpr.screenlate.ui.theme.LocalEInk
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
 */
@Composable
fun ScreenlateNavHost(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    debugImagePath: String? = null,
    debugImageCaption: String = "",
    ankiSettingsRequests: Int = 0,
) {
    val navController = rememberNavController()
    val start: Any = debugImagePath?.let { ImageViewerRoute(it, debugImageCaption) } ?: HomeRoute
    val back: () -> Unit = { navController.popBackStack() }
    LaunchedEffect(ankiSettingsRequests) {
        if (ankiSettingsRequests > 0) navController.navigate(AnkiRoute) { launchSingleTop = true }
    }
    // Navigation's default cross-fade; e-ink screens switch at once, since every frame of a fade costs a refresh.
    val eInk = LocalEInk.current
    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { if (eInk) EnterTransition.None else fadeIn(tween(FADE_MS)) },
        exitTransition = { if (eInk) ExitTransition.None else fadeOut(tween(FADE_MS)) },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSearch = { navController.navigate(SearchRoute()) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenDictionaries = { navController.navigate(DictionariesRoute) },
                onOpenAnki = { navController.navigate(AnkiRoute) },
                onOpenAppText = { navController.navigate(BubbleRoute(showAppText = true)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = back,
                onOpen = { page ->
                    navController.navigate(
                        when (page) {
                            SettingsPage.BUBBLE -> BubbleRoute()
                            SettingsPage.BACKGROUND -> BackgroundRoute
                            SettingsPage.LOOKUP -> LookupRoute
                            SettingsPage.POPUP -> PopupRoute
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
        composable<BackgroundRoute> { BackgroundWorkScreen(onBack = back) }
        composable<AppearanceRoute> { AppearanceScreen(themeMode, onThemeModeChange, onBack = back) }
        composable<YomitanImportRoute> {
            YomitanImportScreen(onBack = back, onOpenDictionaries = { navController.navigate(DictionariesRoute) })
        }
        composable<BackupRoute> { BackupScreen(onBack = back) }
        composable<AboutRoute> {
            AboutScreen(
                onBack = back,
                onOpenOcrTest = { navController.navigate(OcrTestRoute) },
                onOpenLibraries = { navController.navigate(LibrariesRoute) },
                onOpenNotices = { navController.navigate(NoticesRoute) },
            )
        }
        composable<LibrariesRoute> { LibrariesScreen(onBack = back) }
        composable<NoticesRoute> { NoticesScreen(onBack = back) }
        composable<SearchRoute> { entry ->
            SearchScreen(
                onBack = back,
                onOpenAnkiSettings = { navController.navigate(AnkiRoute) },
                initialQuery = entry.toRoute<SearchRoute>().query,
            )
        }
        composable<AnkiRoute> { AnkiSettingsScreen(onBack = back) }
        composable<DictionariesRoute> {
            DictionariesScreen(onBack = back, onOpenYomitanImport = { navController.navigate(YomitanImportRoute) })
        }
        composable<OcrTestRoute> { OcrTestScreen(onBack = back) }
        composable<ImageViewerRoute> { entry ->
            entry.toRoute<ImageViewerRoute>().let { ImageViewerScreen(it.path, it.caption) }
        }
    }
}

private const val FADE_MS = 700
