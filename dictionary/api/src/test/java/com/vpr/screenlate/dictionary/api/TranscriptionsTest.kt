package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.PersianSupport
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.PitchAccent
import com.vpr.screenlate.dictionary.api.model.PitchGroup
import com.vpr.screenlate.dictionary.api.model.TermEntry
import com.vpr.screenlate.dictionary.api.registry.TranscriptionTags
import org.junit.Test

class TranscriptionsTest {
    private val names = mapOf("General-American" to "us", "British" to "gb")

    private fun tags(vararg entries: Triple<String, String, List<String>>): TranscriptionTags =
        TranscriptionTags.Builder().apply { entries.forEach { (term, ipa, tags) -> add(term, ipa, tags) } }.build()

    private fun group(vararg transcriptions: String) = PitchGroup("IPA", transcriptions = transcriptions.toList())

    @Test
    fun `transcriptions get the regions of their tags`() {
        val tags = tags(
            Triple("water", "/ˈwɔtɚ/", listOf("General-American")),
            Triple("water", "/ˈwɔːtə/", listOf("British", "dialect", "General-American")),
        )

        val prepared = Transcriptions.prepare(group("/ˈwɔtɚ/", "/ˈwɔːtə/", "/ˈwɑtɚ/"), "water", tags, names)

        assertThat(prepared.transcriptions).containsExactly("/ˈwɔtɚ/", "/ˈwɔːtə/", "/ˈwɑtɚ/").inOrder()
        assertThat(prepared.transcriptionRegions)
            .containsExactly(listOf("us"), listOf("gb", "us"), emptyList<String>()).inOrder()
        assertThat(prepared.transcriptionTags).containsExactly(
            listOf("General-American"),
            listOf("British", "dialect", "General-American"),
            emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun `transcriptions the language puts first go before the others, which keep their order`() {
        val tags = tags(
            Triple("כלב", "/kalb/", listOf("reconstructed Biblical")),
            Triple("כלב", "/ˈkɛlɛv/", listOf("Tiberian")),
            Triple("כלב", "[ˈkelev]", listOf("Modern-Israeli", "sg")),
            Triple("כלב", "/kaˈlev/", listOf("Modern-Israeli")),
        )
        val modern = { itemTags: List<String> -> "Modern-Israeli" in itemTags }

        val prepared = Transcriptions.prepare(group("/kalb/", "/ˈkɛlɛv/", "[ˈkelev]", "/kaˈlev/"), "כלב", tags, names, modern)

        assertThat(prepared.transcriptions).containsExactly("[ˈkelev]", "/kaˈlev/", "/kalb/", "/ˈkɛlɛv/").inOrder()
        assertThat(prepared.transcriptionTags.first()).containsExactly("Modern-Israeli", "sg").inOrder()
        // Without a rule of the language, the dictionary's order stays.
        assertThat(Transcriptions.prepare(group("/kalb/", "[ˈkelev]"), "כלב", tags, names).transcriptions)
            .containsExactly("/kalb/", "[ˈkelev]").inOrder()
    }

    @Test
    fun `the iranian transcriptions of a persian word go first, the tehrani one after the formal one`() {
        val tags = tags(
            Triple("آسان", "/ʔaː.ˈsaːn/", listOf("classic")),
            Triple("آسان", "[ʔɑː.sɑ́ːn]", listOf("🇦🇫", "formal", "🇦🇫KA")),
            Triple("آسان", "[ʔɔː.sɔ́ːn]", listOf("Hazaragi")),
            Triple("آسان", "[ʔɒː.sɒ́ːn]", listOf("🇮🇷", "formal")),
            Triple("آسان", "[ʔɒː.súːn]", listOf("🇮🇷TE")),
            Triple("آسان", "[ʔɔ.sɔ́n]", listOf("🇹🇯", "formal")),
        )
        val group = group("/ʔaː.ˈsaːn/", "[ʔɑː.sɑ́ːn]", "[ʔɔː.sɔ́ːn]", "[ʔɒː.sɒ́ːn]", "[ʔɒː.súːn]", "[ʔɔ.sɔ́n]")

        val prepared = Transcriptions.prepare(group, "آسان", tags, emptyMap(), PersianSupport::firstTranscription)

        assertThat(prepared.transcriptions).containsExactly(
            "[ʔɒː.sɒ́ːn]", "[ʔɒː.súːn]", "/ʔaː.ˈsaːn/", "[ʔɑː.sɑ́ːn]", "[ʔɔː.sɔ́ːn]", "[ʔɔ.sɔ́n]",
        ).inOrder()
        assertThat(prepared.transcriptionRegions.first()).containsExactly("ir")
    }

    @Test
    fun `ASCII notations and quoted text are dropped`() {
        val tags = tags(
            Triple("water", "/\"wO:t@/", listOf("XS")),
            Triple("water", "/ˈwɔːtə/, /wO:t@/", listOf("SAMPA")),
        )

        val prepared = Transcriptions.prepare(
            group("/ˈwɔtɚ/", "/\"wO:t@/", "/ˈwɔːtə/, /wO:t@/", "/\"water\" (Reino Unido)/"),
            "water",
            tags,
            names,
        )

        assertThat(prepared.transcriptions).containsExactly("/ˈwɔtɚ/")
    }

    @Test
    fun `a list in one string is split and its parts merge with the same transcriptions`() {
        val prepared = Transcriptions.prepare(group("/ˈwɔ.tɚ/", "/ˈwɔ.tɚ/, /ˈwɑ.tɚ/", "[a], [b]"), "water", null, names)

        assertThat(prepared.transcriptions).containsExactly("/ˈwɔ.tɚ/", "/ˈwɑ.tɚ/", "[a]", "[b]").inOrder()
        assertThat(prepared.transcriptionRegions).isEmpty()
        assertThat(prepared.transcriptionTags).isEmpty()
    }

    @Test
    fun `a phonemic and a phonetic form wrapped in one more pair of slashes are two transcriptions`() {
        val prepared = Transcriptions.prepare(group("//ˈba.jit/ [ˈbɐ.(j)it]/", "//t̪ʰo.ves.suːn//"), "בית", null, names)

        assertThat(prepared.transcriptions).containsExactly("/ˈba.jit/", "[ˈbɐ.(j)it]", "/t̪ʰo.ves.suːn/").inOrder()
        // A doubled slash around other text stays as written.
        assertThat(Transcriptions.split("//ˈbaːʔ/ (etimología 1)/")).containsExactly("//ˈbaːʔ/ (etimología 1)/")
        assertThat(Transcriptions.split("//braːɡ/")).containsExactly("//braːɡ/")
    }

    @Test
    fun `a text with commas that is no list stays whole`() {
        val prepared = Transcriptions.prepare(group("/a/, b"), "water", null, names)

        assertThat(prepared.transcriptions).containsExactly("/a/, b")
    }

    @Test
    fun `phonetic and phonemic forms with the same symbols become the phonemic one with the regions of both`() {
        val tags = tags(
            Triple("water", "[ˈwɔː.tə]", listOf("British")),
            Triple("water", "/ˈwɔːtə/", listOf("General-American")),
        )

        val prepared = Transcriptions.prepare(group("[ˈwɔː.tə]", "/ˈwɑtɚ/", "/ˈwɔːtə/", "[ˈwɑtɚ]"), "water", tags, names)

        assertThat(prepared.transcriptions).containsExactly("/ˈwɔːtə/", "/ˈwɑtɚ/").inOrder()
        assertThat(prepared.transcriptionRegions).containsExactly(listOf("gb", "us"), emptyList<String>()).inOrder()
        assertThat(prepared.transcriptionTags)
            .containsExactly(listOf("British", "General-American"), emptyList<String>()).inOrder()
    }

    @Test
    fun `a clean group stays the same object`() {
        val clean = group("/ˈwɔtɚ/", "[ˈwɔɾɚ]")

        assertThat(Transcriptions.prepare(clean, "water", null, names)).isSameInstanceAs(clean)
    }

    @Test
    fun `a result is prepared group by group with the tags of each dictionary`() {
        val accents = PitchGroup("Pitch", pitches = listOf(PitchAccent(0)))
        val result = LookupResult(
            matched = "water",
            deinflected = "water",
            term = TermEntry(
                "water",
                "water",
                pitches = listOf(accents, group("/ˈwɔtɚ/", "[ˈwɔtɚ]"), PitchGroup("Other", transcriptions = listOf("/ˈwɔtɚ/"))),
            ),
        )
        val tags = mapOf("IPA" to tags(Triple("water", "/ˈwɔtɚ/", listOf("General-American"))))

        val pitches = Transcriptions.prepare(result, tags, names).term.pitches

        assertThat(pitches[0]).isSameInstanceAs(accents)
        assertThat(pitches[1].transcriptions).containsExactly("/ˈwɔtɚ/")
        assertThat(pitches[1].transcriptionRegions).containsExactly(listOf("us"))
        assertThat(pitches[2].transcriptionRegions).isEmpty()
    }

    @Test
    fun `a result without transcriptions is returned as it is`() {
        val result = LookupResult("cat", "cat", term = TermEntry("cat", "cat"))

        assertThat(Transcriptions.prepare(result, emptyMap(), names)).isSameInstanceAs(result)
    }
}
