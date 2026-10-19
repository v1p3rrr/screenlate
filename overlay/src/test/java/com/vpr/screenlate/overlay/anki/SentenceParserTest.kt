package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SentenceParserTest {
    @Test
    fun `a profile switch while a lookup waits does not change the remaining sentence's language`() = runTest {
        var active = Language.JAPANESE
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val languages = mutableListOf<Language>()
        val parsed = async {
            parseSentence("猫犬", active) { text, language ->
                languages += language
                if (text == "猫犬") {
                    started.complete(Unit)
                    resume.await()
                }
                val word = text.take(1)
                val reading = if (word == "猫") "ねこ" else "いぬ"
                LookupResult(word, word, term = TermEntry(word, reading))
            }
        }
        started.await()
        active = Language.ENGLISH
        resume.complete(Unit)

        assertThat(parsed.await()).containsExactly(SentencePart("猫", "猫", "ねこ"), SentencePart("犬", "犬", "いぬ")).inOrder()
        assertThat(languages).containsExactly(Language.JAPANESE, Language.JAPANESE)
        assertThat(active).isEqualTo(Language.ENGLISH)
    }

    @Test
    fun `unmatched supplementary characters and lookup failures preserve the sentence`() = runTest {
        val parsed = parseSentence("😀猫!", Language.JAPANESE) { text, _ ->
            if (text == "猫!") LookupResult("猫", "猫", term = TermEntry("猫", "ねこ"))
            else error("No dictionary answer")
        }
        assertThat(parsed).containsExactly(SentencePart("😀"), SentencePart("猫", "猫", "ねこ"), SentencePart("!")).inOrder()
        assertThat(parsed.joinToString("") { it.text }).isEqualTo("😀猫!")
    }

    @Test
    fun `cancelling a lookup stops sentence parsing`() = runTest {
        var cancelled = false
        try {
            parseSentence("猫", Language.JAPANESE) { _, _ -> throw CancellationException() }
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertThat(cancelled).isTrue()
    }

    @Test
    fun `in a language looked up by words unknown text is passed over a word at a time`() = runTest {
        val lookups = mutableListOf<String>()
        suspend fun parse(sentence: String, language: Language, vararg known: String) =
            parseSentence(sentence, language) { text, _ ->
                lookups += text
                known.firstOrNull { text.startsWith(it) }?.let { LookupResult(it, it, term = TermEntry(it, "")) }
            }

        assertThat(parse("unhappy is well-known", Language.ENGLISH, "happy", "is", "known")).containsExactly(
            SentencePart("unhappy "),
            SentencePart("is", "is", ""),
            SentencePart(" well-known"),
        ).inOrder()
        assertThat(lookups).containsNoneOf("happy is well-known", "known")
        // Apostrophes inside a word keep it whole; an elided article ends one in French only.
        assertThat(parse("aujourd'hui", Language.FRENCH, "hui").map { it.text }).containsExactly("aujourd'hui")
        assertThat(parse("l'eau", Language.FRENCH, "eau").map { it.text }).containsExactly("l'", "eau").inOrder()
        assertThat(parse("l'eau", Language.ENGLISH, "eau").map { it.text }).containsExactly("l'eau")
        // Languages looked up by letters still try every character.
        assertThat(parse("unhappy", Language.KOREAN, "happy").map { it.text }).containsExactly("un", "happy").inOrder()
        assertThat(parse("未知猫", Language.JAPANESE, "猫").map { it.text }).containsExactly("未知", "猫").inOrder()
    }
}
