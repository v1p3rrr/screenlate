package com.vpr.screenlate.core.translate

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertThrows
import org.junit.Test

/** Answers as the services gave them on 2026-10-09, shortened. */
class AnswerParsingTest {
    @Test
    fun `Google joins the translated sentences`() {
        val body = """[[["Погода сегодня хорошая. ","今日はいい天気ですね。",null,null,3,null,null,[[]],[[["8610","ja_en_2023q1.md"]]]],""" +
            """["Пойдем гулять.","散歩に行きましょう。",null,null,3]],null,"ja",null,null,null,null,[]]"""
        assertThat(GoogleTranslator.parse(body)).isEqualTo("Погода сегодня хорошая. Пойдем гулять.")
    }

    @Test
    fun `Microsoft's answer, with and without a transliteration`() {
        val bing = """[{"translations":[{"text":"Сегодня хорошая погода.","to":"ru","transliteration":{"text":"Segodnya","script":"Latn"}}],""" +
            """"usedLLM":true,"detectedLanguage":{"language":"ja"}},{"inputTransliteration":"Kyou wa","script":"Latn"}]"""
        assertThat(MicrosoftAnswer.parse(bing)).isEqualTo("Сегодня хорошая погода.")
        val edge = """[{"translations":[{"text":"Сегодня хорошая погода.","to":"ru","sentLen":{"srcSentLen":[11],"transSentLen":[24]}}]}]"""
        assertThat(MicrosoftAnswer.parse(edge)).isEqualTo("Сегодня хорошая погода.")
    }

    @Test
    fun `an answer of another shape is a bad answer`() {
        for (body in listOf("<html>", "{}", "[]", "[{}]", """[{"translations":[]}]""", """[{"translations":[{"to":"ru"}]}]""")) {
            val error = assertThrows(TranslationException::class.java) { MicrosoftAnswer.parse(body) }
            assertThat(error.error.kind).isEqualTo(TranslationError.Kind.BAD_ANSWER)
        }
        for (body in listOf("<html>", "[]", "[null]", "{}")) {
            val error = assertThrows(TranslationException::class.java) { GoogleTranslator.parse(body) }
            assertThat(error.error.kind).isEqualTo(TranslationError.Kind.BAD_ANSWER)
        }
    }

    @Test
    fun `Bing's token page`() {
        val page = """<div id="rich_tta" data-iid="translator.5023"></div><script>_G={IG:"2F1E0C",EF:{}};""" +
            """var params_AbusePreventionHelper = [1791500740887,"KAnzVAfT-ken",3600000];</script>"""
        val token = BingTranslator.parseTokenPage(page, "https://cn.bing.com/translator?x=1".toHttpUrl(), now = 1_000)
        assertThat(token.origin.toString()).isEqualTo("https://cn.bing.com/")
        assertThat(token.ig).isEqualTo("2F1E0C")
        assertThat(token.iid).isEqualTo("translator.5023")
        assertThat(token.key).isEqualTo("1791500740887")
        assertThat(token.token).isEqualTo("KAnzVAfT-ken")
        // A minute before the hour is up.
        assertThat(token.expiresAt).isEqualTo(1_000 + 3_600_000 - 60_000)
    }

    @Test
    fun `a token page without the token is a bad answer`() {
        val error = assertThrows(TranslationException::class.java) {
            BingTranslator.parseTokenPage("""<div data-iid="translator.1"></div>IG:"A"""", "https://www.bing.com/".toHttpUrl(), 0)
        }
        assertThat(error.error.kind).isEqualTo(TranslationError.Kind.BAD_ANSWER)
    }

    @Test
    fun `Bing's captcha and refused token`() {
        val captcha = assertThrows(TranslationException::class.java) { BingTranslator.parseTranslation("""{"ShowCaptcha":true}""") }
        assertThat(captcha.error.kind).isEqualTo(TranslationError.Kind.CAPTCHA)
        assertThrows(BingTranslator.TokenRejectedException::class.java) {
            BingTranslator.parseTranslation("""{"statusCode":205,"errorMessage":""}""")
        }
    }
}
