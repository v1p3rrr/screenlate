package com.vpr.screenlate.dictionary.api.imports

import android.os.SystemClock

/**
 * Lets a progress percent through when it changed and [intervalMs] has passed since the last one let through. Every
 * update is a WorkManager database write that the screens showing the tasks rebuild from, and a fast download moves a
 * percent many times a second.
 */
internal class ProgressThrottle(
    private val intervalMs: Long = INTERVAL_MS,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var reported: Int? = null
    private var reportedAt = 0L

    /** Whether to report [percent] now; the first percent always goes through. */
    fun pass(percent: Int): Boolean {
        val now = clock()
        val last = reported
        if (last != null && (percent == last || now - reportedAt < intervalMs)) return false
        reported = percent
        reportedAt = now
        return true
    }

    companion object {
        const val INTERVAL_MS = 500L
    }
}
