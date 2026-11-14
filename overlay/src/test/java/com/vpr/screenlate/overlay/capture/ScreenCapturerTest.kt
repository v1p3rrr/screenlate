package com.vpr.screenlate.overlay.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Test
import sun.misc.Unsafe

class ScreenCapturerTest {

    private class StubAccessibilityService : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
        override fun onInterrupt() {}
    }

    private fun unsafe(): Unsafe =
        Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe

    @Test
    fun `capturer factory uses accessibility screenshots starting at API 30`() {
        val service = unsafe().allocateInstance(StubAccessibilityService::class.java) as AccessibilityService
        val executor = Executor { it.run() }
        assertThat(ScreenCapturer.create(service, executor, 24)).isInstanceOf(LegacyMediaProjectionCapturer::class.java)
        assertThat(ScreenCapturer.create(service, executor, 29)).isInstanceOf(LegacyMediaProjectionCapturer::class.java)
        assertThat(ScreenCapturer.create(service, executor, 30)).isInstanceOf(ModernAccessibilityCapturer::class.java)
        assertThat(ScreenCapturer.create(service, executor, 34)).isInstanceOf(ModernAccessibilityCapturer::class.java)
    }

    @Test
    fun `capture exceptions carry descriptive messages`() {
        val consentDenied = CaptureException.ConsentDenied()
        assertThat(consentDenied.message).isEqualTo("Screenshot consent was not granted")

        val secureWindow = CaptureException.SecureWindow()
        assertThat(secureWindow.message).isEqualTo("The app blocks screenshots")

        val failed = CaptureException.Failed(42)
        assertThat(failed.message).isEqualTo("Screenshot failed with code 42")
    }
}
