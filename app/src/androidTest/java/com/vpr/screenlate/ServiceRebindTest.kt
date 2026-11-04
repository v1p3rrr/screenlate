package com.vpr.screenlate

import android.app.UiAutomation
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.overlay.OverlayServiceStatus
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only the disposable verification application's service and restores the system settings. */
@RunWith(AndroidJUnit4::class)
class ServiceRebindTest {
    @Test
    fun runningStateFollowsQuickRebinds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName == "com.vpr.screenlate.verification")
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val resolver = context.contentResolver
        val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        val enabledKey = Settings.Secure.ACCESSIBILITY_ENABLED
        val original = Settings.Secure.getString(resolver, key)
        val originalEnabled = Settings.Secure.getString(resolver, enabledKey)
        val component = "${context.packageName}/com.vpr.screenlate.overlay.ScreenlateAccessibilityService"
        assumeTrue(original.orEmpty().split(':').none { it.startsWith(context.packageName + "/") })
        automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
        try {
            // Avoid competing Screenlate overlays while leaving unrelated accessibility services intact.
            val others = original.orEmpty().split(':').filter {
                it.isNotBlank() && !it.contains("com.vpr.screenlate.overlay.ScreenlateAccessibilityService")
            }
            fun enable(on: Boolean) {
                Settings.Secure.putString(resolver, key, (others + if (on) listOf(component) else emptyList()).joinToString(":"))
                Settings.Secure.putInt(resolver, enabledKey, 1)
            }
            enable(true)
            await { OverlayServiceStatus.running.value }
            repeat(12) {
                enable(false)
                SystemClock.sleep(100)
                enable(true)
                await { OverlayServiceStatus.running.value }
                SystemClock.sleep(350)
                assertThat(OverlayServiceStatus.running.value).isTrue()
            }
            enable(false)
            await { !OverlayServiceStatus.running.value }
        } finally {
            Settings.Secure.putString(resolver, key, original)
            Settings.Secure.putString(resolver, enabledKey, originalEnabled)
            automation.dropShellPermissionIdentity()
            assertThat(Settings.Secure.getString(resolver, key)).isEqualTo(original)
            assertThat(Settings.Secure.getString(resolver, enabledKey)).isEqualTo(originalEnabled)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertThat(condition()).isTrue()
    }
}
