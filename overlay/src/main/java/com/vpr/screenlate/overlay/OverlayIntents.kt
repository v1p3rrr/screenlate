package com.vpr.screenlate.overlay

import android.content.Context
import android.content.Intent
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.log.Log

/** How the overlay opens the app, and the extras it puts on those intents. */
object OverlayIntents {
    /** Which screen the app should open; see the `OPEN_*` values. */
    const val EXTRA_OPEN = "open"

    /** The Anki settings, e.g. from the grey ➕ of a broken Anki setup; of the language in [EXTRA_LANGUAGE] if given. */
    const val OPEN_ANKI_SETTINGS = "anki"

    /** The dictionaries; of the language in [EXTRA_LANGUAGE] if given. */
    const val OPEN_DICTIONARIES = "dictionaries"

    /** The on-device models of the language in [EXTRA_LANGUAGE] (its code). */
    const val OPEN_OCR_MODELS = "ocr-models"
    const val EXTRA_LANGUAGE = "language"
    const val INSTALLATION_URL = "screenlate://installation"
    const val OCR_MODELS_URL = "screenlate://ocr-models"

    /** The dictionaries of [language]. */
    fun openInstallation(context: Context, language: Language) =
        openScreen(context, OPEN_DICTIONARIES) { putExtra(EXTRA_LANGUAGE, language.code) }

    /** The Anki settings of [language]. */
    fun openAnkiSettings(context: Context, language: Language) =
        openScreen(context, OPEN_ANKI_SETTINGS) { putExtra(EXTRA_LANGUAGE, language.code) }

    fun openOcrModels(context: Context, language: Language) =
        openScreen(context, OPEN_OCR_MODELS) { putExtra(EXTRA_LANGUAGE, language.code) }

    /**
     * Opens [screen] (one of the `OPEN_*` values) in the app's task, or in a new one. A task started here has the
     * launcher's intent at its root, so the launcher and [openApp] bring it back instead of starting a second main
     * screen on top. The running main screen gets the request in `onNewIntent` and keeps its pages.
     */
    fun openScreen(context: Context, screen: String, configure: Intent.() -> Unit = {}) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.putExtra(EXTRA_OPEN, screen).apply(configure)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(intent) }.onFailure { Log.w(TAG, "Cannot open the app", it) }
    }

    /**
     * Brings the app to the front as the launcher does: its task as it was left, or a new one. `SINGLE_TOP` covers a
     * task started by another intent (one kept from an older version): the main screen on top is reused rather than
     * a second one started over it.
     */
    fun openApp(context: Context) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            ?.let { intent -> runCatching { context.startActivity(intent) }.onFailure { Log.w(TAG, "Cannot open the app", it) } }
    }

    private const val TAG = "OverlayIntents"
}
