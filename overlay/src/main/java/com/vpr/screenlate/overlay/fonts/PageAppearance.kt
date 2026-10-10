package com.vpr.screenlate.overlay.fonts

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.overlay.settings.PopupAppearance
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The lookup page's language, fonts, text weight and custom CSS, as the argument of `Popup.setAppearance`. */
@Singleton
class PageAppearance @Inject constructor(
    private val settings: PopupAppearanceRepository,
    private val fonts: PopupFonts,
) {
    fun json(language: Language): Flow<JsonObject> {
        val support = language.support
        // The phone's font files are read once, off the main thread.
        val weights = flow { emit(SystemFontFiles.weights(support)) }.flowOn(Dispatchers.IO)
        return combine(settings.appearance(language), fonts.installed, weights) { appearance, installed, systemWeights ->
            build(support, appearance, installed, systemWeights)
        }.distinctUntilChanged()
    }

    companion object {
        /**
         * Every `font-family` of the custom CSS gets the page's font list appended, so a font the phone lacks falls
         * back to the chosen font and the language's font rather than to the browser default.
         *
         * The text weight and letter outline apply to all text only with a chosen font set for all text; otherwise
         * the page applies them to the language's script ([scriptPattern]).
         */
        fun build(
            support: LanguageSupport,
            appearance: PopupAppearance,
            installed: List<InstalledFont>,
            weights: PageFonts.SystemWeights = PageFonts.SystemWeights(),
        ): JsonObject {
            val chosen = installed.firstOrNull { it.id == appearance.fontId }
            val allText = chosen != null && appearance.fontForAllText
            val family = PageFonts.fontFamily(chosen)
            val css = appearance.customCss
            val customCss = if (css.isBlank()) "" else CssCheck.withFallback(css, CssCheck.analyze(css).fontFamilies, family)
            return buildJsonObject {
                put("lang", support.languageTag)
                put("fontFaces", PageFonts.fontFaces(support.systemFonts, installed, chosen, scriptOnly = !allText, weights))
                put("fontFamily", family)
                put("fontSize", appearance.fontSize)
                put("textWeight", appearance.textWeight)
                put("textStroke", appearance.letterThickness * PopupAppearance.STROKE_PER_STEP)
                put("textScope", if (allText) "all" else "script")
                put("scriptPattern", scriptPattern(support.systemFonts.unicodeRange))
                put("customCss", customCss)
                put("definitionCopy", if (appearance.copyDefinitions) appearance.copyMode.id else "")
                if (chosen != null) {
                    put("preload", PageFonts.CHOSEN)
                    put("preloadText", support.fontSample)
                }
            }
        }

        /**
         * [unicodeRange] ("U+3000-30FF, U+4E00") as a character class for a JavaScript regular expression with the
         * `u` flag. Wildcard ranges ("U+4??") are left out.
         */
        fun scriptPattern(unicodeRange: String): String =
            unicodeRange.split(',').map { it.trim() }.filter { RANGE.matches(it) }
                .joinToString("", "[", "]") { range -> range.removePrefix("U+").split('-').joinToString("-") { "\\u{$it}" } }

        private val RANGE = Regex("U\\+[0-9A-Fa-f]{1,6}(-[0-9A-Fa-f]{1,6})?")
    }
}
