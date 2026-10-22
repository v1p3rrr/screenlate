package com.vpr.screenlate.core.common.log

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    // Long waits for the writer thread: a loaded machine can take more than the app's limits to write the queued lines.
    private fun logFile(maxFileBytes: Long = LogFile.DEFAULT_MAX_FILE_BYTES, directory: File = folder.root) = LogFile(
        directory,
        maxFileBytes,
        clock = { 1_761_120_000_123 },
        zone = ZoneOffset.UTC,
        processId = 4321,
        flushMs = 60_000,
        crashFlushMs = 60_000,
    )

    private fun LogFile.text(): String = ByteArrayOutputStream().also { copyTo(it) }.toString(Charsets.UTF_8)

    @Test
    fun `writes lines in call order with time, process, level and tag`() {
        val log = logFile()
        log.append('I', "ScreenlateService", "Service connected")
        log.append('W', "OverlayController", "Scan failed")

        val lines = log.text().lines().filter { it.isNotEmpty() }
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).startsWith("2025-10-22 08:00:00.123  4321 ")
        assertThat(lines[0]).endsWith(" I ScreenlateService: Service connected")
        assertThat(lines[1]).endsWith(" W OverlayController: Scan failed")
    }

    @Test
    fun `prefixes every line of a stack trace`() {
        val log = logFile()
        log.append('W', "Tag", "Failed", IOException("broken"))

        val lines = log.text().lines().filter { it.isNotEmpty() }
        assertThat(lines.size).isGreaterThan(2)
        assertThat(lines[1]).endsWith(" W Tag: java.io.IOException: broken")
        lines.forEach { assertThat(it).contains(" W Tag: ") }
    }

    @Test
    fun `keeps two files at most and drops the oldest lines`() {
        val log = logFile(maxFileBytes = 1_000)
        repeat(90) { log.append('I', "Tag", "Line $it") }

        val text = log.text()
        assertThat(File(folder.root, LogFile.CURRENT).length()).isLessThan(1_000)
        assertThat(File(folder.root, LogFile.PREVIOUS).length()).isAtLeast(1_000)
        assertThat(folder.root.list()!!.toList()).containsExactly(LogFile.CURRENT, LogFile.PREVIOUS)
        assertThat(text).doesNotContain("Tag: Line 0\n")
        assertThat(text).endsWith("Tag: Line 89\n")
        val numbers = text.lines().filter { it.isNotEmpty() }.map { it.substringAfterLast(' ').toInt() }
        assertThat(numbers).isInStrictOrder()
    }

    @Test
    fun `a new process appends to the lines of the last one`() {
        logFile().apply { append('I', "Tag", "First process") }.flush()
        val log = logFile()
        log.append('I', "Tag", "Second process")

        assertThat(log.text()).containsMatch("(?s)First process.*Second process")
    }

    @Test
    fun `writeNow follows the queued lines`() {
        val log = logFile()
        repeat(50) { log.append('I', "Tag", "Queued $it") }
        log.writeNow('E', "Crash", "Uncaught exception")

        val lines = log.text().lines().filter { it.isNotEmpty() }
        assertThat(lines).hasSize(51)
        assertThat(lines.last()).endsWith(" E Crash: Uncaught exception")
    }

    @Test
    fun `an unwritable directory drops lines without failing`() {
        val blocked = folder.newFile("not-a-directory")
        val log = logFile(directory = File(blocked, "logs"))
        log.append('I', "Tag", "Lost")
        log.flush()

        assertThat(log.text()).isEmpty()
    }
}
