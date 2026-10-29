package com.vpr.screenlate.settings

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.overlay.fonts.CatalogFont
import com.vpr.screenlate.overlay.fonts.CatalogFontFile
import com.vpr.screenlate.overlay.fonts.FontFile
import com.vpr.screenlate.overlay.fonts.InstalledFont
import org.junit.Test

class FontsForLanguageTest {

    private fun catalog(id: String, language: String?, subsets: List<String> = emptyList()) = CatalogFont(
        id = id,
        family = id,
        languages = listOfNotNull(language),
        subsets = subsets,
        files = listOf(CatalogFontFile("https://example.org/$id.ttf")),
        sizeMb = 1.0,
        license = "SIL OFL 1.1",
        homepage = "https://example.org",
    )

    private fun installed(id: String, catalogId: String?) = InstalledFont(id, id, listOf(FontFile("$id.ttf")), catalogId)

    private val catalog = listOf(
        catalog("klee-one", "ja"),
        catalog("noto-sans-sc", "zh"),
        catalog("lexend", null, listOf("latin", "latin-ext", "vietnamese")),
    )
    private val klee = installed("klee-one", "klee-one")
    private val notoSc = installed("noto-sans-sc", "noto-sans-sc")
    private val lexend = installed("lexend", "lexend")
    private val own = installed("own", null)
    private val dropped = installed("dropped", "no-longer-in-the-catalog")
    private val all = listOf(klee, notoSc, lexend, own, dropped)

    @Test
    fun `a language lists its own catalog's fonts, the user's files and fonts the catalog no longer has`() {
        assertThat(PopupAppearanceViewModel.fontsFor(Language.CHINESE, all, catalog, chosen = null))
            .containsExactly(notoSc, own, dropped).inOrder()
        assertThat(PopupAppearanceViewModel.fontsFor(Language.JAPANESE, all, catalog, chosen = null))
            .containsExactly(klee, own, dropped).inOrder()
        assertThat(PopupAppearanceViewModel.fontsFor(Language.ENGLISH, all, catalog, chosen = null))
            .containsExactly(lexend, own, dropped).inOrder()
    }

    @Test
    fun `a font offered by its letters shows for every language it covers`() {
        assertThat(PopupAppearanceViewModel.fontsFor(Language.FRENCH, all, catalog, chosen = null)).contains(lexend)
        assertThat(PopupAppearanceViewModel.fontsFor(Language.PORTUGUESE, all, catalog, chosen = null)).contains(lexend)
        assertThat(PopupAppearanceViewModel.fontsFor(Language.KOREAN, all, catalog, chosen = null)).doesNotContain(lexend)
    }

    @Test
    fun `the chosen font stays listed wherever it came from`() {
        assertThat(PopupAppearanceViewModel.fontsFor(Language.CHINESE, all, catalog, chosen = "klee-one"))
            .containsExactly(klee, notoSc, own, dropped).inOrder()
    }
}
