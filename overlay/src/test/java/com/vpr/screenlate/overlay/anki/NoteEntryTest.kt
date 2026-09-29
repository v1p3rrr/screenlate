package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoteEntryTest {
    private val taberu = "食べる" to "たべる"
    private val nomu = "飲む" to "のむ"
    private val miru = "見る" to "みる"

    @Test
    fun `the entry stays where the button was pressed`() {
        assertThat(entryOf(listOf(taberu, nomu), 1, nomu)).isEqualTo(1)
    }

    @Test
    fun `a term shown at another index is found there`() {
        assertThat(entryOf(listOf(miru, taberu, nomu), 0, taberu)).isEqualTo(1)
    }

    @Test
    fun `another word in the popup gets nothing`() {
        assertThat(entryOf(listOf(miru, nomu), 0, taberu)).isNull()
        assertThat(entryOf(emptyList(), 0, taberu)).isNull()
    }
}
