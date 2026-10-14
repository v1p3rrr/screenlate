package com.vpr.screenlate.settings

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.overlay.fonts.CatalogFont
import com.vpr.screenlate.overlay.fonts.CatalogFontFile
import com.vpr.screenlate.overlay.fonts.FontFile
import com.vpr.screenlate.overlay.fonts.InstalledFont
import org.junit.Test

class FontsForLanguageTest {

    private fun catalog(id: String, language: String) = CatalogFont(
        id = id,
        family = id,
        language = language,
        files = listOf(CatalogFontFile("https://example.org/$id.ttf")),
        sizeMb = 1.0,
        license = "SIL OFL 1.1",
        homepage = "https://example.org",
    )

    private fun installed(id: String, catalogId: String?) = InstalledFont(id, id, listOf(FontFile("$id.ttf")), catalogId)

    private val catalog = listOf(catalog("klee-one", "ja"), catalog("noto-sans-sc", "zh"))
    private val klee = installed("klee-one", "klee-one")
    private val notoSc = installed("noto-sans-sc", "noto-sans-sc")
    private val own = installed("own", null)
    private val dropped = installed("dropped", "no-longer-in-the-catalog")
    private val all = listOf(klee, notoSc, own, dropped)

    @Test
    fun `a language lists its own catalog's fonts, the user's files and fonts the catalog no longer has`() {
        assertThat(PopupAppearanceViewModel.fontsFor(Language.CHINESE, all, catalog, chosen = null))
            .containsExactly(notoSc, own, dropped).inOrder()
        assertThat(PopupAppearanceViewModel.fontsFor(Language.JAPANESE, all, catalog, chosen = null))
            .containsExactly(klee, own, dropped).inOrder()
        assertThat(PopupAppearanceViewModel.fontsFor(Language.ENGLISH, all, catalog, chosen = null))
            .containsExactly(own, dropped).inOrder()
    }

    @Test
    fun `the chosen font stays listed wherever it came from`() {
        assertThat(PopupAppearanceViewModel.fontsFor(Language.CHINESE, all, catalog, chosen = "klee-one"))
            .containsExactly(klee, notoSc, own, dropped).inOrder()
    }
}
