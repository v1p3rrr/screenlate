package com.vpr.screenlate.overlay.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScreenCapturerTest {

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
