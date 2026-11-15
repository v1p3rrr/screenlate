package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkStatusTest {
    @Test
    fun `isOnline returns false when offline mode is active even if system is online`() {
        val status = NetworkStatus(isOffline = { true }, systemOnline = { true })
        assertThat(status.isOnline()).isFalse()
    }

    @Test
    fun `isOnline returns system status when offline mode is false`() {
        val onlineStatus = NetworkStatus(isOffline = { false }, systemOnline = { true })
        assertThat(onlineStatus.isOnline()).isTrue()

        val offlineStatus = NetworkStatus(isOffline = { false }, systemOnline = { false })
        assertThat(offlineStatus.isOnline()).isFalse()
    }
}
