package com.vpr.screenlate.overlay.translate

import com.vpr.screenlate.core.translate.TranslationService

/**
 * Transient translation choices and cached translations scoped to a single popup session.
 *
 * When the user manually chooses a translation service from the menu, the chosen service
 * produces a translation for the sentence. That translation is held here so that Anki notes
 * created during this session receive the user's explicitly selected translation.
 * Dismissing the popup or starting a new scan clears the session back to automatic cascade.
 */
class PopupTranslationSession {
    /** The manually chosen translation service for this session, or null for automatic cascade. */
    var chosenService: TranslationService? = null

    private val translations = mutableMapOf<String, String>()

    /** Records an active translation for [sentence]. */
    fun setTranslation(sentence: String, translation: String) {
        translations[sentence] = translation
    }

    /** Returns the translation recorded for [sentence], if any. */
    fun getTranslation(sentence: String): String? = translations[sentence]

    /** Resets the session back to defaults (automatic cascade and empty cache). */
    fun clear() {
        chosenService = null
        translations.clear()
    }
}
