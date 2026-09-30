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
    fun `counts lines across blank lines and comments`() {
        assertThat(problems("}\n}")).containsExactly(1 to Problem.UNEXPECTED_BRACE, 2 to Problem.UNEXPECTED_BRACE)
        assertThat(problems("\n\n/* open")).containsExactly(3 to Problem.UNCLOSED_COMMENT)
        assertThat(problems("/* a\nb */\n\n.a {\n color red;\n}")).containsExactly(5 to Problem.MISSING_COLON)
        val long = ".a { color: red; }\n".repeat(5000) + "}"
        assertThat(problems(long)).containsExactly(5001 to Problem.UNEXPECTED_BRACE)
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

    @Test
    fun `finds files loaded from the internet with their lines and hosts`() {
        val css = listOf(
            "@import url(\"https://Fonts.Example.com/css?family=A\");",
            ".a { background: url( 'http://img.example.org:8080/x.png' ) }",
            "@import '//cdn.example.net/b.css';",
            ".b { background: url(//img.example.org/y.png) }",
        ).joinToString("\n")
        assertThat(CssCheck.remoteFiles(css)).containsExactly(
            CssCheck.Issue(1, Problem.REMOTE_FILE, "fonts.example.com"),
            CssCheck.Issue(2, Problem.REMOTE_FILE, "img.example.org"),
            CssCheck.Issue(3, Problem.REMOTE_FILE, "cdn.example.net"),
            CssCheck.Issue(4, Problem.REMOTE_FILE, "img.example.org"),
        ).inOrder()
    }

    @Test
    fun `ignores local files, data and commented out urls`() {
        val css = listOf(
            "/* old: url(https://a.example.com/x.png)",
            "   @import 'https://b.example.com/c.css'; */",
            ".a { background: url(data:image/png;base64,AAAA) }",
            ".b { background: url(img/x.png); src: local(\"X\") }",
            ".c { background: url(https://c.example.com/y.png) }",
        ).joinToString("\n")
        assertThat(CssCheck.remoteFiles(css)).containsExactly(CssCheck.Issue(5, Problem.REMOTE_FILE, "c.example.com"))
    }

    @Test
    fun `finds addresses written with escapes, backslashes and tabs`() {
        val css = """
            .a { background: url(\68ttps://a.example.com/x.png) }
            @font-face { font-family: X; src: url("htt\70s://b.example.com/x.woff") }
            .c { background: url(https:\2f\2f c.example.com/y.png) }
            .d { background: \75rl(https://d.example.com/y.png) }
            @\69mport "https://e.example.com/e.css";
            .f { background: url("https:\\\\f.example.com/f.png") }
            .g { background: url("htt\9 ps:/\9/g.example.com/g.png") }
            .h { background: url(https://user@H.example.com:8443/h.png) }
        """.trimIndent()
        assertThat(CssCheck.remoteFiles(css)).containsExactly(
            CssCheck.Issue(1, Problem.REMOTE_FILE, "a.example.com"),
            CssCheck.Issue(2, Problem.REMOTE_FILE, "b.example.com"),
            CssCheck.Issue(3, Problem.REMOTE_FILE, "c.example.com"),
            CssCheck.Issue(4, Problem.REMOTE_FILE, "d.example.com"),
            CssCheck.Issue(5, Problem.REMOTE_FILE, "e.example.com"),
            CssCheck.Issue(6, Problem.REMOTE_FILE, "f.example.com"),
            CssCheck.Issue(7, Problem.REMOTE_FILE, "g.example.com"),
            CssCheck.Issue(8, Problem.REMOTE_FILE, "h.example.com"),
        ).inOrder()
    }

    @Test
    fun `a comment marker inside a string or after an escaped quote hides nothing`() {
        val css = listOf(
            ".a::before { content: \"/*\" }",
            ".b { background: url(https://a.example.com/x.png) }",
            ".c\\\" { color: red } .d::after { content: \"/*\" }",
            ".e { background: url(https://b.example.com/y.png) } /* \" */",
        ).joinToString("\n")
        assertThat(CssCheck.remoteFiles(css)).containsExactly(
            CssCheck.Issue(2, Problem.REMOTE_FILE, "a.example.com"),
            CssCheck.Issue(4, Problem.REMOTE_FILE, "b.example.com"),
        ).inOrder()
    }

    @Test
    fun `a string continued over a line break or an unquoted address hides no comment start`() {
        // A hex escape takes the line break after it, and `\` before CRLF continues the string, so `/*` is text.
        val continued = listOf(".a::before { content: \"x\\41\n/*\" }", ".a::before { content: \"x\\\r\n/*\" }")
        for (string in continued) {
            val css = "$string\n.b { background: url(https://a.example.com/x.png) }\n/* */"
            assertThat(CssCheck.remoteFiles(css)).containsExactly(CssCheck.Issue(3, Problem.REMOTE_FILE, "a.example.com"))
        }
        val inUrl = listOf(
            ".x { background: url(/*) }",
            "@font-face { font-family: Q; src: url(https://b.example.com/q.woff) }",
            ".y { background: url(*/) }",
        ).joinToString("\n")
        assertThat(CssCheck.remoteFiles(inUrl)).containsExactly(CssCheck.Issue(2, Problem.REMOTE_FILE, "b.example.com"))
        assertThat(CssCheck.analyze(".a { background: url(img/*.png) }").issues).isEmpty()
    }

    @Test
    fun `finds addresses split by escaped or other line breaks and escaped whitespace`() {
        val css = listOf(
            ".a { background: url(\"htt\\\r\nps://a.example.com/x.png\") }",
            ".b { background: url(\"htt\\\u000Cps://b.example.com/x.png\") }",
            ".c { background: url(htt\\70\r\ns://c.example.com/x.png) }",
            ".d { background: url(htt\\9 ps://d.example.com/x.png) }",
            ".e { background: url(https:/\\A /e.example.com/x.png) }",
        ).joinToString("\n")
        assertThat(CssCheck.remoteFiles(css).map { it.detail })
            .containsExactly("a.example.com", "b.example.com", "c.example.com", "d.example.com", "e.example.com")
            .inOrder()
    }

    @Test
    fun `escaped and one-slash addresses on the page itself are not remote`() {
        val css = """
            .a { background: url(\2f img/x.png) }
            .b { background: url(https:/img/y.png); src: url(https:img/z.png) }
            .c { background: url("data:image/png;base64,Ly9h") }
        """.trimIndent()
        assertThat(CssCheck.remoteFiles(css)).isEmpty()
    }
}
