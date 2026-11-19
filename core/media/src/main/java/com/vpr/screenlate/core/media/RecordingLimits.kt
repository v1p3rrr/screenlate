package com.vpr.screenlate.core.media

/** A scan takes one immutable copy of these settings, including its not-yet-started editor allowance. */
data class RecordingLimits(
    val historyMillis: Long = 300_000,
    val waitingMillis: Long = 300_000,
    val editorMillis: Long = 120_000,
) {
    init {
        for (value in listOf(historyMillis, waitingMillis, editorMillis)) {
            require(value in 30_000..600_000 && value % 30_000 == 0L)
        }
    }
}

object PlaybackRecordingAvailability {
    const val MIN_SDK = 29

    fun supported(sdk: Int): Boolean = sdk >= MIN_SDK
}
