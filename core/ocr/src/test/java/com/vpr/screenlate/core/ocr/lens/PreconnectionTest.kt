package com.vpr.screenlate.core.ocr.lens

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// The test scheduler's clock is experimental API.
@OptIn(ExperimentalCoroutinesApi::class)
class PreconnectionTest {
    private var now = 0L
    private val pending = mutableListOf<() -> Unit>()
    private val preconnection = Preconnection(interval = 30_000, clock = { now }) { done -> pending += done }

    @Test
    fun `one connection opens at a time`() {
        preconnection.start()
        preconnection.start()
        assertThat(pending).hasSize(1)
    }

    @Test
    fun `none opens soon after the last contact`() {
        preconnection.start()
        pending.removeFirst().invoke()
        now = 29_000
        preconnection.start()
        assertThat(pending).isEmpty()
        now = 31_000
        preconnection.start()
        assertThat(pending).hasSize(1)
    }

    @Test
    fun `a request counts as contact`() {
        now = 100_000
        preconnection.contacted()
        now = 110_000
        preconnection.start()
        assertThat(pending).isEmpty()
    }

    @Test
    fun `a request waits for the connection being opened`() = runTest {
        preconnection.start()
        val waiting = async { preconnection.awaitOpening(timeout = 5_000) }
        runCurrent()
        assertThat(waiting.isCompleted).isFalse()
        pending.removeFirst().invoke()
        runCurrent()
        assertThat(waiting.isCompleted).isTrue()
    }

    @Test
    fun `a request waits no longer than the timeout`() = runTest {
        preconnection.start()
        val waiting = async { preconnection.awaitOpening(timeout = 5_000) }
        advanceTimeBy(5_001)
        assertThat(waiting.isCompleted).isTrue()
    }

    @Test
    fun `a request does not wait without a connection being opened`() = runTest {
        val waiting = async { preconnection.awaitOpening(timeout = 5_000) }
        runCurrent()
        assertThat(waiting.isCompleted).isTrue()
    }
}
