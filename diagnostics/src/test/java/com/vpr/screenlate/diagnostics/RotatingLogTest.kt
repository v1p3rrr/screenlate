package com.vpr.screenlate.diagnostics

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RotatingLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val directory: File by lazy { File(folder.root, "events") }

    private fun log() = RotatingLog(directory, "events", maxFileBytes = 10, maxFiles = 3)

    @Test
    fun `starts a new file when one is full and keeps the newest`() {
        val log = log()
        // Each line is six bytes with its line break: two fill a file.
        repeat(11) { log.append("line%02d".format(it).take(5)) }
        log.close()
        val files = log.files()
        assertThat(files.map { it.name }).containsExactly("events-0004.jsonl", "events-0005.jsonl", "events-0006.jsonl").inOrder()
        assertThat(files.last().readText()).isEqualTo("line1\n")
    }

    @Test
    fun `a new log continues the newest file while it has room`() {
        log().apply {
            append("a")
            close()
        }
        log().apply {
            append("b")
            close()
        }
        assertThat(log().files().single().readText()).isEqualTo("a\nb\n")
    }

    @Test
    fun `a new log starts after a full file`() {
        log().apply {
            append("12345")
            append("12345")
            close()
        }
        log().apply {
            append("c")
            close()
        }
        assertThat(log().files().map { it.readText() }).containsExactly("12345\n12345\n", "c\n").inOrder()
    }

    @Test
    fun `clear deletes the files and starts again from the first`() {
        val log = log()
        repeat(5) { log.append("12345") }
        log.clear()
        log.append("x")
        log.close()
        assertThat(log.files().map { it.name }).containsExactly("events-0001.jsonl")
    }

    @Test
    fun `ignores other files in the directory`() {
        directory.mkdirs()
        File(directory, "notes.txt").writeText("other")
        File(directory, "events-x.jsonl").writeText("other")
        val log = log()
        log.append("a")
        log.close()
        assertThat(log.files().map { it.name }).containsExactly("events-0001.jsonl")
    }
}
