package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
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
