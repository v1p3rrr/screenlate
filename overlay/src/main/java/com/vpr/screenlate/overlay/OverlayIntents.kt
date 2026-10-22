package com.vpr.screenlate.overlay

import android.content.Context
import android.content.Intent
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.log.Log

/** How the overlay opens the app, and the extras it puts on those intents. */
object OverlayIntents {
    /** Which screen the app should open; see the `OPEN_*` values. */
    const val EXTRA_OPEN = "open"

    /** The Anki settings, e.g. from the grey ➕ of a broken Anki setup. */
    const val OPEN_ANKI_SETTINGS = "anki"
    const val OPEN_DICTIONARIES = "dictionaries"

    /** The on-device models of the language in [EXTRA_LANGUAGE] (its code). */
    const val OPEN_OCR_MODELS = "ocr-models"
    const val EXTRA_LANGUAGE = "language"
    const val INSTALLATION_URL = "screenlate://installation"
    const val OCR_MODELS_URL = "screenlate://ocr-models"

    fun openInstallation(context: Context) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?.putExtra(EXTRA_OPEN, OPEN_DICTIONARIES)
            ?.let { context.startActivity(it) }
    }

    fun openOcrModels(context: Context, language: Language) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?.putExtra(EXTRA_OPEN, OPEN_OCR_MODELS)
            ?.putExtra(EXTRA_LANGUAGE, language.code)
            ?.let { context.startActivity(it) }
    }

    /** Brings the app to the front as the launcher does: its task as it was left, or a new one. */
    fun openApp(context: Context) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?.let { intent -> runCatching { context.startActivity(intent) }.onFailure { Log.w(TAG, "Cannot open the app", it) } }
    }

    private const val TAG = "OverlayIntents"
}
