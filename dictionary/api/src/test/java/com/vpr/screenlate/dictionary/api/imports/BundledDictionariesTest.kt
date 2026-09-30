package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries.Asset
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BundledDictionariesTest {

    private val jmdict = Asset("10-jmdict-english.zip", 100)
    private val frequency = Asset("20-jiten-global-frequency.zip", 20)
    private val pitch = Asset("30-kanjium-pitch-accents.zip", 30)
    private val shipped = listOf(jmdict, frequency, pitch)

    @Test
    fun `a fresh install gets every archive`() {
        assertThat(BundledDictionaries.pending(shipped, emptySet())).containsExactlyElementsIn(shipped).inOrder()
    }

    @Test
    fun `installed archives and ones the user deleted are not installed again`() {
        assertThat(BundledDictionaries.pending(shipped, setOf(jmdict.key, frequency.key, pitch.key))).isEmpty()
    }

    @Test
    fun `a new version of the same archive replaces it`() {
        val installed = setOf("10-jmdict-english.zip:90", frequency.key, pitch.key)
        assertThat(BundledDictionaries.pending(shipped, installed)).containsExactly(jmdict)
    }

    @Test
    fun `a different dictionary in a slot the user already filled is skipped`() {
        val installed = setOf("10-jitendex.zip:38698313", frequency.key, pitch.key)
        assertThat(BundledDictionaries.pending(shipped, installed)).isEmpty()
    }

    @Test
    fun `a new slot is installed next to replaced ones`() {
        val installed = setOf("10-jitendex.zip:38698313", frequency.key)
        assertThat(BundledDictionaries.pending(shipped, installed)).containsExactly(pitch)
    }

    @Test
    fun `an archive whose dictionary the user deleted is not installed again in any version`() {
        val installed = setOf("20-jiten-global-frequency.zip:19", jmdict.key, pitch.key)
        assertThat(BundledDictionaries.pending(shipped, installed, declined = setOf(frequency.name))).isEmpty()
        assertThat(BundledDictionaries.pending(shipped, emptySet(), declined = setOf(frequency.name)))
            .containsExactly(jmdict, pitch).inOrder()
    }

    @Test
    fun `archives installed before in any version are found by name`() {
        val installed = setOf("10-jmdict-english.zip:90", pitch.key, "10-jitendex.zip:38698313")
        assertThat(BundledDictionaries.installedBefore(shipped, installed)).containsExactly(jmdict, pitch).inOrder()
    }

    @Test
    fun `a bracketed version does not change the title`() {
        assertThat(BundledDictionaries.baseTitle("JMdict [2026-09-27]")).isEqualTo("JMdict")
        assertThat(BundledDictionaries.baseTitle("Jiten")).isEqualTo("Jiten")
        assertThat(BundledDictionaries.baseTitle("Kanjium Pitch Accents")).isEqualTo("Kanjium Pitch Accents")
    }

    @Test
    fun `an installed copy in another revision or with the older English title is the same dictionary`() {
        assertThat(BundledDictionaries.sameTitle("KANJIDIC [2026-250]", "KANJIDIC [2026-270]")).isTrue()
        assertThat(BundledDictionaries.sameTitle("KANJIDIC (English)", "KANJIDIC [2026-270]")).isTrue()
        assertThat(BundledDictionaries.sameTitle("KANJIDIC", "KANJIDIC [2026-270]")).isTrue()
        assertThat(BundledDictionaries.sameTitle("KANJIDIC (French)", "KANJIDIC [2026-270]")).isFalse()
        assertThat(BundledDictionaries.sameTitle("Jitendex.org [2026-09-20]", "JMdict [2026-09-27]")).isFalse()
    }

    private val kanjidic = Asset("40-kanjidic-english.zip", 40)
    private val titles = mapOf(jmdict to "JMdict [2026-09-27]", frequency to "Jiten [2026-09-01]", kanjidic to "KANJIDIC [2026-270]")

    private suspend fun alreadyPresent(pending: List<Asset>, installed: Set<String>, present: Set<String>) =
        BundledDictionaries.alreadyPresent(pending, installed, { present }, { titles[it] })

    @Test
    fun `a new archive whose dictionary the user already has is left out`() = runTest {
        val installed = setOf(jmdict.key, frequency.key)
        val present = setOf("JMdict [2026-09-27]", "KANJIDIC (English)")
        assertThat(alreadyPresent(listOf(kanjidic), installed, present)).containsExactly(kanjidic)
        assertThat(alreadyPresent(listOf(kanjidic), installed, setOf("JMdict [2026-09-27]", "KANJIDIC (French)"))).isEmpty()
    }

    @Test
    fun `a new version of an installed archive is not taken for the user's own copy`() = runTest {
        val installed = setOf(Asset(jmdict.name, 99).key)
        assertThat(alreadyPresent(listOf(jmdict), installed, setOf("JMdict [2026-09-20]"))).isEmpty()
    }

    @Test
    fun `nothing is read without new archives or installed dictionaries`() = runTest {
        val unread: suspend () -> Set<String> = { error("titles read") }
        assertThat(BundledDictionaries.alreadyPresent(emptyList(), emptySet(), unread) { error("archive opened") }).isEmpty()
        assertThat(BundledDictionaries.alreadyPresent(listOf(kanjidic), emptySet(), { emptySet() }) { error("archive opened") })
            .isEmpty()
    }
}
