package com.vpr.screenlate.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Why earlier processes of the app ended ([ApplicationExitInfo], kept by the system, no permission needed), why this
 * one started, and a short state for the next process to read about this one ([setProcessStateSummary]).
 */
internal object ProcessInfo {
    private const val CURSOR = "exit-cursor.txt"
    private const val MAX_TRACE_BYTES = 1L shl 20
    private const val MAX_SUMMARY = 128
    private const val SUMMARY_PREFIX = "sl;"

    fun start(): Map<String, Any?> = linkedMapOf(
        "pid" to Process.myPid(),
        "since_start_ms" to SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime(),
        "requested_ms_ago" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SystemClock.elapsedRealtime() - Process.getStartRequestedElapsedRealtime()
        } else {
            null
        },
    )

    /** Exits recorded since the last process looked; ANR traces are copied into [traces]. */
    fun exits(context: Context, directory: File, traces: File) {
        val manager = context.getSystemService(ActivityManager::class.java)
        val cursorFile = File(directory, CURSOR)
        val cursor = runCatching { cursorFile.readText().trim().toLong() }.getOrDefault(0L)
        val exits = runCatching { manager.getHistoricalProcessExitReasons(null, 0, 0) }.getOrDefault(emptyList())
            .filter { it.timestamp > cursor }
            .sortedBy { it.timestamp }
        for (exit in exits) {
            val trace = if (exit.reason == ApplicationExitInfo.REASON_ANR) saveTrace(exit, traces) else null
            Diag.event(
                "exit",
                "at" to exit.timestamp,
                "pid" to exit.pid,
                "process" to exit.processName,
                "reason" to reasonName(exit.reason),
                "status" to exit.status,
                "importance" to exit.importance,
                "pss_kb" to exit.pss,
                "rss_kb" to exit.rss,
                "description" to exit.description,
                "state" to exit.processStateSummary?.let(::summaryText),
                "trace" to trace,
            )
        }
        exits.lastOrNull()?.let { cursorFile.writeText(it.timestamp.toString()) }
    }

    private fun saveTrace(exit: ApplicationExitInfo, traces: File): String? = runCatching {
        val input = exit.traceInputStream ?: return null
        traces.mkdirs()
        val file = File(traces, "anr-${exit.timestamp}.txt")
        input.use { stream -> file.outputStream().use { out -> copyLimited(stream, out) } }
        traces.listFiles().orEmpty().sortedBy { it.name }.dropLast(MAX_TRACES).forEach(File::delete)
        file.name
    }.getOrNull()

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(8192)
        var left = MAX_TRACE_BYTES
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read < 0) break
            output.write(buffer, 0, read)
            left -= read
        }
    }

    /** Why the system started this process (Android 15 and newer); completed once the app has drawn or settled. */
    fun watchStart(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val manager = context.getSystemService(ActivityManager::class.java)
        runCatching {
            manager.addApplicationStartInfoCompletionListener(context.mainExecutor) { info -> Diag.event("process_start", *startFields(info)) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun startFields(info: ApplicationStartInfo): Array<Pair<String, Any?>> {
        val times = info.startupTimestamps
        val launch = times[ApplicationStartInfo.START_TIMESTAMP_LAUNCH]
        fun after(key: Int) = times[key]?.let { at -> launch?.let { (at - it) / 1_000_000 } }
        return arrayOf(
            "reason" to info.reason,
            "type" to info.startType,
            "state" to info.startupState,
            "force_stopped_before" to info.wasForceStopped(),
            "fork_ms" to after(ApplicationStartInfo.START_TIMESTAMP_FORK),
            "bind_ms" to after(ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION),
            "app_create_ms" to after(ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE),
            "first_frame_ms" to after(ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME),
        )
    }

    /**
     * Up to 128 bytes that the next process reads in this one's [ApplicationExitInfo]. The WebView sets a summary of its
     * own when it loads, so ours starts with [SUMMARY_PREFIX].
     */
    fun setSummary(context: Context, state: Map<String, String>) {
        val text = (SUMMARY_PREFIX + state.entries.joinToString(";") { "${it.key}=${it.value}" }).take(MAX_SUMMARY)
        runCatching { context.getSystemService(ActivityManager::class.java).setProcessStateSummary(text.encodeToByteArray()) }
    }

    /** A summary of ours without the prefix; another writer's (the WebView's binary one) only as its size. */
    fun summaryText(bytes: ByteArray): String {
        val text = bytes.decodeToString()
        return if (text.startsWith(SUMMARY_PREFIX)) text.removePrefix(SUMMARY_PREFIX) else "other:${bytes.size}b"
    }

    fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "init_failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resources"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
        ApplicationExitInfo.REASON_OTHER -> "other"
        ApplicationExitInfo.REASON_FREEZER -> "freezer"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package_state_change"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "package_updated"
        else -> "reason_$reason"
    }

    private const val MAX_TRACES = 4
}
