package com.vpr.screenlate.core.anki.audio

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SentenceAudioStagingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun firstCreationRemovesOnlyAbandonedSentenceExports() {
        val directory = temporary.newFolder()
        val old = File(directory, "screenlate_sentence_old.opus").apply { writeBytes(ByteArray(2)) }
        val unrelatedAudio = File(directory, "downloaded.opus").apply { writeBytes(ByteArray(2)) }
        val unrelatedSentence = File(directory, "screenlate_sentence_old.wav").apply { writeBytes(ByteArray(2)) }
        val staging = SentenceAudioStaging(directory)

        val current = staging.create()
        assertThat(old.exists()).isFalse()
        assertThat(unrelatedAudio.exists()).isTrue()
        assertThat(unrelatedSentence.exists()).isTrue()

        val retry = staging.create()
        assertThat(current.exists()).isTrue()
        assertThat(retry.exists()).isTrue()
    }

    @Test
    fun aFailedOrphanDeletionRejectsCreationAndCanBeRetried() {
        val directory = temporary.newFolder()
        val old = File(directory, "screenlate_sentence_old.opus").apply { writeBytes(ByteArray(2)) }
        val failing = SentenceAudioStaging(directory, delete = { false })
        assertThrows(IOException::class.java) { failing.create() }
        assertThat(old.exists()).isTrue()
        assertThat(directory.listFiles()!!.toList()).containsExactly(old)

        val restarted = SentenceAudioStaging(directory)
        restarted.create().delete()
        assertThat(old.exists()).isFalse()
    }
}
