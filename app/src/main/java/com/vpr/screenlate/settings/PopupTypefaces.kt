package com.vpr.screenlate.settings

import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typefaces the Popup settings built, kept while the app runs: reading a font file takes a moment, so the font list
 * and the preview open in their fonts at once, and a slider move does not read the phone's font again.
 */
@Singleton
class PopupTypefaces @Inject constructor() {
    /** By font key and weight; see [PopupAppearanceViewModel]. */
    val byKey = ConcurrentHashMap<String, Typeface>()

    /** Whether the phone declares no font for the language; null until checked. */
    @Volatile
    var systemFontMissing: Boolean? = null
}
