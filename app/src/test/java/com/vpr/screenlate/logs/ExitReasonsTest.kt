package com.vpr.screenlate.logs

import android.app.ApplicationExitInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExitReasonsTest {

    private fun record(timestamp: Long, reason: Int, description: String? = null) = ExitRecord(
        timestamp = timestamp,
        pid = 4321,
        reason = reason,
        status = 0,
        importance = 400,
        pssKb = 120 * 1024,
        rssKb = 250 * 1024,
        description = description,
    )

    @Test
    fun `keeps only exits after the last one logged, oldest first`() {
        val records = listOf(
            record(300, ApplicationExitInfo.REASON_USER_REQUESTED),
            record(200, ApplicationExitInfo.REASON_LOW_MEMORY),
            record(100, ApplicationExitInfo.REASON_OTHER),
        )

        assertThat(ExitReasons.newSince(records, seenUpTo = 100).map { it.timestamp }).containsExactly(200L, 300L).inOrder()
        assertThat(ExitReasons.newSince(records, seenUpTo = 300)).isEmpty()
    }

    @Test
    fun `describes the reason, importance, memory and the system's description`() {
        val line = ExitReasons.describe(record(1, ApplicationExitInfo.REASON_USER_REQUESTED, "remove task")) { "at $it" }

        assertThat(line).isEqualTo(
            "Process 4321 ended at 1: USER_REQUESTED, status 0, importance 400 (cached), " +
                "last sampled pss 120 MB, rss 250 MB, \"remove task\"",
        )
    }

    @Test
    fun `leaves out a crash's description, which may quote the exception message`() {
        val line = ExitReasons.describe(record(1, ApplicationExitInfo.REASON_CRASH, "Unexpected word 読む")) { "" }

        assertThat(line).contains("CRASH")
        assertThat(line).doesNotContain("読む")
    }

    @Test
    fun `names reasons newer than the minimum SDK and unknown ones`() {
        assertThat(ExitReasons.reasonName(14)).isEqualTo("FREEZER")
        assertThat(ExitReasons.reasonName(15)).isEqualTo("PACKAGE_STATE_CHANGE")
        assertThat(ExitReasons.reasonName(16)).isEqualTo("PACKAGE_UPDATED")
        assertThat(ExitReasons.reasonName(99)).isEqualTo("reason 99")
    }
    @Test
    fun `latest main exit is not masked by a later isolated renderer exit`() {
        val main = record(100, ApplicationExitInfo.REASON_USER_REQUESTED).copy(processName = "com.vpr.screenlate.debug")
        val renderer = record(110, ApplicationExitInfo.REASON_OTHER).copy(processName = "sandboxed_process0")
        assertThat(ExitReasons.latestMainProcess(listOf(renderer, main), "com.vpr.screenlate.debug")).isEqualTo(main)
        assertThat(ExitReasons.latestMainProcess(listOf(renderer), "com.vpr.screenlate.debug")).isNull()
    }

    @Test
    fun `exit metadata identifies process and marks memory as last sampled`() {
        val exit = record(100, ApplicationExitInfo.REASON_OTHER).copy(
            processName = "sandboxed_process0", realUid = 99001, packageUid = 10123,
        )
        val line = ExitReasons.describe(exit) { "at $it" }
        assertThat(line).contains("process sandboxed_process0")
        assertThat(line).contains("uid 99001, package uid 10123")
        assertThat(line).contains("last sampled")
    }

    @Test
    fun `user requested reason does not assert every task removal is a force-stop`() {
        assertThat(ExitReasons.reasonName(ApplicationExitInfo.REASON_USER_REQUESTED)).isEqualTo("USER_REQUESTED")
    }

}
