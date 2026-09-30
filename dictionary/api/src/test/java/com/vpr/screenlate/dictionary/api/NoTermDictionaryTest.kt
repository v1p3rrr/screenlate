package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NoTermDictionaryTest {

    @Test
    fun `a dictionary with definitions needs no reason`() = runTest {
        assertThat(noTermDictionary(hasTermDictionaries = true, registryEmpty = { true }, importing = { true })).isNull()
    }

    @Test
    fun `an empty registry is installing only while an import runs`() = runTest {
        assertThat(noTermDictionary(hasTermDictionaries = false, registryEmpty = { true }, importing = { true }))
            .isEqualTo(NoTermDictionary.INSTALLING)
        assertThat(noTermDictionary(hasTermDictionaries = false, registryEmpty = { true }, importing = { false }))
            .isEqualTo(NoTermDictionary.NONE_ON)
    }

    @Test
    fun `installed dictionaries that are all off are not installing`() = runTest {
        assertThat(noTermDictionary(hasTermDictionaries = false, registryEmpty = { false }, importing = { true }))
            .isEqualTo(NoTermDictionary.NONE_ON)
    }
}
