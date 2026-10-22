package com.vpr.screenlate.overlay

import android.view.accessibility.AccessibilityWindowInfo

/**
 * What happened in the seconds before the accessibility service stopped, for the log: some phones turn the service
 * off on their own, and nothing else in the app shows when or after what. Names packages of system apps only; other
 * apps appear as "an app". Times are on one monotonic clock.
 */
class ServiceStopDiagnostics(
    private val ownPackage: String,
    private val isSystemPackage: (String) -> Boolean,
) {
    private class Event(val time: Long, val text: String)

    private val events = ArrayDeque<Event>()
    private val windows = HashMap<Int, String>()
    private var touchEnded: Long? = null
    private var scanStarted: Long? = null

    /** A window came to the front (an activity, dialog or panel). */
    fun windowStateChanged(time: Long, packageName: String?, className: String?) {
        add(time, "front: ${source(packageName, className)}")
    }

    fun windowAdded(time: Long, id: Int, type: Int, packageName: String?) {
        val text = "${typeName(type)} window of ${source(packageName, null)}"
        if (windows.size >= MAX_WINDOWS) windows.clear()
        windows[id] = text
        add(time, "added: $text")
    }

    fun windowRemoved(time: Long, id: Int) {
        add(time, "removed: ${windows.remove(id) ?: "a window"}")
    }

    /** The finger left the bubble, or the system took the touch. */
    fun bubbleTouchEnded(time: Long) {
        touchEnded = time
    }

    fun scanStarted(time: Long) {
        scanStarted = time
    }

    /** The log text for a stop at [now]; [stillEnabled] is whether the service is still on the enabled list. */
    fun report(now: Long, stillEnabled: Boolean): String = buildString {
        append("Service stopped; on the enabled list: ${if (stillEnabled) "yes" else "no"}")
        append("; bubble touch ended: ${ago(now, touchEnded)}; scan started: ${ago(now, scanStarted)}")
        val recent = events.filter { now - it.time <= WINDOW_MS }
        if (recent.isEmpty()) {
            append("; no window events in the last ${WINDOW_MS / 1000} s")
        } else {
            append("; window events in the last ${WINDOW_MS / 1000} s:")
            recent.forEach { append("\n  -${now - it.time} ms ${it.text}") }
        }
    }

    private fun add(time: Long, text: String) {
        events.addLast(Event(time, text))
        while (events.size > MAX_EVENTS || time - events.first().time > WINDOW_MS) events.removeFirst()
    }

    private fun source(packageName: String?, className: String?): String = when {
        packageName == null -> "unknown"
        packageName == ownPackage -> "Screenlate"
        isSystemPackage(packageName) -> if (className == null) packageName else "$packageName ($className)"
        else -> "an app"
    }

    private fun ago(now: Long, time: Long?): String = if (time == null) "never" else "${now - time} ms ago"

    private fun typeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "app"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "keyboard"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "accessibility overlay"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "split-screen divider"
        AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY -> "magnification"
        else -> "type $type"
    }

    private companion object {
        const val WINDOW_MS = 10_000L
        const val MAX_EVENTS = 40
        const val MAX_WINDOWS = 64
    }
}

private val systemPackagePrefixes = listOf(
    "com.android.", "com.google.android.",
    "com.coloros.", "com.oplus.", "com.oppo.", "com.heytap.", "com.nearme.", "com.oneplus.", "com.realme.",
    "com.miui.", "com.xiaomi.", "com.huawei.", "com.hihonor.", "com.honor.", "com.samsung.", "com.sec.",
    "com.vivo.", "com.iqoo.", "com.bbk.", "com.transsion.", "com.motorola.", "com.sonymobile.", "com.asus.",
    "com.nothing.", "com.meizu.",
)

/**
 * Whether a package the app cannot see looks like part of the system or the phone maker's software. Since Android 11
 * the system's own packages without a launcher icon (the home screen, system panels and dialogs) are hidden from apps.
 */
internal fun hasSystemPackagePrefix(name: String): Boolean =
    name == "android" || systemPackagePrefixes.any { name.startsWith(it) }
