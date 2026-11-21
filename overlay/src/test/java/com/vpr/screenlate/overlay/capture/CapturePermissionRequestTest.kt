package com.vpr.screenlate.overlay.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CapturePermissionRequestTest {
    @Test
    fun screenshotConsentDoesNotRequireAudioPermission() {
        for (sdk in listOf(24, 28, 29, 30, 37)) {
            assertThat(CapturePermissionRequest(captureAudio = false).nextStep(sdk, audioGranted = false))
                .isEqualTo(CapturePermissionStep.PROJECTION_CONSENT)
        }
    }

    @Test
    fun recordingRequestsAudioBeforeProjectionAndNeverReusesAnUnsupportedPath() {
        val request = CapturePermissionRequest(captureAudio = true)
        assertThat(request.nextStep(29, audioGranted = false)).isEqualTo(CapturePermissionStep.AUDIO_PERMISSION)
        assertThat(request.nextStep(29, audioGranted = true)).isEqualTo(CapturePermissionStep.PROJECTION_CONSENT)
        assertThat(request.nextStep(28, audioGranted = true)).isEqualTo(CapturePermissionStep.UNSUPPORTED)
    }

    @Test
    fun existingProjectionOnlyNeedsTheMissingAudioGrant() {
        val request = CapturePermissionRequest(captureAudio = true, requestProjection = false)
        assertThat(request.nextStep(29, audioGranted = false)).isEqualTo(CapturePermissionStep.AUDIO_PERMISSION)
        assertThat(request.nextStep(29, audioGranted = true)).isEqualTo(CapturePermissionStep.COMPLETE)
    }
}
