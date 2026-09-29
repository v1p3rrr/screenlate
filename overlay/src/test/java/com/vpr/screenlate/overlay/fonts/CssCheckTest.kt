package com.vpr.screenlate.overlay.fonts

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.overlay.fonts.CssCheck.Problem
import org.junit.Test

class CssCheckTest {

    private fun problems(css: String) = CssCheck.analyze(css).issues.map { it.line to it.problem }

    @Test
    fun `valid css has no issues`() {
        val css = """
            /* comment with { and ; */
            @import url("x.css");
            .a { color: red; background: url(data:image/png;base64,AAA=) }
            @media (max-width: 400px) { .b { font-size: 12px } }
            :root { --empty: ; --tag-text-color: #131313 }
            .c::before { content: "}" }
        """.trimIndent()
        assertThat(CssCheck.analyze(css).issues).isEmpty()
    }

    @Test
    fun `reports stray and missing braces with lines`() {
        assertThat(problems(".a { color: red; }\n}\n\n}")).containsExactly(2 to Problem.UNEXPECTED_BRACE, 4 to Problem.UNEXPECTED_BRACE)
        assertThat(problems(".a {\n color: red;\n")).containsExactly(1 to Problem.UNCLOSED_BLOCK)
    }

    @Test
    fun `reports broken declarations`() {
        assertThat(problems(".a {\n color red;\n}")).containsExactly(2 to Problem.MISSING_COLON)
        assertThat(problems(".a { color: ; }")).containsExactly(1 to Problem.EMPTY_VALUE)
        assertThat(problems(".a { color: !important }")).containsExactly(1 to Problem.EMPTY_VALUE)
        assertThat(problems(".a { my color: red }")).containsExactly(1 to Problem.BAD_PROPERTY)
        assertThat(problems("color: red;\n.a { }")).containsExactly(1 to Problem.OUTSIDE_RULE)
    }

    @Test
    fun `reports unclosed comments and strings`() {
        assertThat(problems(".a { color: red }\n/* open")).containsExactly(2 to Problem.UNCLOSED_COMMENT)
        assertThat(problems(".a {\n content: \"open\n}")).contains(2 to Problem.UNCLOSED_STRING)
    }

    @Test
    fun `reads font families and flags empty entries`() {
        val css = ".a {\nfont-family: Yu Gothic,  \"MS  Mincho\" , 'A\\'B' !important;\n}\n.b { font-family: Yu Gothic,  !important; }"
        val result = CssCheck.analyze(css)
        assertThat(result.fontFamilies.map { it.names }).containsExactly(
            listOf("Yu Gothic", "MS  Mincho", "A'B"),
            listOf("Yu Gothic"),
        ).inOrder()
        assertThat(result.issues).containsExactly(CssCheck.Issue(4, Problem.EMPTY_FONT_NAME, "Yu Gothic,"))
        assertThat(result.fontFamilies.map { it.appendable }).containsExactly(true, false).inOrder()
    }

    @Test
    fun `appends the fallback before important`() {
        val css = ".a { font-family: Arial !important; }\n.b { FONT-FAMILY:\"Yu Mincho\" }\n.c { font-family: inherit }"
        val result = CssCheck.analyze(css)
        assertThat(CssCheck.withFallback(css, result.fontFamilies, "\"X\", sans-serif")).isEqualTo(
            ".a { font-family: Arial, \"X\", sans-serif !important; }\n.b { FONT-FAMILY:\"Yu Mincho\", \"X\", sans-serif }\n.c { font-family: inherit }",
        )
    }

    @Test
    fun `lists each unknown font once`() {
        val css = ".a { font-family: Meiryo, Nope, serif }\n.b { font-family: nope, var(--f) }"
        val unknown = CssCheck.unknownFonts(CssCheck.analyze(css)) { it == "Meiryo" || it == "serif" }
        assertThat(unknown).containsExactly(CssCheck.Issue(1, Problem.UNKNOWN_FONT, "Nope"))
    }

    @Test
    fun `leaves font-face rules alone and knows the fonts they define`() {
        val css = "@font-face { font-family: \"My Font\"; src: local(\"X\") }\n" +
            "@media (min-width: 1px) { @font-face { font-family: Other; src: url(o.woff2) } }\n" +
            ".a { font-family: \"My Font\", Missing }\n.b { font-family: other }"
        val result = CssCheck.analyze(css)
        assertThat(result.issues).isEmpty()
        assertThat(result.declaredFonts).containsExactly("My Font", "Other").inOrder()
        assertThat(result.fontFamilies.map { it.names }).containsExactly(listOf("My Font", "Missing"), listOf("other")).inOrder()
        assertThat(CssCheck.withFallback(css, result.fontFamilies, "sans-serif")).isEqualTo(
            "@font-face { font-family: \"My Font\"; src: local(\"X\") }\n" +
                "@media (min-width: 1px) { @font-face { font-family: Other; src: url(o.woff2) } }\n" +
                ".a { font-family: \"My Font\", Missing, sans-serif }\n.b { font-family: other, sans-serif }",
        )
        val unknown = CssCheck.unknownFonts(result) { false }
        assertThat(unknown).containsExactly(CssCheck.Issue(3, Problem.UNKNOWN_FONT, "Missing"))
    }
}
