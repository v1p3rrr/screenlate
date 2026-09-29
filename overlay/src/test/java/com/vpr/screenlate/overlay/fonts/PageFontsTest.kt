package com.vpr.screenlate.overlay.fonts

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.JapaneseSupport
import com.vpr.screenlate.overlay.settings.PopupAppearance
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class PageFontsTest {

    private val system = JapaneseSupport.systemFonts
    private val klee = InstalledFont(
        id = "klee-one",
        family = "Klee One",
        files = listOf(FontFile("klee-one-0.ttf", "400"), FontFile("klee-one-1.ttf", "600")),
        catalogId = "klee-one",
    )
    private val notoSans = InstalledFont("noto-sans-jp", "Noto Sans JP", listOf(FontFile("noto-sans-jp-0.ttf", "100 900")))

    @Test
    fun `the system font is limited to the script`() {
        val css = PageFonts.fontFaces(system, emptyList())
        assertThat(css).contains(
            "@font-face { font-family: \"Screenlate Sans\"; src: local(\"NotoSansCJKjp-Regular\"), local(\"Noto Sans CJK JP Regular\")",
        )
        assertThat(css.lines().first()).contains("unicode-range: U+2E80-2FDF")
        assertThat(css.lines().first()).doesNotContain("font-weight")
    }

    @Test
    fun `a variable system font declares its weight range`() {
        val css = PageFonts.fontFaces(system, emptyList(), weights = PageFonts.SystemWeights(sans = "100 900"))
        assertThat(css.lines()[0]).contains("font-weight: 100 900; unicode-range: U+2E80-2FDF")
        assertThat(css.lines()[1]).contains("Screenlate Serif")
        assertThat(css.lines()[1]).doesNotContain("font-weight")
    }

    @Test
    fun `windows font names stand for the system fonts`() {
        val css = PageFonts.fontFaces(system, emptyList())
        assertThat(css).contains("font-family: \"Meiryo\"; src: local(\"Meiryo\"), local(\"NotoSansCJKjp-Regular\")")
        assertThat(css).contains("font-family: \"Yu Mincho\"; src: local(\"Yu Mincho\"), local(\"NotoSerifCJKjp-Regular\")")
    }

    @Test
    fun `installed fonts are declared by file and replace aliases of the same name`() {
        val css = PageFonts.fontFaces(system, listOf(klee, notoSans))
        assertThat(css).contains("font-family: \"Klee One\"; src: url(\"/fonts/klee-one-0.ttf\"); font-weight: 400;")
        assertThat(css).contains("font-family: \"Klee One\"; src: url(\"/fonts/klee-one-1.ttf\"); font-weight: 600;")
        assertThat(css).doesNotContain("font-family: \"Noto Sans JP\"; src: local(")
        assertThat(css).contains("font-family: \"Noto Sans JP\"; src: url(\"/fonts/noto-sans-jp-0.ttf\"); font-weight: 100 900;")
    }

    @Test
    fun `the page font list starts with the chosen font`() {
        assertThat(PageFonts.fontFamily(null)).isEqualTo("\"Screenlate Sans\", sans-serif")
        assertThat(PageFonts.fontFamily(klee)).isEqualTo("\"Screenlate Chosen\", \"Screenlate Sans\", sans-serif")
    }

    @Test
    fun `another font of the same family does not take the chosen font's place`() {
        val own = InstalledFont("file-1", "Klee One", listOf(FontFile("file-1-a.ttf", "700")))
        val css = PageFonts.fontFaces(system, listOf(klee, own), chosen = own)
        val chosen = css.lines().filter { it.contains("\"Screenlate Chosen\"") }
        assertThat(chosen).hasSize(1)
        assertThat(chosen.single()).contains("url(\"/fonts/file-1-a.ttf\"); font-weight: 700; font-display")
    }

    @Test
    fun `names and weights cannot leave their css values`() {
        assertThat(PageFonts.quote("A\u0000B\nC}")).isEqualTo("\"ABC}\"")
        assertThat(PageFonts.isWeight("400")).isTrue()
        assertThat(PageFonts.isWeight("100 900")).isTrue()
        assertThat(PageFonts.isWeight("400; } body { color: red")).isFalse()
        assertThat(PageFonts.isWeight("")).isFalse()
        assertThat(PageFonts.isWeight("0")).isFalse()
        assertThat(PageFonts.isWeight("bold")).isFalse()
    }

    @Test
    fun `knows generic, android, alias and installed names`() {
        fun available(name: String) = PageFonts.isAvailable(name, system, listOf(klee))
        assertThat(available("sans-serif")).isTrue()
        assertThat(available("Arial")).isTrue()
        assertThat(available("times  new roman")).isTrue()
        assertThat(available("yu gothic")).isTrue()
        assertThat(available("游明朝")).isTrue()
        assertThat(available("Klee One")).isTrue()
        assertThat(available("Comic Sans MS")).isFalse()
        assertThat(PageFonts.quote("A\"B\\")).isEqualTo("\"A\\\"B\\\\\"")
    }

    @Test
    fun `page appearance appends the fallback to custom css`() {
        val appearance = PopupAppearance(fontId = "klee-one", fontSize = 17, customCss = ".a { font-family: Meiryo }")
        val json = PageAppearance.build(JapaneseSupport, appearance, listOf(klee))
        assertThat(json["lang"]?.jsonPrimitive?.content).isEqualTo("ja")
        assertThat(json["fontSize"]?.jsonPrimitive?.int).isEqualTo(17)
        assertThat(json["preload"]?.jsonPrimitive?.content).isEqualTo("Screenlate Chosen")
        assertThat(json["preloadText"]?.jsonPrimitive?.content).isEqualTo(JapaneseSupport.fontSample)
        assertThat(json["customCss"]?.jsonPrimitive?.content)
            .isEqualTo(".a { font-family: Meiryo, \"Screenlate Chosen\", \"Screenlate Sans\", sans-serif }")
    }

    @Test
    fun `a chosen font is limited to the script unless it is for all text`() {
        val limited = PageAppearance.build(JapaneseSupport, PopupAppearance(fontId = "klee-one"), listOf(klee))
        val faces = limited["fontFaces"]?.jsonPrimitive?.content.orEmpty()
        assertThat(faces).contains(
            "font-family: \"Screenlate Chosen\"; src: url(\"/fonts/klee-one-1.ttf\"); font-weight: 600; unicode-range: U+2E80-2FDF",
        )
        assertThat(faces).contains("font-family: \"Klee One\"; src: url(\"/fonts/klee-one-0.ttf\"); font-weight: 400; font-display")
        assertThat(limited["fontFamily"]?.jsonPrimitive?.content).isEqualTo("\"Screenlate Chosen\", \"Screenlate Sans\", sans-serif")

        val all = PageAppearance.build(JapaneseSupport, PopupAppearance(fontId = "klee-one", fontForAllText = true), listOf(klee))
        assertThat(all["fontFaces"]?.jsonPrimitive?.content)
            .contains("font-family: \"Screenlate Chosen\"; src: url(\"/fonts/klee-one-0.ttf\"); font-weight: 400; font-display")
        assertThat(all["fontFamily"]?.jsonPrimitive?.content).isEqualTo("\"Screenlate Chosen\", \"Screenlate Sans\", sans-serif")
        assertThat(all["preload"]?.jsonPrimitive?.content).isEqualTo("Screenlate Chosen")
    }

    @Test
    fun `heavier text follows the font switch`() {
        val heavier = PopupAppearance(fontId = "klee-one", textWeight = 600, letterThickness = 3)
        val script = PageAppearance.build(JapaneseSupport, heavier, listOf(klee))
        assertThat(script["textWeight"]?.jsonPrimitive?.int).isEqualTo(600)
        assertThat(script["textStroke"]?.jsonPrimitive?.double).isWithin(1e-9).of(0.03)
        assertThat(script["textScope"]?.jsonPrimitive?.content).isEqualTo("script")
        assertThat(script["scriptPattern"]?.jsonPrimitive?.content).startsWith("[\\u{2E80}-\\u{2FDF}\\u{3000}-\\u{30FF}")

        val all = PageAppearance.build(JapaneseSupport, heavier.copy(fontForAllText = true), listOf(klee))
        assertThat(all["textScope"]?.jsonPrimitive?.content).isEqualTo("all")
        // The phone's font is always limited to the script, and so is the weight with it.
        val system = PageAppearance.build(JapaneseSupport, heavier.copy(fontId = null, fontForAllText = true), listOf(klee))
        assertThat(system["textScope"]?.jsonPrimitive?.content).isEqualTo("script")
    }

    @Test
    fun `unicode ranges become a character class`() {
        assertThat(PageAppearance.scriptPattern("U+3000-30FF, U+4E00, U+4??, U+20000-2FA1F"))
            .isEqualTo("[\\u{3000}-\\u{30FF}\\u{4E00}\\u{20000}-\\u{2FA1F}]")
    }

    @Test
    fun `text weight and thickness keep to their steps`() {
        assertThat(PopupAppearance.textWeight(350)).isEqualTo(400)
        assertThat(PopupAppearance.textWeight(640)).isEqualTo(600)
        assertThat(PopupAppearance.textWeight(650)).isEqualTo(700)
        assertThat(PopupAppearance.textWeight(900)).isEqualTo(700)
        assertThat(PopupAppearance.letterThickness(-1)).isEqualTo(0)
        assertThat(PopupAppearance.letterThickness(9)).isEqualTo(PopupAppearance.MAX_THICKNESS)
    }

    @Test
    fun `a missing chosen font falls back to the system font`() {
        val json = PageAppearance.build(JapaneseSupport, PopupAppearance(fontId = "gone"), emptyList())
        assertThat(json["fontFamily"]?.jsonPrimitive?.content).isEqualTo("\"Screenlate Sans\", sans-serif")
        assertThat(json.containsKey("preload")).isFalse()
        assertThat(json["customCss"]?.jsonPrimitive?.content).isEmpty()
    }
}
