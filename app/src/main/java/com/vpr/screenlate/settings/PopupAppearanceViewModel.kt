package com.vpr.screenlate.settings

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.translate.TranslationSettings
import com.vpr.screenlate.core.translate.TranslationSettingsRepository
import com.vpr.screenlate.languages.ShownLanguage
import com.vpr.screenlate.overlay.fonts.CatalogFont
import com.vpr.screenlate.overlay.fonts.CssCheck
import com.vpr.screenlate.overlay.fonts.FontDownload
import com.vpr.screenlate.overlay.fonts.FontImport
import com.vpr.screenlate.overlay.fonts.InstalledFont
import com.vpr.screenlate.overlay.fonts.PageFonts
import com.vpr.screenlate.overlay.fonts.PopupFonts
import com.vpr.screenlate.overlay.fonts.SystemFontFiles
import com.vpr.screenlate.overlay.settings.DefinitionCopyMode
import com.vpr.screenlate.overlay.settings.OverlaySettings
import com.vpr.screenlate.overlay.settings.PopupAppearance
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Font, text size and weight, custom CSS, and definition copying of the lookup page, and whether the popup starts with
 * the recognized text. The font, its scope and the CSS are the [shown] language's.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PopupAppearanceViewModel @Inject constructor(
    private val repository: PopupAppearanceRepository,
    private val fonts: PopupFonts,
    private val settingsReset: SettingsReset,
    private val cache: PopupTypefaces,
    private val overlaySettings: OverlaySettingsRepository,
    private val translationSettings: TranslationSettingsRepository,
    profiles: LanguageProfiles,
) : ViewModel() {
    val shown = ShownLanguage(profiles, viewModelScope)

    /** The shown language's appearance with the shared settings. */
    val appearance: StateFlow<PopupAppearance?> = shown.language
        .flatMapLatest { repository.appearance(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.cachedAppearance(shown.language.value))

    /** The popup settings stored with the bubble's: the recognized text at the top, auto-hide. */
    val overlay: StateFlow<OverlaySettings?> =
        overlaySettings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, overlaySettings.cachedSettings)

    /** For the 文A switch, which the Translation page has too. */
    val translation: StateFlow<TranslationSettings?> =
        translationSettings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, translationSettings.cachedSettings)

    val installed: StateFlow<List<InstalledFont>> = fonts.installed
    val downloads: StateFlow<Map<String, FontDownload>> = fonts.downloads
    val catalog: List<CatalogFont> get() = fonts.catalog.filter { it.language == shown.language.value.support.languageTag }

    /** Typefaces for the font list: installed fonts by id, the phone's font for the shown language under [SYSTEM]. */
    val typefaces: StateFlow<Map<String, Typeface>> = combine(fonts.installed, shown.language, ::loadTypefaces)
        .stateIn(viewModelScope, SharingStarted.Eagerly, cachedTypefaces(fonts.installed.value, shown.language.value))

    /**
     * The preview's typeface: the chosen font, or the phone's, with a variable font set to the text weight. The
     * preview asks it for that weight, so a font with one weight is made bold from 600 on, as on the page.
     */
    val previewTypeface: StateFlow<Typeface?> =
        combine(shown.language.flatMapLatest { language -> repository.appearance(language).map { language to it } }, fonts.installed) {
                (language, appearance), installed ->
            Triple(language, installed.firstOrNull { it.id == appearance.fontId }, appearance.textWeight)
        }
            .distinctUntilChanged()
            .map { (language, font, weight) -> withContext(Dispatchers.IO) { cachedTypeface(language, font, weight) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, cachedPreview(shown.language.value))

    /** The phone declares no font for the shown language, so its characters may take another region's forms. */
    val systemFontMissing: StateFlow<Boolean> = shown.language
        .map { language ->
            cache.systemFontMissing[language] ?: withContext(Dispatchers.IO) {
                language.support.systemFonts.sans.isNotEmpty() && systemFont(language) == null
            }.also { cache.systemFontMissing[language] = it }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, cache.systemFontMissing[shown.language.value] ?: false)

    private val mutableImport = MutableStateFlow<FontImport?>(null)

    /** The result of the last font file added, to report a file that could not be added. */
    val lastImport: StateFlow<FontImport?> = mutableImport

    /** An edit of [language]'s CSS. */
    private class CssEdit(val language: Language, val css: String)

    private val cssEdits = MutableStateFlow<CssEdit?>(null)

    /** The shown language's CSS being edited, which may be ahead of the saved setting; null before its first edit. */
    val cssDraft: String? get() = cssEdits.value?.takeIf { it.language == shown.language.value }?.css

    private val mutableResets = MutableStateFlow(0)

    /** Counts resets of the page, after which the CSS field takes [cssDraft] again. */
    val resets: StateFlow<Int> = mutableResets

    init {
        // The field edits its own copy; the setting follows once typing pauses.
        viewModelScope.launch {
            cssEdits.filterNotNull().debounce(CSS_SAVE_DELAY_MS).collect { repository.setCustomCss(it.language, it.css) }
        }
    }

    /** Shows [language]'s settings. CSS of the language shown before that is still waiting is saved at once. */
    fun show(language: Language) {
        cssEdits.value?.takeIf { it.language != language }?.let { pending ->
            launch { repository.setCustomCss(pending.language, pending.css) }
        }
        shown.show(language)
    }

    /** [language]'s saved CSS as last read, for the CSS field's first frame after the language changes. */
    fun savedCss(language: Language): String? = repository.cachedAppearance(language)?.customCss

    fun selectFont(id: String?) {
        val language = shown.language.value
        launch { repository.setFont(language, id) }
    }

    fun setFontForAllText(enabled: Boolean) {
        val language = shown.language.value
        launch { repository.setFontForAllText(language, enabled) }
    }

    fun setFontSize(size: Int) = launch { repository.setFontSize(size) }

    fun setTextWeight(weight: Int) = launch { repository.setTextWeight(weight) }

    fun setLetterThickness(thickness: Int) = launch { repository.setLetterThickness(thickness) }

    fun setCopyDefinitions(enabled: Boolean) = launch { repository.setCopyDefinitions(enabled) }

    fun setCopyMode(mode: DefinitionCopyMode) = launch { repository.setCopyMode(mode) }

    fun setShowSourceText(enabled: Boolean) = launch { overlaySettings.setShowSourceText(enabled) }

    fun setHideAfterAdd(enabled: Boolean) = launch { overlaySettings.setHideAfterAdd(enabled) }

    fun setTranslateButton(enabled: Boolean) = launch { translationSettings.setButton(enabled) }

    fun setHideOffWord(enabled: Boolean) = launch { overlaySettings.setHideOffWord(enabled) }

    fun download(font: CatalogFont) = fonts.download(font)

    /** Deletes [font]; a language that used it goes back to the phone's font. */
    fun delete(font: InstalledFont) = launch {
        Language.entries
            .filter { repository.appearance(it).first().fontId == font.id }
            .forEach { repository.setFont(it, null) }
        fonts.delete(font.id)
    }

    fun import(uri: Uri) {
        val language = shown.language.value
        launch {
            val result = fonts.import(uri)
            mutableImport.value = result
            if (result is FontImport.Added) repository.setFont(language, result.font.id)
        }
    }

    fun setCustomCss(css: String) {
        cssEdits.value = CssEdit(shown.language.value, css)
    }

    /** Resets the page's shared settings and the shown language's, the CSS being edited included. */
    fun resetSettings() {
        val language = shown.language.value
        // The default as the latest edit: a save of the old text still waiting would otherwise bring it back.
        cssEdits.value = CssEdit(language, PopupAppearance().customCss)
        viewModelScope.launch {
            withContext(NonCancellable) { settingsReset.reset(SettingsSection.POPUP, language) }
            mutableResets.update { it + 1 }
        }
    }

    override fun onCleared() {
        // An edit made within the save delay would go with the view model's scope.
        val pending = cssEdits.value ?: return
        if (pending.css != savedCss(pending.language)) {
            CoroutineScope(Dispatchers.IO).launch { repository.setCustomCss(pending.language, pending.css) }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private suspend fun loadTypefaces(installed: List<InstalledFont>, language: Language): Map<String, Typeface> =
        withContext(Dispatchers.IO) {
            val weight = PopupAppearance.NORMAL_WEIGHT
            val loaded = installed.mapNotNull { font -> cachedTypeface(language, font, weight)?.let { font.id to it } }
            loaded.toMap() + listOfNotNull(cachedTypeface(language, null, weight)?.let { SYSTEM to it })
        }

    /** The typefaces of the font list already built, so the list opens in its fonts; the rest follow. */
    private fun cachedTypefaces(installed: List<InstalledFont>, language: Language): Map<String, Typeface> {
        val weight = PopupAppearance.NORMAL_WEIGHT
        val fontsBuilt = installed.mapNotNull { font -> cache.byKey[key(language, font, weight)]?.let { font.id to it } }
        return fontsBuilt.toMap() + listOfNotNull(cache.byKey[key(language, null, weight)]?.let { SYSTEM to it })
    }

    /** The preview's typeface for [language]'s saved appearance, if already built. */
    private fun cachedPreview(language: Language): Typeface? {
        val appearance = repository.cachedAppearance(language) ?: return null
        val font = fonts.installed.value.firstOrNull { it.id == appearance.fontId }
        return cache.byKey[key(language, font, appearance.textWeight)]
    }

    /** [typeface] for [font] (the phone's for [language] when null) at [weight], built once while the app runs. */
    private fun cachedTypeface(language: Language, font: InstalledFont?, weight: Int): Typeface? {
        val key = key(language, font, weight)
        return cache.byKey[key] ?: typeface(sources(language, font), weight)?.also { cache.byKey[key] = it }
    }

    /** A font's files with their change times, so a file added again under the same name is read again. */
    private fun key(language: Language, font: InstalledFont?, weight: Int): String {
        val files = font?.files?.joinToString(",") { "${it.name}:${fonts.file(it).lastModified()}" } ?: "$SYSTEM${language.code}"
        return "${font?.id}|$files|$weight"
    }

    /** A font file and the CSS weight the page declares for it. */
    private class Source(val weight: String, val builder: () -> Font.Builder)

    /** The files of [font], or of the phone's sans-serif font for [language] when null (the page's default). */
    private fun sources(language: Language, font: InstalledFont?): List<Source> {
        if (font != null) return font.files.map { file -> Source(file.weight) { Font.Builder(fonts.file(file)) } }
        val system = systemFont(language) ?: return emptyList()
        val weight = SystemFontFiles.weightRange(system) ?: system.style.weight.toString()
        return listOf(Source(weight) { Font.Builder(system.buffer).setTtcIndex(system.ttcIndex) })
    }

    /**
     * [sources] as one family for [weight]: a variable font is set to it within its range, and a file with one weight
     * keeps its own. Files that cannot be read (such as web fonts) are left out; null if none can.
     */
    private fun typeface(sources: List<Source>, weight: Int): Typeface? {
        val loaded = sources.mapNotNull { source ->
            runCatching {
                val range = source.weight.split(' ').mapNotNull { it.toIntOrNull() }
                val builder = source.builder()
                if (range.size == 2) {
                    val used = weight.coerceIn(range[0], range[1])
                    builder.setWeight(used).setFontVariationSettings("'wght' $used")
                } else {
                    range.firstOrNull()?.takeIf { it in 1..1000 }?.let { builder.setWeight(it) }
                }
                builder.build()
            }.getOrNull()
        }
        // A family takes one font per style.
        val fonts = loaded.distinctBy { it.style }
        if (fonts.isEmpty()) return null
        return runCatching {
            val family = FontFamily.Builder(fonts.first()).apply { fonts.drop(1).forEach { addFont(it) } }.build()
            Typeface.CustomFallbackBuilder(family).build()
        }.getOrNull()
    }

    companion object {
        const val SYSTEM = ""
        const val CSS_SAVE_DELAY_MS = 400L

        /**
         * The phone's sans-serif font the page names for [language]; null when the page keeps the phone's default font
         * for it (Latin script) or the phone has none.
         */
        private fun systemFont(language: Language): Font? =
            language.support.takeIf { it.systemFonts.sans.isNotEmpty() }?.let { SystemFontFiles.find(it) }

        /**
         * Syntax problems of [css], the fonts it names that the page cannot show for [language], then the files it
         * loads from the internet.
         */
        fun cssIssues(css: String, language: Language, installed: List<InstalledFont>): List<CssCheck.Issue> {
            if (css.isBlank()) return emptyList()
            val result = CssCheck.analyze(css)
            val system = language.support.systemFonts
            return result.issues + CssCheck.unknownFonts(result) { PageFonts.isAvailable(it, system, installed) } +
                CssCheck.remoteFiles(css)
        }
    }
}
