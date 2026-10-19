package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkRunsTest {
    @Test
    fun `counts a resize for each shape other than the last one`() {
        val runs = NetworkRuns(listOf(1, 3, 48, 320))
        runs.add(intArrayOf(1, 3, 48, 320), 10)
        runs.add(intArrayOf(1, 3, 48, 640), 30)
        runs.add(intArrayOf(1, 3, 48, 640), 20)
        runs.add(intArrayOf(1, 3, 48, 160), 5)

        assertThat(runs.ms).isEqualTo(65L)
        assertThat(runs.shape).isEqualTo(listOf(1, 3, 48, 160))
        assertThat(runs.describe()).isEqualTo("runs=4 ms=65 max=30 resizes=2 resize_ms=35 largest=1x3x48x640")
    }

    @Test
    fun `the first run of a network without a known shape resizes it`() {
        val runs = NetworkRuns(null)
        runs.add(intArrayOf(1, 3, 960, 544), 200)

        assertThat(runs.describe()).isEqualTo("runs=1 ms=200 max=200 resizes=1 resize_ms=200 largest=1x3x960x544")
    }
}
