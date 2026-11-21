package com.vpr.screenlate.overlay.capture

internal enum class CapturePermissionStep { AUDIO_PERMISSION, PROJECTION_CONSENT, COMPLETE, UNSUPPORTED }

/** Consent purpose, independent of whether a shared MediaProjection already exists. */
internal data class CapturePermissionRequest(
    val captureAudio: Boolean = false,
    val requestProjection: Boolean = true,
) {
    fun nextStep(sdk: Int, audioGranted: Boolean): CapturePermissionStep {
        if (captureAudio && sdk < 29) return CapturePermissionStep.UNSUPPORTED
        if (captureAudio && !audioGranted) return CapturePermissionStep.AUDIO_PERMISSION
        return if (requestProjection) CapturePermissionStep.PROJECTION_CONSENT else CapturePermissionStep.COMPLETE
    }
}
