package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ContextRetentionTest {
    @Test fun readingDoesNotConsumeSeparateEditorAllowance() {
        val context = ContextRetention(0, RecordingLimits())
        assertThat(context.editorReady(270_000)).isTrue()
        assertThat(context.current(270_000).cutoffMillis).isEqualTo(390_000)
        assertThat(context.current(300_000).stage).isEqualTo(RetentionStage.EDITOR)
        assertThat(context.current(390_000).reason).isEqualTo(RetentionEnd.EDITOR_LIMIT)
    }

    @Test fun earlyEditorReplacesRatherThanExtendsWaitingDeadline() {
        val context = ContextRetention(0, RecordingLimits())
        context.editorReady(10_000)
        assertThat(context.current(10_000).cutoffMillis).isEqualTo(130_000)
        assertThat(context.current(130_001).stage).isEqualTo(RetentionStage.SEALED)
    }

    @Test fun openingAtOrAfterWaitingDeadlineDoesNotGrantAllowanceWithoutTimer() {
        for (time in listOf(300_000L, 300_001L, 3_600_000L)) {
            val context = ContextRetention(0, RecordingLimits())
            assertThat(context.editorReady(time)).isFalse()
            assertThat(context.current(time).cutoffMillis).isEqualTo(300_000)
            assertThat(context.current(time).reason).isEqualTo(RetentionEnd.WAITING_LIMIT)
            assertThat(context.current(time).firstEditorMillis).isNull()
        }
    }

    @Test fun openingJustBeforeDeadlineIgnoresOldTimerAndNeverRenews() {
        val context = ContextRetention(0, RecordingLimits())
        assertThat(context.editorReady(299_999)).isTrue()
        assertThat(context.current(300_000).stage).isEqualTo(RetentionStage.EDITOR)
        assertThat(context.editorReady(350_000)).isFalse()
        assertThat(context.current(350_000).cutoffMillis).isEqualTo(419_999)
        context.current(420_000)
        assertThat(context.editorReady(450_000)).isFalse()
    }

    @Test fun capacityFailureAndCaptureStopAreTerminal() {
        val failed = ContextRetention(0, RecordingLimits())
        assertThat(failed.editorReady(1000, capacityAvailable = false)).isFalse()
        assertThat(failed.editorReady(2000)).isFalse()
        assertThat(failed.current(2000).reason).isEqualTo(RetentionEnd.RESOURCE_LIMIT)
        assertThat(failed.current(2000).cutoffMillis).isEqualTo(1000)
        val stopped = ContextRetention(0, RecordingLimits())
        stopped.stop(1000)
        assertThat(stopped.editorReady(2000)).isFalse()
        assertThat(stopped.current(2000).reason).isEqualTo(RetentionEnd.CAPTURE_STOPPED)
    }

    @Test fun stopAfterCutoffKeepsOriginalReasonAndEndpoint() {
        val context = ContextRetention(0, RecordingLimits())
        context.stop(400_000)
        assertThat(context.current(400_000).cutoffMillis).isEqualTo(300_000)
        assertThat(context.current(400_000).reason).isEqualTo(RetentionEnd.WAITING_LIMIT)
    }

    @Test fun stopWithEarlierTimestampClampsToLastSeenMillisWithoutThrowing() {
        val context = ContextRetention(1000, RecordingLimits())
        context.current(2000)
        // Stop called with earlier timestamp (e.g. from race) clamps to last seen millis
        context.stop(1500)
        assertThat(context.current(2000).stage).isEqualTo(RetentionStage.SEALED)
        assertThat(context.current(2000).reason).isEqualTo(RetentionEnd.CAPTURE_STOPPED)
        assertThat(context.current(2000).cutoffMillis).isEqualTo(2000)
    }

    @Test fun limitsAreFrozenAndReleasedContextCannotReopen() {
        val original = RecordingLimits()
        val context = ContextRetention(0, original)
        original.copy(editorMillis = 600_000)
        context.editorReady(1000)
        assertThat(context.current(1000).cutoffMillis).isEqualTo(121_000)
        context.release()
        assertThat(context.editorReady(2000)).isFalse()
        assertThat(context.current(3_600_000).stage).isEqualTo(RetentionStage.RELEASED)
    }

    @Test fun validatesClockAndDurationBounds() {
        assertThrows(IllegalArgumentException::class.java) { RecordingLimits(historyMillis = 29_999) }
        assertThrows(IllegalArgumentException::class.java) { RecordingLimits(editorMillis = 45_000) }
        assertThrows(IllegalArgumentException::class.java) { RecordingLimits(waitingMillis = 630_000) }
        assertThat(RecordingLimits(30_000, 600_000, 30_000).waitingMillis).isEqualTo(600_000)
        val context = ContextRetention(1000, RecordingLimits())
        assertThrows(IllegalArgumentException::class.java) { context.current(999) }
    }

    @Test fun unsupportedAndroidIsGated() {
        for (sdk in 24..28) assertThat(PlaybackRecordingAvailability.supported(sdk)).isFalse()
        for (sdk in 29..37) assertThat(PlaybackRecordingAvailability.supported(sdk)).isTrue()
    }
}
