package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test

class WaveformIndexTest {
    @Test
    fun appendCompletesPartialBucketsAndRejectsGapsAndBudgetOverflowBeforeReading() {
        val index = WaveformIndex(1000, bucketFrames = 2, maxBuckets = 4)
        index.append(ByteArrayInputStream(byteArrayOf(0, 0, 0, 0, 0, 64)), AudioRange(1000, 1003))
        index.append(ByteArrayInputStream(byteArrayOf(-1, 127, 0, 0, 0, 32)), AudioRange(1003, 1006))
        assertThat(index.summary(AudioRange(1000, 1006), 3).peaks).usingTolerance(0.001)
            .containsExactly(0.0, 1.0, 0.25).inOrder()
        val unread = object : java.io.InputStream() { override fun read(): Int = error("Rejected append must not read") }
        assertThrows(IllegalArgumentException::class.java) { index.append(unread, AudioRange(1007, 1008)) }
        assertThrows(IOException::class.java) { index.append(unread, AudioRange(1006, 1009)) }
        assertThat(index.endFrame).isEqualTo(1006)
        assertThat(index.allocatedBytes).isAtMost(32)
    }

    @Test
    fun overviewAndDetailSharePeaksInOriginalSourceCoordinates() {
        val index = WaveformIndex(startFrame = 1000, bucketFrames = 2, maxBuckets = 8)
        val bytes = byteArrayOf(0, 0, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, -1, 127, 0, 0)
        ByteArrayInputStream(bytes).use { index.append(it, AudioRange(1000, 1008)) }
        val overview = index.summary(AudioRange(1000, 1008), 2)
        assertThat(overview.peaks).usingTolerance(0.001).containsExactly(0.5, 1.0).inOrder()
        val detail = index.summary(AudioRange(1000, 1004), 2)
        assertThat(detail.range).isEqualTo(AudioRange(1000, 1004))
        assertThat(detail.peaks).usingTolerance(0.001).containsExactly(0.0, 0.5).inOrder()
        assertThat(index.endFrame).isEqualTo(1008)
    }
}
