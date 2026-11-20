package com.vpr.screenlate.logs

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/** One past process exit as [ApplicationExitInfo] reports it; a plain class so the formatting is testable. */
data class ExitRecord(
    val timestamp: Long,
    val pid: Int,
    val reason: Int,
    val status: Int,
    val importance: Int,
    val pssKb: Long,
    val rssKb: Long,
    val description: String?,
    val processName: String? = null,
    val realUid: Int? = null,
    val packageUid: Int? = null,
    val stateSummary: String? = null,
)

/** Turns the system's records of how earlier processes ended into log lines. */
object ExitReasons {
    private const val MAX_DESCRIPTION = 200

    /** The exits after [seenUpTo], oldest first. */
    fun newSince(records: List<ExitRecord>, seenUpTo: Long): List<ExitRecord> =
        records.filter { it.timestamp > seenUpTo }.sortedBy { it.timestamp }

    /** External services remain in the journal, but cannot replace the main process's exit in About. */
    fun latestMainProcess(records: List<ExitRecord>, mainProcess: String): ExitRecord? =
        records.filter { it.processName == mainProcess }.maxByOrNull { it.timestamp }

    fun readRecords(context: Context): List<ExitRecord> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        return manager.getHistoricalProcessExitReasons(context.packageName, 0, 64).map { record ->
            ExitRecord(record.timestamp, record.pid, record.reason, record.status, record.importance,
                record.pss, record.rss, record.description, record.processName, record.realUid, record.packageUid,
                record.processStateSummary?.toString(Charsets.US_ASCII)?.takeIf { it.startsWith("v1 e=") }
                    ?.replace(Regex("[^a-zA-Z0-9 =]"), "?")?.take(128))
        }
    }

    /** The latest main-process exit, or null if unsupported / absent. */
    fun lastExit(context: Context): ExitRecord? = runCatching {
        latestMainProcess(readRecords(context), context.applicationInfo.processName)
    }.getOrNull()

    /**
     * A crash's description may quote the exception message, which may carry text, so it is left out; the crash line
     * of the app's own log has the stack trace.
     */
    fun describe(record: ExitRecord, detailed: Boolean = true, time: (Long) -> String): String = buildString {
        append("Process ${record.pid} ended ${time(record.timestamp)}: ${reasonName(record.reason)}")
        append(", status ${record.status}, importance ${importanceName(record.importance)}")
        append(", last sampled pss ${record.pssKb / 1024} MB, rss ${record.rssKb / 1024} MB")
        if (detailed) {
            record.processName?.let { append(", process $it") }
            record.realUid?.let { append(", uid $it, package uid ${record.packageUid}") }
            record.stateSummary?.let { append(", prior state [$it]") }
        }
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
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
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
