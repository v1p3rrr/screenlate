package com.vpr.screenlate.overlay

import android.view.accessibility.AccessibilityWindowInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServiceStopDiagnosticsTest {

    private val diagnostics = ServiceStopDiagnostics(
        ownPackage = "com.vpr.screenlate",
        isSystemPackage = { it.startsWith("com.android.") || it.startsWith("com.coloros.") },
    )

    @Test
    fun `reports the enabled list, the last touch and scan, and the recent window events`() {
        diagnostics.scanStarted(1_000)
        diagnostics.windowStateChanged(1_500, "com.coloros.safecenter", "com.coloros.safecenter.Dialog")
        diagnostics.windowAdded(1_600, id = 7, type = AccessibilityWindowInfo.TYPE_SYSTEM, packageName = "com.android.systemui")
        diagnostics.bubbleTouchEnded(2_000)
        diagnostics.windowRemoved(2_500, id = 7)

        assertThat(diagnostics.report(now = 3_200, stillEnabled = false)).isEqualTo(
            "Service stopped; on the enabled list: no; bubble touch ended: 1200 ms ago; scan started: 2200 ms ago; " +
                "window events in the last 10 s:" +
                "\n  -1700 ms front: com.coloros.safecenter (com.coloros.safecenter.Dialog)" +
                "\n  -1600 ms added: system window of com.android.systemui" +
                "\n  -700 ms removed: system window of com.android.systemui",
        )
    }

    @Test
    fun `names only system packages and Screenlate`() {
        diagnostics.windowStateChanged(100, "org.example.reader", "org.example.reader.BookActivity")
        diagnostics.windowAdded(200, id = 3, type = AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY, packageName = "com.vpr.screenlate")

        val report = diagnostics.report(now = 300, stillEnabled = true)
        assertThat(report).doesNotContain("org.example")
        assertThat(report).contains("front: an app")
        assertThat(report).contains("added: accessibility overlay window of Screenlate")
        assertThat(report).contains("on the enabled list: yes")
    }

    @Test
    fun `forgets events older than ten seconds`() {
        diagnostics.windowStateChanged(1_000, "com.android.systemui", null)
        diagnostics.windowStateChanged(20_000, "com.android.settings", null)

        val report = diagnostics.report(now = 21_000, stillEnabled = false)
        assertThat(report).doesNotContain("systemui")
        assertThat(report).contains("-1000 ms front: com.android.settings")
        assertThat(report).contains("bubble touch ended: never")
    }

    @Test
    fun `says so when no window changed`() {
        assertThat(diagnostics.report(now = 5_000, stillEnabled = true)).endsWith("; no window events in the last 10 s")
    }

    @Test
    fun `takes hidden packages of the system and phone makers for system ones`() {
        assertThat(hasSystemPackagePrefix("android")).isTrue()
        assertThat(hasSystemPackagePrefix("com.google.android.apps.nexuslauncher")).isTrue()
        assertThat(hasSystemPackagePrefix("com.oplus.safecenter")).isTrue()
        assertThat(hasSystemPackagePrefix("com.coloros.securitypermission")).isTrue()
        assertThat(hasSystemPackagePrefix("org.example.reader")).isFalse()
        assertThat(hasSystemPackagePrefix("com.androidx.example")).isFalse()
        assertThat(hasSystemPackagePrefix("android.example")).isFalse()
    }
}
