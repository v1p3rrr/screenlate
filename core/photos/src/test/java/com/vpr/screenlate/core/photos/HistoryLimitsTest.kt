package com.vpr.screenlate.core.photos

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HistoryLimitsTest {
    @Test
    fun `no limits keep everything`() {
        assertThat(HistoryLimits().kept(listOf(5_000_000L, 5_000_000L, 5_000_000L))).isEqualTo(3)
        assertThat(HistoryLimits().kept(emptyList())).isEqualTo(0)
    }

    @Test
    fun `the count limit keeps the latest photos`() {
        assertThat(HistoryLimits(count = 2).kept(listOf(1L, 1L, 1L))).isEqualTo(2)
        assertThat(HistoryLimits(count = 5).kept(listOf(1L, 1L, 1L))).isEqualTo(3)
    }

    @Test
    fun `the size limit counts from the latest photo`() {
        assertThat(HistoryLimits(megabytes = 1).kept(listOf(600_000L, 400_000L, 1L))).isEqualTo(2)
        assertThat(HistoryLimits(megabytes = 1).kept(listOf(600_000L, 500_000L, 1L))).isEqualTo(1)
    }

    @Test
    fun `the latest photo stays even when it alone is over the size limit`() {
        assertThat(HistoryLimits(megabytes = 1).kept(listOf(3_000_000L, 1L))).isEqualTo(1)
    }

    @Test
    fun `the stricter of both limits applies`() {
        assertThat(HistoryLimits(count = 3, megabytes = 1).kept(listOf(400_000L, 400_000L, 400_000L))).isEqualTo(2)
        assertThat(HistoryLimits(count = 1, megabytes = 10).kept(listOf(400_000L, 400_000L))).isEqualTo(1)
    }
}
