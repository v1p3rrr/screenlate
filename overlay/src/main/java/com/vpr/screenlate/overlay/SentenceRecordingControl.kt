package com.vpr.screenlate.overlay

import androidx.annotation.MainThread

/**
 * Global control bridge for sentence audio recording runtime actions.
 * Allows the settings UI and overlay controller to share start/stop execution.
 */
object SentenceRecordingControl {
    var startRecordingAction: (() -> Unit)? = null

    var stopRecordingAction: (() -> Unit)? = null

    val isAvailable: Boolean get() = startRecordingAction != null

    @MainThread
    fun startRecording(): Boolean {
        val action = startRecordingAction ?: return false
        action()
        return true
    }

    @MainThread
    fun stopRecording(): Boolean {
        val action = stopRecordingAction ?: return false
        action()
        return true
    }
}
