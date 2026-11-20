package com.vpr.screenlate.core.common.diagnostics

/** Process-local facts; a WebView object is not proof that its separate renderer is resident. */
data class BackgroundDiagnosticState(
    val serviceConnected: Boolean = false,
    val keepAliveRequested: Boolean = false,
    val foregroundService: Boolean = false,
    val docked: Boolean = true,
    val scanOpen: Boolean = false,
    val scanSequence: Long = 0,
    val recognitionJobs: Int = 0,
    val catcherWidth: Int = 1,
    val catcherHeight: Int = 1,
    val catcherFlags: Int = 0,
    val popupVisible: Boolean = false,
    val catcherAttached: Boolean = false,
    val catcherActive: Boolean = false,
    val webViewPresent: Boolean = false,
    val activityResumed: Boolean = false,
    val textSource: String = "unknown",
    val ocrMode: String = "unknown",
    val reduceOcrLoad: Boolean = false,
    val hideOnOutsideTap: Boolean = false,
) {
    fun describe(): String = "service=$serviceConnected keepAliveRequested=$keepAliveRequested " +
        "foregroundService=$foregroundService docked=$docked scanOpen=$scanOpen scan=$scanSequence " +
        "popup=$popupVisible catcherAttached=$catcherAttached catcherActive=$catcherActive webView=$webViewPresent " +
        "recognitionJobs=$recognitionJobs catcherSize=${catcherWidth}x$catcherHeight catcherFlags=$catcherFlags " +
        "activityResumed=$activityResumed textSource=$textSource ocrMode=$ocrMode reduceOcrLoad=$reduceOcrLoad " +
        "hideOnOutsideTap=$hideOnOutsideTap"
}

internal data class DiagnosticCpuPoint(val elapsedMs: Long, val uptimeMs: Long, val cpuMs: Long)

internal fun cpuInterval(previous: DiagnosticCpuPoint?, current: DiagnosticCpuPoint): String {
    if (previous == null) return "cpuDelta=unavailable"
    val elapsed = current.elapsedMs - previous.elapsedMs
    val awake = current.uptimeMs - previous.uptimeMs
    val cpu = current.cpuMs - previous.cpuMs
    if (elapsed <= 0 || awake < 0 || cpu < 0) return "cpuDelta=unavailable"
    // 1000 permille is one fully occupied core; CPU time can exceed elapsed time on a multicore process.
    return "cpuDeltaMs=$cpu elapsedDeltaMs=$elapsed awakeDeltaMs=$awake cpuCorePermille=${cpu * 1000 / elapsed}"
}

/** Event samples are bounded independently of event logging; the periodic timer shares the same budget. */
internal class DiagnosticSampleGate(private val minimumIntervalMs: Long = 5_000) {
    private var last: Long? = null

    fun admit(now: Long): Boolean {
        val before = last
        if (before != null && now >= before && now - before < minimumIntervalMs) return false
        last = now
        return true
    }
}
