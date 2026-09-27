package com.vpr.screenlate.overlay.fonts

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.overlay.settings.PopupAppearance
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The lookup page's language, fonts and custom CSS, as the argument of `Popup.setAppearance`. */
@Singleton
class PageAppearance @Inject constructor(
    private val settings: PopupAppearanceRepository,
    private val fonts: PopupFonts,
) {
    fun json(language: Language): Flow<JsonObject> =
        combine(settings.appearance, fonts.installed) { appearance, installed ->
            build(language.support, appearance, installed)
        }.distinctUntilChanged()

    companion object {
        /**
         * Every `font-family` of the custom CSS gets the page's font list appended, so a font the phone lacks falls
         * back to the chosen font and the language's font rather than to the browser default.
         */
        fun build(support: LanguageSupport, appearance: PopupAppearance, installed: List<InstalledFont>): JsonObject {
            val chosen = installed.firstOrNull { it.id == appearance.fontId }
            val family = PageFonts.fontFamily(chosen)
            val css = appearance.customCss
            val customCss = if (css.isBlank()) "" else CssCheck.withFallback(css, CssCheck.analyze(css).fontFamilies, family)
            return buildJsonObject {
                put("lang", support.languageTag)
                put("fontFaces", PageFonts.fontFaces(support.systemFonts, installed))
                put("fontFamily", family)
                put("fontSize", appearance.fontSize)
                put("customCss", customCss)
                chosen?.let { put("preload", it.family) }
            }
        }
    }
}
