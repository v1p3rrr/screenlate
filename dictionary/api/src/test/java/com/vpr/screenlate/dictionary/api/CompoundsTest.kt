package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.DanishSupport
import com.vpr.screenlate.core.common.language.DutchSupport
import com.vpr.screenlate.core.common.language.FinnishSupport
import com.vpr.screenlate.core.common.language.NorwegianSupport
import com.vpr.screenlate.core.common.language.SwedishSupport
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CompoundsTest {

    private val links = listOf("s", "es", "n")

    /** Lengths of the [words] (lowercase) [text] starts with, as the engine's prefix matches. */
    private fun prefixes(words: Set<String>, text: String) = words.filter { text.lowercase().startsWith(it) }.map { it.length }

    private fun split(word: String, vararg words: String): List<String>? = runBlocking {
        Compounds.split(word, links) { prefixes(words.toSet(), it) }?.map { word.substring(it.first, it.last + 1) }
    }

    @Test
    fun `the longest word comes first and the rest is split the same way`() {
        assertThat(split("Haustürschlüssel", "hau", "haus", "tür", "schlüssel")).containsExactly("Haus", "tür", "schlüssel")
            .inOrder()
        assertThat(split("Haustürschlüssel", "haus", "haustür", "tür", "schlüssel")).containsExactly("Haustür", "schlüssel")
            .inOrder()
    }

    @Test
    fun `a linking element is skipped only when the rest needs it`() {
        assertThat(split("Arbeitszimmer", "arbeit", "zimmer")).containsExactly("Arbeit", "zimmer").inOrder()
        assertThat(split("Hundesteuer", "hund", "hunde", "steuer", "teuer", "este")).containsExactly("Hunde", "steuer")
            .inOrder()
        assertThat(split("Sonnenschein", "sonne", "schein")).containsExactly("Sonne", "schein").inOrder()
    }

    @Test
    fun `dutch links are skipped the same way`() {
        fun dutch(word: String, vararg words: String) = runBlocking {
            Compounds.split(word, DutchSupport.compoundLinks!!) { prefixes(words.toSet(), it) }
                ?.map { word.substring(it.first, it.last + 1) }
        }
        assertThat(dutch("stadsdeel", "stad", "deel")).containsExactly("stad", "deel").inOrder()
        assertThat(dutch("boekenkast", "boek", "kast")).containsExactly("boek", "kast").inOrder()
        assertThat(dutch("pannekoek", "pan", "panne", "koek")).containsExactly("panne", "koek").inOrder()
    }

    @Test
    fun `swedish links are skipped the same way, a part cut short before its link is not tried`() {
        fun swedish(word: String, vararg words: String) = runBlocking {
            Compounds.split(word, SwedishSupport.compoundLinks!!) { prefixes(words.toSet(), it) }
                ?.map { word.substring(it.first, it.last + 1) }
        }
        assertThat(swedish("statsminister", "stat", "minister")).containsExactly("stat", "minister").inOrder()
        assertThat(swedish("skattepengar", "skatt", "pengar")).containsExactly("skatt", "pengar").inOrder()
        assertThat(swedish("barnabarn", "barn")).containsExactly("barn", "barn").inOrder()
        // Kyrko- is kyrka's.
        assertThat(swedish("kyrkogård", "kyrka", "gård")).isNull()
    }

    @Test
    fun `norwegian links are s and e`() {
        fun norwegian(word: String, vararg words: String) = runBlocking {
            Compounds.split(word, NorwegianSupport.compoundLinks!!) { prefixes(words.toSet(), it) }
                ?.map { word.substring(it.first, it.last + 1) }
        }
        assertThat(norwegian("arbeidsplass", "arbeid", "plass")).containsExactly("arbeid", "plass").inOrder()
        assertThat(norwegian("fylkesmann", "fylke", "mann")).containsExactly("fylke", "mann").inOrder()
        assertThat(norwegian("barnehage", "barn", "hage")).containsExactly("barn", "hage").inOrder()
        // u is no Norwegian link.
        assertThat(norwegian("gatukjøkken", "gate", "gat", "kjøkken")).isNull()
    }

    @Test
    fun `danish links are s and e`() {
        fun danish(word: String, vararg words: String) = runBlocking {
            Compounds.split(word, DanishSupport.compoundLinks!!) { prefixes(words.toSet(), it) }
                ?.map { word.substring(it.first, it.last + 1) }
        }
        assertThat(danish("statsminister", "stat", "minister")).containsExactly("stat", "minister").inOrder()
        assertThat(danish("skolebørn", "skol", "skole", "børn")).containsExactly("skole", "børn").inOrder()
        assertThat(danish("sportschef", "sport", "chef")).containsExactly("sport", "chef").inOrder()
        // o is no Danish link.
        assertThat(danish("kirkogård", "kirke", "kirk", "gård")).isNull()
    }

    @Test
    fun `finnish joins its words without a link, a genitive first part is a word of its own`() {
        fun finnish(word: String, vararg words: String) = runBlocking {
            Compounds.split(word, FinnishSupport.compoundLinks!!) { prefixes(words.toSet(), it) }
                ?.map { word.substring(it.first, it.last + 1) }
        }
        // kirjaston, the genitive of kirjasto, is a form row of the dictionary.
        assertThat(finnish("kirjastonhoitaja", "kirjasto", "kirjaston", "hoitaja")).containsExactly("kirjaston", "hoitaja")
            .inOrder()
        assertThat(finnish("rautatieasema", "rauta", "rautatie", "tie", "asema")).containsExactly("rautatie", "asema")
            .inOrder()
        assertThat(finnish("kirjastossa", "kirjasto")).isNull()
    }

    @Test
    fun `the split with the fewest parts wins, then the one whose shortest part is longest`() {
        fun dutch(word: String, vararg words: String) = runBlocking {
            Compounds.split(word, DutchSupport.compoundLinks!!) { prefixes(words.toSet(), it) }
                ?.map { word.substring(it.first, it.last + 1) }
        }
        assertThat(dutch("stadsbestuurder", "stad", "stads", "stadsbestuur", "bestuur", "bestuurder", "der"))
            .containsExactly("stads", "bestuurder").inOrder()
        // Two parts before three, even with short ones: the longest first word stays when nothing beats it.
        assertThat(split("Haustürschlüssel", "haus", "haustür", "tür", "schlüssel", "türschlüssel"))
            .containsExactly("Haustür", "schlüssel").inOrder()
        assertThat(split("Haustürschlüsselbund", "haus", "tür", "schlüssel", "schlüsselbund", "bund"))
            .containsExactly("Haus", "tür", "schlüsselbund").inOrder()
        // Equal shortest parts: the longer first word.
        assertThat(split("Wachstube", "wach", "wachs", "stube", "tube")).containsExactly("Wachs", "tube").inOrder()
    }

    @Test
    fun `the search keeps within its lookups`() = runBlocking {
        val words = setOf("abc", "abcdef", "def", "ghi", "jkl", "mno", "pqr", "stu", "vwx")
        val word = "abcdefghijklmnopqrstuvwx"
        var calls = 0
        val parts = Compounds.split(word, emptyList()) { text ->
            calls++
            prefixes(words, text)
        }
        assertThat(parts?.map { word.substring(it.first, it.last + 1) })
            .containsExactly("abcdef", "ghi", "jkl", "mno", "pqr", "stu", "vwx").inOrder()
        assertThat(calls).isAtMost(Compounds.MAX_LOOKUPS)
    }

    @Test
    fun `a shorter first word is tried when the longer one leaves a rest without words`() {
        assertThat(split("Wasserflasche", "wasser", "wasserf", "flasche")).containsExactly("Wasser", "flasche").inOrder()
    }

    @Test
    fun `no split for a word the parts do not cover or for short parts`() {
        assertThat(split("Müller", "müll")).isNull()
        assertThat(split("Ölkanne", "öl", "kanne")).isNull()
        assertThat(split("Hausxyz", "haus")).isNull()
    }

    @Test
    fun `the word ends at the first character that is not a letter`() {
        assertThat(Compounds.word("Haustür, sagte er")).isEqualTo("Haustür")
        assertThat(Compounds.word("3. Oktober")).isEmpty()
    }

    private fun result(matched: String, expression: String) =
        LookupResult(matched = matched, deinflected = expression, term = TermEntry(expression, ""))

    @Test
    fun `parts carry the whole word as their match and a word with an entry is not split`() = runBlocking {
        val dictionary = mapOf("haus" to "Haus", "tür" to "Tür")
        val lookup: suspend (String) -> List<LookupResult> = { text ->
            dictionary.filterKeys { text.lowercase().startsWith(it) }.map { (key, word) -> result(text.take(key.length), word) }
        }
        val parts = compoundParts("Haustür ist", listOf(result("Haus", "Haus")), links, lookup)
        assertThat(parts?.map { part -> part.map { it.term.expression } }).containsExactly(listOf("Haus"), listOf("Tür"))
            .inOrder()
        assertThat(parts?.flatten()?.map { it.matched }).containsExactly("Haustür", "Haustür")
        assertThat(compoundParts("Haustür ist", listOf(result("Haustür", "Haustür")), links, lookup)).isNull()
    }

    @Test
    fun `every entry of the parts names the parts by their first headwords`() {
        val parts = listOf(
            listOf(result("Küchentischlampe", "Küchentisch")),
            emptyList(),
            listOf(result("Küchentischlampe", "Lampe"), result("Küchentischlampe", "lampen")),
        )
        val marked = parts.markedAsCompound()
        assertThat(marked.map { it.term.expression }).containsExactly("Küchentisch", "Lampe", "lampen").inOrder()
        assertThat(marked.map { it.compound }.distinct()).containsExactly(listOf("Küchentisch", "Lampe"))
    }

    @Test
    fun `capitalized headwords come first and keep their order`() {
        val results = listOf(result("lampe", "lampen"), result("lampe", "Lampe"), result("lampe", "lampe"), result("lampe", "Lampen"))
        assertThat(results.capitalizedFirst().map { it.term.expression }).containsExactly("Lampe", "Lampen", "lampen", "lampe")
            .inOrder()
    }
}
