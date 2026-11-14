package com.vpr.screenlate.overlay.capture

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureConsentTest {
    @Test
    fun `late grant and dismissal after timeout cannot complete the next request`() = runTest {
        val consent = CaptureConsent<String>()
        var firstId = ""
        var closed = false
        val first = async {
            consent.request(100) { id ->
                firstId = id
                assertThat(consent.attach(id) { closed = true }).isTrue()
            }
        }
        advanceTimeBy(101)
        assertThat(first.await()).isNull()
        assertThat(closed).isTrue()

        var secondId = ""
        val second = async { consent.request(100) { secondId = it } }
        runCurrent()
        consent.complete(firstId, "stale")
        consent.complete(firstId, null)
        assertThat(consent.attach(firstId) {}).isFalse()
        assertThat(second.isCompleted).isFalse()
        consent.complete(secondId, "granted")
        assertThat(second.await()).isEqualTo("granted")
    }

    @Test
    fun `denial cancellation and shutdown release the request`() = runTest {
        val consent = CaptureConsent<String>()
        assertThat(consent.request(100) { consent.complete(it, null) }).isNull()

        var closed = false
        val cancelled = async { consent.request(100) { id -> consent.attach(id) { closed = true } } }
        runCurrent()
        cancelled.cancelAndJoin()
        assertThat(closed).isTrue()

        val stopped = async { consent.request(100) {} }
        runCurrent()
        consent.cancel()
        assertThat(stopped.await()).isNull()
        assertThat(consent.request(100) { consent.complete(it, "retry") }).isEqualTo("retry")
    }
}
