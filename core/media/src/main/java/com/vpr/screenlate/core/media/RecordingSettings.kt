package com.vpr.screenlate.core.media

/**
 * User preferences for sentence audio recording.
 *
 * @property recordingEnabled whether background sentence audio recording is turned on.
 * @property defaultEditor whether the audio editor opens by default when adding notes.
 * @property limits the history, waiting, and editor duration limits for a scan.
 * @property minSilenceMillis minimum silence duration for manual pause cuts (default 1500 ms).
 * @property silenceMarginMillis edge preservation margin around silence cuts (default 150 ms).
 */
data class RecordingSettings(
    val recordingEnabled: Boolean = false,
    val defaultEditor: Boolean = true,
    val limits: RecordingLimits = RecordingLimits(),
    val minSilenceMillis: Long = DEFAULT_MIN_SILENCE_MILLIS,
    val silenceMarginMillis: Long = DEFAULT_SILENCE_MARGIN_MILLIS,
) {
    init {
        require(minSilenceMillis in MIN_SILENCE_RANGE)
        require(silenceMarginMillis in SILENCE_MARGIN_RANGE)
    }

    companion object {
        const val DEFAULT_MIN_SILENCE_MILLIS = 1500L
        const val DEFAULT_SILENCE_MARGIN_MILLIS = 150L
        val MIN_SILENCE_RANGE = 100L..30_000L
        val SILENCE_MARGIN_RANGE = 0L..2_000L
    }
}
