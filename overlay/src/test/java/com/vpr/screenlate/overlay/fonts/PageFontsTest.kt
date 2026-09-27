package com.vpr.screenlate.overlay.fonts

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.JapaneseSupport
import com.vpr.screenlate.overlay.settings.PopupAppearance
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
        assertThat(PageFonts.fontFamily(klee)).isEqualTo("\"Klee One\", \"Screenlate Sans\", sans-serif")
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
        assertThat(json["preload"]?.jsonPrimitive?.content).isEqualTo("Klee One")
        assertThat(json["customCss"]?.jsonPrimitive?.content)
            .isEqualTo(".a { font-family: Meiryo, \"Klee One\", \"Screenlate Sans\", sans-serif }")
    }

    @Test
    fun `a missing chosen font falls back to the system font`() {
        val json = PageAppearance.build(JapaneseSupport, PopupAppearance(fontId = "gone"), emptyList())
        assertThat(json["fontFamily"]?.jsonPrimitive?.content).isEqualTo("\"Screenlate Sans\", sans-serif")
        assertThat(json.containsKey("preload")).isFalse()
        assertThat(json["customCss"]?.jsonPrimitive?.content).isEmpty()
    }
}
