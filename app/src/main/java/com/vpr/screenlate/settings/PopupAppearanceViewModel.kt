package com.vpr.screenlate.settings

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.graphics.fonts.SystemFonts
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
import com.vpr.screenlate.overlay.settings.PopupAppearance
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Font, text size and custom CSS of the lookup page. */
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

    /** Typefaces for previews: installed fonts by id, the phone's font for the language under [SYSTEM]. */
    val typefaces: StateFlow<Map<String, Typeface>> = fonts.installed
        .map { installed -> loadTypefaces(installed) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The phone declares no font for the language, so its kanji may take another region's forms. */
    val systemFontMissing: StateFlow<Boolean> = flow { emit(withContext(Dispatchers.IO) { systemTypeface() == null }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val mutableImport = MutableStateFlow<FontImport?>(null)

    /** The result of the last font file added, to report a file that is not a font. */
    val lastImport: StateFlow<FontImport?> = mutableImport

    private val cssEdits = MutableStateFlow<String?>(null)

    init {
        // The field edits its own copy; the setting follows once typing pauses.
        viewModelScope.launch { cssEdits.filterNotNull().debounce(CSS_SAVE_DELAY_MS).collect { repository.setCustomCss(it) } }
    }

    fun selectFont(id: String?) = launch { repository.setFont(id) }

    fun setFontSize(size: Int) = launch { repository.setFontSize(size) }

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

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private suspend fun loadTypefaces(installed: List<InstalledFont>): Map<String, Typeface> =
        withContext(Dispatchers.IO) {
            val loaded = installed.mapNotNull { font ->
                val file = fonts.file(font) ?: return@mapNotNull null
                runCatching { Typeface.Builder(file).build() }.getOrNull()?.let { font.id to it }
            }.toMap()
            loaded + listOfNotNull(systemTypeface()?.let { SYSTEM to it })
        }

    /** The phone's regular font declared for the language, as the page asks for it. */
    private fun systemTypeface(): Typeface? = runCatching {
        val tag = LANGUAGE.support.languageTag
        val font = SystemFonts.getAvailableFonts()
            .filter { font -> (0 until font.localeList.size()).any { font.localeList[it].language == tag } }
            // The page's default is the sans-serif face.
            .minWithOrNull(
                compareBy<Font>({ it.file?.name?.contains("Serif") == true }, { abs(it.style.weight - FontStyle.FONT_WEIGHT_NORMAL) }),
            )
            ?: return null
        Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build()).build()
    }.getOrNull()

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
