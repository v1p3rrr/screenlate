package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PerGenerationTest {

    @Test
    fun `computes once per generation`() = runTest {
        val cache = PerGeneration<String>()
        var computed = 0
        suspend fun get(generation: Int) = cache.get(generation) { "value ${++computed}" }

        assertThat(get(1)).isEqualTo("value 1")
        assertThat(get(1)).isEqualTo("value 1")
        assertThat(get(2)).isEqualTo("value 2")
        assertThat(get(1)).isEqualTo("value 3")
        assertThat(computed).isEqualTo(3)
    }

    @Test
    fun `a failed computation is not kept`() = runTest {
        val cache = PerGeneration<String>()
        val failure = runCatching { cache.get(1) { error("failed") } }

        assertThat(failure.isFailure).isTrue()
        assertThat(cache.get(1) { "value" }).isEqualTo("value")
    }
}
