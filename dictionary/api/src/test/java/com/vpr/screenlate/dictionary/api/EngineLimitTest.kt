package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EngineLimitTest {
    @Test
    fun oneDictionaryKeepsTheEngineCut() {
        assertThat(engineLimit(limit = 32, termDictionaries = 1)).isEqualTo(32)
        assertThat(engineLimit(limit = 32, termDictionaries = 0)).isEqualTo(32)
    }

    @Test
    fun severalDictionariesGetEveryCandidate() {
        assertThat(engineLimit(limit = 32, termDictionaries = 2)).isEqualTo(Int.MAX_VALUE)
    }
}
