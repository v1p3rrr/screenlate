package com.vpr.screenlate.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vpr.screenlate.R
import com.vpr.screenlate.anki.AnkiSettingsScreen
import com.vpr.screenlate.background.BackgroundTipViewModel
import com.vpr.screenlate.background.BackgroundWorkScreen
import com.vpr.screenlate.background.rememberBackgroundTipBadge
import com.vpr.screenlate.backup.BackupScreen
import com.vpr.screenlate.bubble.BubbleSettingsScreen
import com.vpr.screenlate.camera.CameraScreen
import com.vpr.screenlate.camera.CameraSettingsScreen
import com.vpr.screenlate.camera.PhotoHistoryScreen
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.debug.ImageViewerScreen
import com.vpr.screenlate.debug.OcrTestScreen
import com.vpr.screenlate.dictionaries.DictionariesScreen
import com.vpr.screenlate.home.HomeScreen
import com.vpr.screenlate.languages.FirstRunScreen
import com.vpr.screenlate.languages.LanguageSetupScreen
import com.vpr.screenlate.languages.LanguagesScreen
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
import kotlinx.serialization.Serializable

@Serializable
private object HomeRoute

@Serializable
private object SettingsRoute

@Serializable
private object OcrTestRoute

@Serializable
private object DictionariesRoute

/**
 * @property language the code of the language the page opens on, as `AnkiSettingsViewModel` reads it; null for the
 *   active one.
 * @property audio scrolls to the audio sources.
 */
@Serializable
private data class AnkiRoute(val language: String? = null, val audio: Boolean = false)

/** @property showAppText scrolls to the app text switches. */
@Serializable
private data class BubbleRoute(val showAppText: Boolean = false, val ocrModels: String? = null)

/** Opens the on-device models of the language with [language] (its code); [number] tells repeated requests apart. */
data class OcrModelsRequest(val language: String, val number: Int)

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

/** @property yomitan opens the page at the Yomitan import. */
@Serializable
private data class BackupRoute(val yomitan: Boolean = false)

@Serializable
private object AboutRoute

@Serializable
private object LibrariesRoute

@Serializable
private object NoticesRoute

@Serializable
private data class SearchRoute(val query: String = "")

@Serializable
private object CameraRoute

@Serializable
private object CameraHistoryRoute

@Serializable
private object CameraSettingsRoute

@Serializable
private object FirstRunRoute

@Serializable
private object LanguagesRoute

/** Argument names as `LanguageSetupViewModel` reads them. */
@Serializable
private data class LanguageSetupRoute(val code: String, val firstRun: Boolean = false)

@Serializable
private data class ImageViewerRoute(val path: String, val caption: String = "")

/** The pages of the navigation bar, left to right, with the route each one starts on. */
private enum class Tab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int, val root: Any) {
    HOME(R.string.nav_home, R.drawable.ic_home, HomeRoute),
    SEARCH(R.string.nav_search, R.drawable.ic_search, SearchRoute()),
    CAMERA(R.string.camera_title, R.drawable.ic_photo_camera, CameraRoute),
    SETTINGS(R.string.settings_title, R.drawable.ic_settings, SettingsRoute),
}

/**
 * The app's pages under a navigation bar. Each tab keeps its own pages: switching tabs saves the open tab's back stack
 * and restores the other's, and a page opened from a tab stays in it. Back on the first page of a tab goes to Home.
 *
 * @param debugImagePath when set, starts on the debug image viewer instead of the home screen.
 * @param debugImageCaption text shown above the debug image, to test app text next to text in an image.
 * @param ankiSettingsRequests each increment opens the Anki settings (from the overlay's grey ➕).
 * @param dictionariesRequests each increment opens the Dictionaries screen (from search without a dictionary).
 * @param ocrModelsRequest opens a language's on-device models (from the overlay's message that the device cannot read).
 * @param cameraRequests each increment opens the Camera page (an image shared to the app), except during the first run.
 * @param firstRun starts on the question which language the user learns.
 */
@Composable
fun ScreenlateNavHost(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    firstRun: Boolean = false,
    debugImagePath: String? = null,
    debugImageCaption: String = "",
    ankiSettingsRequests: Int = 0,
    dictionariesRequests: Int = 0,
    ocrModelsRequest: OcrModelsRequest? = null,
    cameraRequests: Int = 0,
    tip: BackgroundTipViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val start: Any = debugImagePath?.let { ImageViewerRoute(it, debugImageCaption) } ?: if (firstRun) FirstRunRoute else HomeRoute
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val entry by navController.currentBackStackEntryAsState()
    val settingsBadge = rememberBackgroundTipBadge(tip)
    val back: () -> Unit = { navController.fromResumed { popBackStack() } }
    val go: (Any) -> Unit = { route -> navController.fromResumed { navigate(route) } }
    val select: (Tab) -> Unit = { next ->
        if (next == tab) {
            navController.popBackStack(next.root::class, inclusive = false)
        } else {
            // Leaving the settings list with the badge shown counts as having seen the tip.
            if (settingsBadge && entry?.destination?.hasRoute<SettingsRoute>() == true) tip.markSeen()
            navController.navigate(next.root) {
                popUpTo(navController.graph.id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            tab = next
        }
    }
    // After the first run nothing before the home screen stays on the back stack.
    val home: () -> Unit = {
        navController.navigate(HomeRoute) { popUpTo(navController.graph.id) { inclusive = true } }
        tab = Tab.HOME
    }
    // Requests from the overlay open their page in the Settings tab.
    val inSettings: (Any) -> Unit = { route ->
        if (tab != Tab.SETTINGS) select(Tab.SETTINGS)
        navController.navigate(route) { launchSingleTop = true }
    }
    LaunchedEffect(ankiSettingsRequests) {
        if (ankiSettingsRequests > 0) inSettings(AnkiRoute())
    }
    LaunchedEffect(dictionariesRequests) {
        if (dictionariesRequests > 0) inSettings(DictionariesRoute)
    }
    LaunchedEffect(ocrModelsRequest) {
        ocrModelsRequest?.let { inSettings(BubbleRoute(ocrModels = it.language)) }
    }
    val onTabRoot = entry?.destination?.hasRoute(tab.root::class) == true
    BackHandler(enabled = tab != Tab.HOME && onTabRoot) { select(Tab.HOME) }
    val barShown = entry?.let(::showsBar) ?: (start == HomeRoute)
    // The Camera page imports the image when it opens; during the first run it waits until the user goes there.
    LaunchedEffect(cameraRequests) {
        if (cameraRequests > 0 && barShown) select(Tab.CAMERA)
    }
    // One Scaffold for every page: the NavHost is not recreated when the bar shows or hides.
    Scaffold(
        bottomBar = { if (barShown) TabBar(tab, settingsBadge, select) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        // Screens switch at once. A cross-fade let the window's background shine through both half-transparent
        // screens (a white flash in the dark theme), showed the old screen over the new one, and held taps until it
        // ended, as the screen is not resumed before (see fromResumed).
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenDictionaries = { go(DictionariesRoute) },
                    onOpenAnki = { language -> go(AnkiRoute(language?.code)) },
                    onOpenAudio = { language -> go(AnkiRoute(language?.code, audio = true)) },
                    onOpenAppText = { go(BubbleRoute(showAppText = true)) },
                    onOpenLanguages = { go(LanguagesRoute) },
                )
            }
            composable<LanguagesRoute> {
                LanguagesScreen(onBack = back, onOpenLanguageSetup = { go(LanguageSetupRoute(it.code)) })
            }
            composable<FirstRunRoute> {
                FirstRunScreen(onSetup = { go(LanguageSetupRoute(it.code, firstRun = true)) }, onDone = home)
            }
            composable<LanguageSetupRoute> { entry ->
                val firstRunSetup = entry.toRoute<LanguageSetupRoute>().firstRun
                LanguageSetupScreen(onBack = back, onDone = if (firstRunSetup) home else back)
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpen = { page ->
                        go(
                            when (page) {
                                SettingsPage.BUBBLE -> BubbleRoute()
                                SettingsPage.BACKGROUND -> BackgroundRoute
                                SettingsPage.LOOKUP -> LookupRoute
                                SettingsPage.POPUP -> PopupRoute
                                SettingsPage.TRANSLATION -> TranslationRoute
                                SettingsPage.CAMERA -> CameraSettingsRoute
                                SettingsPage.DICTIONARIES -> DictionariesRoute
                                SettingsPage.ANKI -> AnkiRoute()
                                SettingsPage.APPEARANCE -> AppearanceRoute
                                SettingsPage.BACKUP -> BackupRoute()
                                SettingsPage.ABOUT -> AboutRoute
                            },
                        )
                    },
                )
            }
            composable<BubbleRoute> { entry ->
                val route = entry.toRoute<BubbleRoute>()
                BubbleSettingsScreen(onBack = back, showAppText = route.showAppText, ocrModels = route.ocrModels)
            }
            composable<LookupRoute> { LookupSettingsScreen(onBack = back) }
            composable<PopupRoute> { PopupSettingsScreen(onBack = back) }
            composable<TranslationRoute> { TranslationSettingsScreen(onBack = back) }
            composable<BackgroundRoute> { BackgroundWorkScreen(onBack = back) }
            composable<AppearanceRoute> { AppearanceScreen(themeMode, onThemeModeChange, onBack = back) }
            composable<BackupRoute> { entry ->
                BackupScreen(
                    onBack = back,
                    onOpenDictionaries = { go(DictionariesRoute) },
                    showYomitan = entry.toRoute<BackupRoute>().yomitan,
                )
            }
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
                    onBack = null,
                    onOpenAnkiSettings = { go(AnkiRoute()) },
                    onOpenDictionaries = { go(DictionariesRoute) },
                    initialQuery = entry.toRoute<SearchRoute>().query,
                )
            }
            composable<CameraRoute> {
                CameraScreen(onOpenDictionaries = { inSettings(DictionariesRoute) },
                    onOpenModels = { language -> inSettings(BubbleRoute(ocrModels = language.code)) },
                    onOpenLanguages = { inSettings(LanguagesRoute) },
                    onOpenHistory = { go(CameraHistoryRoute) },
                    onOpenSettings = { go(CameraSettingsRoute) })
            }
            composable<CameraHistoryRoute> { PhotoHistoryScreen(onBack = back) }
            composable<CameraSettingsRoute> { CameraSettingsScreen(onBack = back) }
            composable<AnkiRoute> { entry -> AnkiSettingsScreen(onBack = back, showAudio = entry.toRoute<AnkiRoute>().audio) }
            composable<DictionariesRoute> {
                DictionariesScreen(
                    onBack = back,
                    onOpenYomitanImport = { go(BackupRoute(yomitan = true)) },
                    onOpenLanguages = { go(LanguagesRoute) },
                )
            }
            composable<OcrTestRoute> { OcrTestScreen(onBack = back) }
            composable<ImageViewerRoute> { entry ->
                entry.toRoute<ImageViewerRoute>().let { ImageViewerScreen(it.path, it.caption) }
            }
        }
    }
}

/** The first run and the debug image viewer fill the window; every other page has the navigation bar. */
private fun showsBar(entry: NavBackStackEntry): Boolean {
    val destination = entry.destination
    return when {
        destination.hasRoute<FirstRunRoute>() || destination.hasRoute<ImageViewerRoute>() -> false
        destination.hasRoute<LanguageSetupRoute>() -> !entry.toRoute<LanguageSetupRoute>().firstRun
        else -> true
    }
}

@Composable
private fun TabBar(selected: Tab, settingsBadge: Boolean, onSelect: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            val badge = tab == Tab.SETTINGS && settingsBadge
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(badge = { if (badge) Badge() }) {
                        Icon(
                            painterResource(tab.icon),
                            contentDescription = if (badge) stringResource(R.string.settings_badge) else null,
                        )
                    }
                },
                label = {
                    Text(
                        stringResource(tab.label),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = LocalTextStyle.current.fontSize),
                    )
                },
            )
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
