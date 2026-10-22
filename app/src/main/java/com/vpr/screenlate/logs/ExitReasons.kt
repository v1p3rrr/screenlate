package com.vpr.screenlate.logs

import android.app.ApplicationExitInfo

/** One past process exit as [ApplicationExitInfo] reports it; a plain class so the formatting is testable. */
internal data class ExitRecord(
    val timestamp: Long,
    val pid: Int,
    val reason: Int,
    val status: Int,
    val importance: Int,
    val pssKb: Long,
    val rssKb: Long,
    val description: String?,
)

/** Turns the system's records of how earlier processes ended into log lines. */
internal object ExitReasons {
    private const val MAX_DESCRIPTION = 200

    /** The exits after [seenUpTo], oldest first. */
    fun newSince(records: List<ExitRecord>, seenUpTo: Long): List<ExitRecord> =
        records.filter { it.timestamp > seenUpTo }.sortedBy { it.timestamp }

    /**
     * A crash's description may quote the exception message, which may carry text, so it is left out; the crash line
     * of the app's own log has the stack trace.
     */
    fun describe(record: ExitRecord, time: (Long) -> String): String = buildString {
        append("Process ${record.pid} ended ${time(record.timestamp)}: ${reasonName(record.reason)}")
        append(", status ${record.status}, importance ${importanceName(record.importance)}")
        append(", pss ${record.pssKb / 1024} MB, rss ${record.rssKb / 1024} MB")
        val description = record.description?.takeIf { it.isNotBlank() && record.reason !in CRASHES }
        if (description != null) append(", \"${description.take(MAX_DESCRIPTION)}\"")
    }

    fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED (force-stop)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        REASON_FREEZER -> "FREEZER"
        REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "reason $reason"
    }

    private fun importanceName(importance: Int): String = when (importance) {
        100 -> "100 (foreground)"
        125 -> "125 (foreground service)"
        200 -> "200 (visible)"
        230 -> "230 (perceptible)"
        300 -> "300 (service)"
        325 -> "325 (top sleeping)"
        350 -> "350 (can't save state)"
        400 -> "400 (cached)"
        1000 -> "1000 (gone)"
        else -> importance.toString()
    }

    private val CRASHES = setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE)

    // ApplicationExitInfo's values from API 34, kept here so that lint does not flag them below that.
    private const val REASON_FREEZER = 14
    private const val REASON_PACKAGE_STATE_CHANGE = 15
    private const val REASON_PACKAGE_UPDATED = 16
}
