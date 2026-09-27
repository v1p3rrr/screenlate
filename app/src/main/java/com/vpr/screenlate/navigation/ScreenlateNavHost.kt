package com.vpr.screenlate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vpr.screenlate.anki.AnkiSettingsScreen
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
import com.vpr.screenlate.settings.SettingsPage
import com.vpr.screenlate.settings.SettingsScreen
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

@Serializable
private object BubbleRoute

@Serializable
private object LookupRoute

@Serializable
private object AppearanceRoute

@Serializable
private object YomitanImportRoute

@Serializable
private object AboutRoute

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
    NavHost(navController = navController, startDestination = start) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSearch = { navController.navigate(SearchRoute()) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenDictionaries = { navController.navigate(DictionariesRoute) },
                onOpenAnki = { navController.navigate(AnkiRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = back,
                onOpen = { page ->
                    navController.navigate(
                        when (page) {
                            SettingsPage.BUBBLE -> BubbleRoute
                            SettingsPage.LOOKUP -> LookupRoute
                            SettingsPage.DICTIONARIES -> DictionariesRoute
                            SettingsPage.ANKI -> AnkiRoute
                            SettingsPage.APPEARANCE -> AppearanceRoute
                            SettingsPage.YOMITAN_IMPORT -> YomitanImportRoute
                            SettingsPage.ABOUT -> AboutRoute
                        },
                    )
                },
            )
        }
        composable<BubbleRoute> { BubbleSettingsScreen(onBack = back) }
        composable<LookupRoute> { LookupSettingsScreen(onBack = back) }
        composable<AppearanceRoute> { AppearanceScreen(themeMode, onThemeModeChange, onBack = back) }
        composable<YomitanImportRoute> {
            YomitanImportScreen(onBack = back, onOpenDictionaries = { navController.navigate(DictionariesRoute) })
        }
        composable<AboutRoute> { AboutScreen(onBack = back, onOpenOcrTest = { navController.navigate(OcrTestRoute) }) }
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
