package com.vpr.screenlate.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.anki.AnkiSettingsScreen
import com.vpr.screenlate.bubble.BubbleSettingsScreen
import com.vpr.screenlate.debug.ImageViewerScreen
import com.vpr.screenlate.dictionaries.DictionariesScreen
import com.vpr.screenlate.debug.OcrTestScreen
import com.vpr.screenlate.home.HomeScreen
import com.vpr.screenlate.search.SearchScreen
import kotlinx.serialization.Serializable

@Serializable
private object HomeRoute

@Serializable
private object OcrTestRoute

@Serializable
private object DictionariesRoute

@Serializable
private object AnkiRoute

@Serializable
private object BubbleRoute

@Serializable
private data class SearchRoute(val query: String = "")

@Serializable
private data class ImageViewerRoute(val path: String, val caption: String = "")

/**
 * @param debugImagePath when set, starts on the debug image viewer instead of the home screen.
 * @param debugImageCaption text shown above the debug image, to test app text next to text in an image.
 */
@Composable
fun ScreenlateNavHost(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    debugImagePath: String? = null,
    debugImageCaption: String = "",
) {
    val navController = rememberNavController()
    val start: Any = debugImagePath?.let { ImageViewerRoute(it, debugImageCaption) } ?: HomeRoute
    NavHost(navController = navController, startDestination = start) {
        composable<HomeRoute> {
            HomeScreen(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onOpenOcrTest = { navController.navigate(OcrTestRoute) },
                onOpenDictionaries = { navController.navigate(DictionariesRoute) },
                onOpenAnki = { navController.navigate(AnkiRoute) },
                onOpenSearch = { navController.navigate(SearchRoute()) },
                onOpenBubble = { navController.navigate(BubbleRoute) },
            )
        }
        composable<BubbleRoute> {
            BubbleSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<SearchRoute> { entry ->
            SearchScreen(
                onBack = { navController.popBackStack() },
                initialQuery = entry.toRoute<SearchRoute>().query,
            )
        }
        composable<AnkiRoute> {
            AnkiSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<DictionariesRoute> {
            DictionariesScreen(onBack = { navController.popBackStack() })
        }
        composable<OcrTestRoute> {
            OcrTestScreen(onBack = { navController.popBackStack() })
        }
        composable<ImageViewerRoute> { entry ->
            entry.toRoute<ImageViewerRoute>().let { ImageViewerScreen(it.path, it.caption) }
        }
    }
}
