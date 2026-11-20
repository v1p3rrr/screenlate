package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

class AudioEditInputStreamTest {

    @Test
    fun skipsExclusionsAndYieldsKeptFrames() {
        // Selection: frames 0..10 (20 bytes). Each frame is 2 bytes: [frameIndex, 0].
        val rawBytes = ByteArray(20) { index ->
            if (index % 2 == 0) (index / 2).toByte() else 0
        }
        val rawStream = ByteArrayInputStream(rawBytes)

        // Cut: frames 3..7 (frames 3, 4, 5, 6 excluded). Kept: [0..3, 7..10] -> 3 + 3 = 6 frames = 12 bytes.
        val selection = AudioRange(0, 10)
        val cut = AudioRange(3, 7)
        val edit = AudioEdit(selection, listOf(cut))

        val editStream = AudioEditInputStream(rawStream, edit)
        val output = editStream.readBytes()

        assertThat(output.size).isEqualTo(12)
        val frames = (0 until 6).map { i -> output[i * 2].toInt() }
        assertThat(frames).containsExactly(0, 1, 2, 7, 8, 9).inOrder()
    }

    @Test
    fun selectionWithoutCutsYieldsAllFrames() {
        val rawBytes = ByteArray(10) { it.toByte() }
        val rawStream = ByteArrayInputStream(rawBytes)
        val edit = AudioEdit(AudioRange(0, 5), emptyList())

        val editStream = AudioEditInputStream(rawStream, edit)
        val output = editStream.readBytes()

        assertThat(output).isEqualTo(rawBytes)
    }

    @Test
    fun multipleCutsOmittedInOrder() {
        // Selection: frames 10..20 (10 frames = 20 bytes).
        val rawBytes = ByteArray(20) { index ->
            if (index % 2 == 0) (10 + index / 2).toByte() else 0
        }
        val rawStream = ByteArrayInputStream(rawBytes)

        // Cuts: 11..13, 16..18.
        // Kept: [10..11, 13..16, 18..20] -> 1 + 3 + 2 = 6 frames = 12 bytes.
        val selection = AudioRange(10, 20)
        val cuts = listOf(AudioRange(11, 13), AudioRange(16, 18))
        val edit = AudioEdit(selection, cuts)

        val editStream = AudioEditInputStream(rawStream, edit)
        val output = editStream.readBytes()

        assertThat(output.size).isEqualTo(12)
        val frames = (0 until 6).map { i -> output[i * 2].toInt() }
        assertThat(frames).containsExactly(10, 13, 14, 15, 18, 19).inOrder()
    }
}
