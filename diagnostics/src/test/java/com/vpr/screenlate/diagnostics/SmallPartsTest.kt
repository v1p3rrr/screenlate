package com.vpr.screenlate.diagnostics

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The parsers and formatters of the diagnostics that need no device. */
class SmallPartsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the logcat copy continues after its last timestamp`() {
        val file = folder.newFile("logcat.txt")
        file.writeText(
            """
            2026-10-19 10:00:00.123  1234  1250 I Screenlate: one
            2026-10-19 10:00:01.456  1234  1250 I Screenlate: two
            --------- beginning of crash
            """.trimIndent() + "\n",
        )
        assertThat(LogcatTee.lastTimestamp(file)).isEqualTo("2026-10-19 10:00:01.456")
    }

    @Test
    fun `no timestamp without lines`() {
        assertThat(LogcatTee.lastTimestamp(File(folder.root, "missing.txt"))).isNull()
        assertThat(LogcatTee.lastTimestamp(folder.newFile("empty.txt"))).isNull()
        assertThat(LogcatTee.lastTimestamp(folder.newFile("other.txt").apply { writeText("no times here\n") })).isNull()
    }

    @Test
    fun `reads the resident size from statm`() {
        assertThat(Memory.parseStatmResident("1234 567 89 1 0 2 0\n")).isEqualTo(567L)
        assertThat(Memory.parseStatmResident("garbage")).isNull()
        assertThat(Memory.parseStatmResident("")).isNull()
    }

    @Test
    fun `a crash keeps types and frames but never the message`() {
        val error = IllegalStateException("looked-up word", IOException("recognized text"))
        val described = Crashes.describe(error)
        assertThat(described["type"]).isEqualTo("java.lang.IllegalStateException")
        assertThat(described["causes"]).isEqualTo(listOf("java.io.IOException"))
        assertThat(described["frames"] as List<*>).isNotEmpty()
        assertThat(described.values.toString()).doesNotContain("looked-up word")
        assertThat(described.values.toString()).doesNotContain("recognized text")
    }

    @Test
    fun `a crash frame names the class, method and line`() {
        val frame = StackTraceElement("com.example.Popup", "show", "Popup.kt", 42)
        assertThat(Crashes.frame(frame)).isEqualTo("com.example.Popup.show:42")
    }
}
