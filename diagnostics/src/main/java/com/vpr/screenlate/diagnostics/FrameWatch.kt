package com.vpr.screenlate.diagnostics

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window

/**
 * Frame times of the app's screens (activities only: the overlay's windows have no [Window] to ask). Each time a
 * screen leaves the foreground one event sums up its frames: how many, how many missed their deadline and the worst.
 */
internal class FrameWatch(private val handler: Handler) : Application.ActivityLifecycleCallbacks {
    private val sessions = mutableMapOf<Activity, Session>()

    private class Session(val started: Long) : Window.OnFrameMetricsAvailableListener {
        var frames = 0
        var janky = 0
        var worstNs = 0L

        override fun onFrameMetricsAvailable(window: Window, metrics: FrameMetrics, dropCount: Int) {
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val deadline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                metrics.getMetric(FrameMetrics.DEADLINE)
            } else {
                FALLBACK_DEADLINE_NS
            }
            frames++
            if (total > deadline) janky++
            if (total > worstNs) worstNs = total
        }
    }

    override fun onActivityResumed(activity: Activity) {
        val session = Session(SystemClock.elapsedRealtime())
        sessions[activity] = session
        runCatching { activity.window.addOnFrameMetricsAvailableListener(session, handler) }
    }

    override fun onActivityPaused(activity: Activity) {
        val session = sessions.remove(activity) ?: return
        runCatching { activity.window.removeOnFrameMetricsAvailableListener(session) }
        // The listener runs on the handler's thread; reading its counts there keeps them consistent.
        handler.post {
            Diag.event(
                "frames",
                "screen" to activity.javaClass.simpleName,
                "shown_ms" to SystemClock.elapsedRealtime() - session.started,
                "frames" to session.frames,
                "janky" to session.janky,
                "worst_ms" to session.worstNs / 1_000_000,
            )
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
        Diag.event("activity", "screen" to activity.javaClass.simpleName, "state" to "created", "restored" to (savedInstanceState != null))

    override fun onActivityDestroyed(activity: Activity) =
        Diag.event("activity", "screen" to activity.javaClass.simpleName, "state" to "destroyed", "finishing" to activity.isFinishing)

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object {
        const val FALLBACK_DEADLINE_NS = 16_666_667L
    }
}
