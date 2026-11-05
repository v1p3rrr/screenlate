package com.vpr.screenlate.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OverlayServiceStatusTest {
    @Test
    fun `the current connection stops`() {
        val connection = OverlayServiceStatus.connected()
        assertThat(OverlayServiceStatus.running.value).isTrue()

        OverlayServiceStatus.disconnected(connection)

        assertThat(OverlayServiceStatus.running.value).isFalse()
    }

    @Test
    fun `a late stop does not replace a newer connection`() {
        val old = OverlayServiceStatus.connected()
        val current = OverlayServiceStatus.connected()
        try {
            OverlayServiceStatus.disconnected(old)

            assertThat(OverlayServiceStatus.running.value).isTrue()
            assertThat(OverlayServiceStatus.connections).isEqualTo(current)
        } finally {
            OverlayServiceStatus.disconnected(current)
        }
    }
}
