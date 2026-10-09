package com.vpr.screenlate.core.translate

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import java.io.IOException
import java.net.UnknownHostException
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test

class SentenceTranslatorTest {
    private val calls = Collections.synchronizedList(mutableListOf<String>())

    /** Answers with [answer] after [delayMs], or fails with [failure]. */
    private inner class FakeTranslator(
        override val service: TranslationService,
        var answer: (String) -> String = { "${service.label}: $it" },
        var delayMs: Long = 0,
        var failure: Exception? = null,
        val supported: Set<String>? = null,
        override val maxLength: Int = 100,
    ) : Translator {
        val count = AtomicInteger()

        override fun code(language: TranslationLanguage): String? =
            language.tag.takeIf { supported == null || it in supported }

        override suspend fun translate(text: String, from: String, to: String): String {
            count.incrementAndGet()
            calls += "$service $from>$to"
            delay(delayMs)
            failure?.let { throw it }
            return answer(text)
        }
    }

    private val bing = FakeTranslator(TranslationService.BING)
    private val google = FakeTranslator(TranslationService.GOOGLE)
    private val edge = FakeTranslator(TranslationService.EDGE)
    private val settings = TranslationSettingsRepository(MemoryDataStore())
    private var locale = Locale.forLanguageTag("ru")

    private fun translator(timeoutMs: Long = 200) =
        SentenceTranslator(listOf(bing, google, edge), settings, { locale }, serviceTimeoutMs = timeoutMs, testTimeoutMs = 500)

    @Test
    fun `the first service translates into the interface language`() = runBlocking<Unit> {
        val result = translator().translate("文", Language.JAPANESE)
        assertThat(result).isEqualTo(TranslationResult.Success("Bing: 文", TranslationService.BING))
        assertThat(calls).containsExactly("BING ja>ru")
    }

    @Test
    fun `the next service takes over on a failure and after the timeout`() = runBlocking<Unit> {
        bing.failure = UnknownHostException("www.bing.com")
        google.delayMs = 1_000
        val result = translator().translate("文", Language.JAPANESE)
        assertThat(result).isEqualTo(TranslationResult.Success("Edge: 文", TranslationService.EDGE))
        assertThat(calls).containsExactly("BING ja>ru", "GOOGLE ja>ru", "EDGE ja>ru").inOrder()
    }

    @Test
    fun `every failure is reported when no service translates`() = runBlocking<Unit> {
        bing.failure = TranslationException(TranslationError.Kind.CAPTCHA)
        google.failure = TranslationException(TranslationError.Kind.LIMITED, 429)
        edge.failure = IOException("reset")
        val result = translator().translate("文", Language.JAPANESE)
        assertThat(result).isEqualTo(
            TranslationResult.Failure(
                listOf(
                    ServiceFailure(TranslationService.BING, TranslationError(TranslationError.Kind.CAPTCHA)),
                    ServiceFailure(TranslationService.GOOGLE, TranslationError(TranslationError.Kind.LIMITED, 429)),
                    ServiceFailure(TranslationService.EDGE, TranslationError(TranslationError.Kind.NETWORK)),
                ),
            ),
        )
    }

    @Test
    fun `the user's order and switches`() = runBlocking<Unit> {
        settings.setServices(
            listOf(
                ServiceChoice(TranslationService.EDGE),
                ServiceChoice(TranslationService.BING, enabled = false),
                ServiceChoice(TranslationService.GOOGLE),
            ),
        )
        edge.failure = IOException()
        val result = translator().translate("文", Language.JAPANESE)
        assertThat(result).isEqualTo(TranslationResult.Success("Google: 文", TranslationService.GOOGLE))
        assertThat(calls).containsExactly("EDGE ja>ru", "GOOGLE ja>ru").inOrder()
    }

    @Test
    fun `no enabled service`() = runBlocking<Unit> {
        settings.setServices(TranslationService.entries.map { ServiceChoice(it, enabled = false) })
        assertThat(translator().translate("文", Language.JAPANESE)).isEqualTo(TranslationResult.Failure(emptyList()))
        assertThat(calls).isEmpty()
    }

    @Test
    fun `a service without the language or for shorter texts is skipped`() = runBlocking<Unit> {
        val partial = FakeTranslator(TranslationService.BING, supported = setOf("ja", "en"))
        val short = FakeTranslator(TranslationService.GOOGLE, maxLength = 2)
        val translator = SentenceTranslator(listOf(partial, short, edge), settings, { locale })
        val result = translator.translate("文章です", Language.JAPANESE)
        assertThat(result).isEqualTo(TranslationResult.Success("Edge: 文章です", TranslationService.EDGE))
        assertThat(partial.count.get()).isEqualTo(0)
        assertThat(short.count.get()).isEqualTo(0)
    }

    @Test
    fun `the chosen language, and English for a Japanese interface`() = runBlocking<Unit> {
        locale = Locale.JAPANESE
        translator().translate("文", Language.JAPANESE)
        settings.setLanguage("zh-Hant")
        translator().translate("文", Language.JAPANESE)
        assertThat(calls).containsExactly("BING ja>en", "BING ja>zh-Hant").inOrder()
    }

    @Test
    fun `a translation is kept and shared`() = runBlocking<Unit> {
        val translator = translator()
        bing.delayMs = 100
        val first = async { translator.translate("文", Language.JAPANESE) }
        val second = async { translator.translate("文", Language.JAPANESE) }
        assertThat(first.await()).isEqualTo(second.await())
        assertThat(translator.translate("文", Language.JAPANESE)).isEqualTo(first.await())
        assertThat(bing.count.get()).isEqualTo(1)
        // Another text, or another language, is translated anew.
        translator.translate("字", Language.JAPANESE)
        settings.setLanguage("en")
        translator.translate("文", Language.JAPANESE)
        assertThat(bing.count.get()).isEqualTo(3)
    }

    @Test
    fun `a failure is not kept`() = runBlocking<Unit> {
        val translator = translator()
        bing.failure = IOException()
        google.failure = IOException()
        edge.failure = IOException()
        assertThat(translator.translate("文", Language.JAPANESE)).isInstanceOf(TranslationResult.Failure::class.java)
        google.failure = null
        assertThat(translator.translate("文", Language.JAPANESE))
            .isEqualTo(TranslationResult.Success("Google: 文", TranslationService.GOOGLE))
    }

    @Test
    fun `a caller that stops waiting leaves the request running`() = runBlocking<Unit> {
        val translator = translator(timeoutMs = 2_000)
        bing.answer = { "late" }
        bing.delayMs = 300
        assertThat(withTimeoutOrNull(50) { translator.translate("文", Language.JAPANESE) }).isNull()
        // The same request finishes and is kept.
        delay(400)
        assertThat(translator.translate("文", Language.JAPANESE)).isEqualTo(TranslationResult.Success("late", TranslationService.BING))
        assertThat(bing.count.get()).isEqualTo(1)
    }

    @Test
    fun `the test asks one service with a longer limit and reports the time`() = runBlocking<Unit> {
        val translator = translator(timeoutMs = 50)
        google.delayMs = 100
        val test = translator.test(TranslationService.GOOGLE, "文", Language.JAPANESE)
        assertThat(test.text).isEqualTo("Google: 文")
        assertThat(test.error).isNull()
        assertThat(test.timeMs).isAtLeast(100)
        edge.delayMs = 1_000
        val slow = translator.test(TranslationService.EDGE, "文", Language.JAPANESE)
        assertThat(slow.error).isEqualTo(TranslationError(TranslationError.Kind.TIMEOUT))
        // Nothing of the test is kept for the popup.
        translator.translate("文", Language.JAPANESE)
        assertThat(bing.count.get()).isEqualTo(1)
    }

    @Test
    fun `a blank answer is a bad answer`() = runBlocking<Unit> {
        bing.answer = { " " }
        val result = translator().translate("文", Language.JAPANESE)
        assertThat(result).isEqualTo(TranslationResult.Success("Google: 文", TranslationService.GOOGLE))
    }
}
