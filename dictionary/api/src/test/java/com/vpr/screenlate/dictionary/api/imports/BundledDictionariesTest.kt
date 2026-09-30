package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.imports.BundledDictionaries.Asset
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
}
