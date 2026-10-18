package com.vpr.screenlate.audio

import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.AudioSourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionsUsedTest {
    private val withoutWiktionary = listOf(AudioSource(AudioSourceType.LINGUA_LIBRE), AudioSource(AudioSourceType.TEXT_TO_SPEECH))

    @Test
    fun `the region order shows while wiktionary is a source`() {
        assertTrue(regionsUsed(withoutWiktionary + AudioSource(AudioSourceType.WIKTIONARY), hasTranscriptions = false))
    }

    @Test
    fun `the region order shows for transcriptions without wiktionary`() {
        assertTrue(regionsUsed(withoutWiktionary, hasTranscriptions = true))
    }

    @Test
    fun `the region order hides when nothing uses it`() {
        assertFalse(regionsUsed(withoutWiktionary, hasTranscriptions = false))
        assertFalse(regionsUsed(emptyList(), hasTranscriptions = false))
    }
}
