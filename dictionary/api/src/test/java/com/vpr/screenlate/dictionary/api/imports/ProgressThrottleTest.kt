package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressThrottleTest {
    private var now = 1_000L
    private val throttle = ProgressThrottle(intervalMs = 500, clock = { now })

    @Test
    fun `the first percent goes through`() {
        assertThat(throttle.pass(0)).isTrue()
    }

    @Test
    fun `a new percent waits for the interval`() {
        throttle.pass(0)
        now += 100
        assertThat(throttle.pass(1)).isFalse()
        now += 400
        assertThat(throttle.pass(2)).isTrue()
    }

    @Test
    fun `the same percent does not go through again`() {
        throttle.pass(5)
        now += 10_000
        assertThat(throttle.pass(5)).isFalse()
        assertThat(throttle.pass(6)).isTrue()
    }

    @Test
    fun `an unknown size reports once`() {
        assertThat(throttle.pass(-1)).isTrue()
        now += 10_000
        assertThat(throttle.pass(-1)).isFalse()
    }
}
