package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.model.FrequencyGroup
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import org.junit.Test

class SortFrequencyTest {

    private val result = LookupResult(
        matched = "猫",
        deinflected = "猫",
        term = TermEntry("猫", "ねこ", frequencies = listOf("JPDB", "Jiten", "VN").map { FrequencyGroup(it) }),
    )

    private fun order(dictionary: String?) =
        result.withSortFrequencyFirst(dictionary).term.frequencies.map { it.dictionary }

    @Test
    fun `the sort dictionary comes first and the others keep their order`() {
        assertThat(order("Jiten")).containsExactly("Jiten", "JPDB", "VN").inOrder()
    }

    @Test
    fun `without a sort dictionary or a match nothing moves`() {
        assertThat(order(null)).containsExactly("JPDB", "Jiten", "VN").inOrder()
        assertThat(order("Other")).containsExactly("JPDB", "Jiten", "VN").inOrder()
    }
}
