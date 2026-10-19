package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// The test scheduler's clock is experimental API.
@OptIn(ExperimentalCoroutinesApi::class)
class IdleActionTest {
    private var runs = 0

    private fun TestScope.idleAction() = IdleAction(backgroundScope, delayMs = 10_000) { runs++ }

    @Test
    fun `runs once the delay passes`() = runTest {
        val action = idleAction()
        action.schedule()
        advanceTimeBy(9_999)
        assertThat(runs).isEqualTo(0)
        advanceTimeBy(2)
        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun `another schedule starts the wait again`() = runTest {
        val action = idleAction()
        action.schedule()
        advanceTimeBy(8_000)
        action.schedule()
        advanceTimeBy(8_000)
        assertThat(runs).isEqualTo(0)
        advanceTimeBy(2_001)
        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun `cancel drops the pending run`() = runTest {
        val action = idleAction()
        action.schedule()
        advanceTimeBy(5_000)
        action.cancel()
        advanceTimeBy(20_000)
        runCurrent()
        assertThat(runs).isEqualTo(0)
    }
}
