package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScanNotesTest {
    private val taberu = "食べる" to "たべる"
    private val notes = ScanNotes()

    @Test
    fun `a note of the open scan is remembered`() {
        assertThat(notes.add(notes.scan, taberu, listOf(1L))).isTrue()
        assertThat(notes[taberu]).containsExactly(1L)
    }

    @Test
    fun `closing forgets the scan's notes`() {
        notes.add(notes.scan, taberu, listOf(1L))
        notes.close()
        assertThat(notes[taberu]).isNull()
    }

    @Test
    fun `a note that finishes after its scan closed stays out of the next one`() {
        val started = notes.scan
        notes.close()
        assertThat(notes.add(started, taberu, listOf(1L))).isFalse()
        assertThat(notes[taberu]).isNull()
    }
}
