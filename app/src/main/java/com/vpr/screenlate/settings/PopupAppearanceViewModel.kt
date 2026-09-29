package com.vpr.screenlate.settings

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.overlay.fonts.CatalogFont
import com.vpr.screenlate.overlay.fonts.CssCheck
import com.vpr.screenlate.overlay.fonts.FontDownload
import com.vpr.screenlate.overlay.fonts.FontImport
import com.vpr.screenlate.overlay.fonts.InstalledFont
import com.vpr.screenlate.overlay.fonts.PageFonts
import com.vpr.screenlate.overlay.fonts.PopupFonts
import com.vpr.screenlate.overlay.fonts.SystemFontFiles
import com.vpr.screenlate.overlay.settings.DefinitionCopyMode
import com.vpr.screenlate.overlay.settings.PopupAppearance
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Font, text size and weight, custom CSS, and definition copying of the lookup page. */
@OptIn(FlowPreview::class)
@HiltViewModel
class PopupAppearanceViewModel @Inject constructor(
    private val repository: PopupAppearanceRepository,
    private val fonts: PopupFonts,
) : ViewModel() {
    val appearance: StateFlow<PopupAppearance?> =
        repository.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val installed: StateFlow<List<InstalledFont>> = fonts.installed
    val downloads: StateFlow<Map<String, FontDownload>> = fonts.downloads
    val catalog: List<CatalogFont> get() = fonts.catalog.filter { it.language == LANGUAGE.support.languageTag }

    /** Typefaces for the font list: installed fonts by id, the phone's font for the language under [SYSTEM]. */
    val typefaces: StateFlow<Map<String, Typeface>> = fonts.installed
        .map { installed -> loadTypefaces(installed) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * The preview's typeface: the chosen font, or the phone's, with a variable font set to the text weight. The
     * preview asks it for that weight, so a font with one weight is made bold from 600 on, as on the page.
     */
    val previewTypeface: StateFlow<Typeface?> =
        combine(repository.appearance, fonts.installed) { appearance, installed ->
            installed.firstOrNull { it.id == appearance.fontId } to appearance.textWeight
        }
            .distinctUntilChanged()
            .map { (font, weight) -> withContext(Dispatchers.IO) { typeface(sources(font), weight) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The phone declares no font for the language, so its kanji may take another region's forms. */
    val systemFontMissing: StateFlow<Boolean> =
        flow { emit(withContext(Dispatchers.IO) { SystemFontFiles.find(LANGUAGE.support) == null }) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val mutableImport = MutableStateFlow<FontImport?>(null)

    /** The result of the last font file added, to report a file that could not be added. */
    val lastImport: StateFlow<FontImport?> = mutableImport

    private val cssEdits = MutableStateFlow<String?>(null)

    /** The CSS being edited, which may be ahead of the saved setting; null before the first edit. */
    val cssDraft: String? get() = cssEdits.value

    init {
        // The field edits its own copy; the setting follows once typing pauses.
        viewModelScope.launch { cssEdits.filterNotNull().debounce(CSS_SAVE_DELAY_MS).collect { repository.setCustomCss(it) } }
    }

    fun selectFont(id: String?) = launch { repository.setFont(id) }

    fun setFontForAllText(enabled: Boolean) = launch { repository.setFontForAllText(enabled) }

    fun setFontSize(size: Int) = launch { repository.setFontSize(size) }

    fun setTextWeight(weight: Int) = launch { repository.setTextWeight(weight) }

    fun setLetterThickness(thickness: Int) = launch { repository.setLetterThickness(thickness) }

    fun setCopyDefinitions(enabled: Boolean) = launch { repository.setCopyDefinitions(enabled) }

    fun setCopyMode(mode: DefinitionCopyMode) = launch { repository.setCopyMode(mode) }

    fun download(font: CatalogFont) = fonts.download(font)

    fun delete(font: InstalledFont) = launch {
        if (appearance.value?.fontId == font.id) repository.setFont(null)
        fonts.delete(font.id)
    }

    fun import(uri: Uri) = launch {
        val result = fonts.import(uri)
        mutableImport.value = result
        if (result is FontImport.Added) repository.setFont(result.font.id)
    }

    fun setCustomCss(css: String) {
        cssEdits.value = css
    }

    override fun onCleared() {
        // An edit made within the save delay would go with the view model's scope.
        val pending = cssEdits.value ?: return
        if (pending != appearance.value?.customCss) CoroutineScope(Dispatchers.IO).launch { repository.setCustomCss(pending) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private suspend fun loadTypefaces(installed: List<InstalledFont>): Map<String, Typeface> =
        withContext(Dispatchers.IO) {
            val loaded = installed.mapNotNull { font -> typeface(sources(font), PopupAppearance.NORMAL_WEIGHT)?.let { font.id to it } }
            loaded.toMap() + listOfNotNull(typeface(sources(null), PopupAppearance.NORMAL_WEIGHT)?.let { SYSTEM to it })
        }

    /** A font file and the CSS weight the page declares for it. */
    private class Source(val weight: String, val builder: () -> Font.Builder)

    /** The files of [font], or of the phone's sans-serif font for the language when null (the page's default). */
    private fun sources(font: InstalledFont?): List<Source> {
        if (font != null) return font.files.map { file -> Source(file.weight) { Font.Builder(fonts.file(file)) } }
        val system = SystemFontFiles.find(LANGUAGE.support) ?: return emptyList()
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
        val LANGUAGE = Language.JAPANESE

        /** Syntax problems of [css], then the fonts it names that the page cannot show. */
        fun cssIssues(css: String, installed: List<InstalledFont>): List<CssCheck.Issue> {
            if (css.isBlank()) return emptyList()
            val result = CssCheck.analyze(css)
            val system = LANGUAGE.support.systemFonts
            return result.issues + CssCheck.unknownFonts(result) { PageFonts.isAvailable(it, system, installed) }
        }
    }
}
