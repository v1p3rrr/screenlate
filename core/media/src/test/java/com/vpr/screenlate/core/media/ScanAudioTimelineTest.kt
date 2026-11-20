package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScanAudioTimelineTest {
    private val clock = PcmClock(1000, 0)

    @Test fun protectsHistoryBeforeLongReadingAndDoesNotSlideShownRange() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val scan = timeline.acquireScan(300_000, 0)!!
        val first = timeline.openEditor(scan, 570_000, 570_000)!!
        assertThat(first.source).isEqualTo(AudioRange(0, 570_000))
        assertThat(first.scanFrame).isEqualTo(300_000)
        assertThat(first.retention.cutoffMillis).isEqualTo(690_000)
        val later = timeline.view(scan, 580_000, 580_000)!!
        assertThat(later.source).isEqualTo(first.source)
        assertThat(later.revision).isEqualTo(first.revision)
        assertThat(later.newFrames).isEqualTo(10_000)
        val refreshed = timeline.refresh(scan, 580_000, 580_000)!!
        assertThat(refreshed.source).isEqualTo(AudioRange(0, 580_000))
        assertThat(refreshed.revision).isEqualTo(first.revision + 1)
    }

    @Test fun capRefreshPublishesWholeTailOnceAndNeverUsesFreshRingToBridge() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val scan = timeline.acquireScan(300_000, 0)!!
        timeline.openEditor(scan, 310_000, 310_000)
        val capped = timeline.refresh(scan, 1_000_000, 1_000_000)!!
        assertThat(capped.source).isEqualTo(AudioRange(0, 430_000))
        assertThat(capped.retention.reason).isEqualTo(RetentionEnd.EDITOR_LIMIT)
        val repeated = timeline.refresh(scan, 1_100_000, 1_100_000)!!
        assertThat(repeated.source).isEqualTo(capped.source)
        assertThat(repeated.revision).isEqualTo(capped.revision)
        assertThat(timeline.retainedRanges(1_100_000, 1_100_000))
            .containsExactly(AudioRange(800_000, 1_100_000), AudioRange(0, 430_000))
    }

    @Test fun firstOpenAfterWaitingCapShowsSavedPrefixWithoutNewAllowance() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val scan = timeline.acquireScan(300_000, 0)!!
        val late = timeline.openEditor(scan, 900_000, 900_000)!!
        assertThat(late.source).isEqualTo(AudioRange(0, 600_000))
        assertThat(late.retention.firstEditorMillis).isNull()
        assertThat(late.retention.reason).isEqualTo(RetentionEnd.WAITING_LIMIT)
    }

    @Test fun limitsAdmittedAtScanRemainFrozenAndReopeningDoesNotRefreshOrRenew() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val scan = timeline.acquireScan(300_000, 0)!!
        timeline.updateLimits(RecordingLimits(30_000, 30_000, 600_000))
        val first = timeline.openEditor(scan, 400_000, 400_000)!!
        val reopened = timeline.openEditor(scan, 450_000, 450_000)!!
        assertThat(reopened.source).isEqualTo(first.source)
        assertThat(reopened.retention.cutoffMillis).isEqualTo(520_000)
        assertThat(reopened.newFrames).isEqualTo(50_000)
        assertThat(timeline.retainedRanges(450_000, 450_000))
            .containsExactly(AudioRange(420_000, 450_000), AudioRange(0, 520_000))
    }

    @Test fun stopKeepsOldAudioButRejectsNewScansAndSessionIdsDoNotCollide() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val scan = timeline.acquireScan(300_000, 0)!!
        timeline.stop(350_000)
        val stopped = timeline.openEditor(scan, 350_000, 400_000)!!
        assertThat(stopped.source).isEqualTo(AudioRange(0, 350_000))
        assertThat(stopped.retention.reason).isEqualTo(RetentionEnd.CAPTURE_STOPPED)
        assertThat(timeline.acquireScan(400_000, 0)).isNull()
        val restarted = ScanAudioTimeline(clock, RecordingLimits())
        assertThat(restarted.acquireScan(400_000, 100_000)).isNotEqualTo(scan)
        assertThat(restarted.refresh(scan, 400_000, 400_000)).isNull()
    }

    @Test fun stopWithEarlierTimestampClampsToLastSeenMillis() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val scan = timeline.acquireScan(300_000, 0)!!
        timeline.retainedRanges(350_000, 360_000)
        timeline.stop(340_000)
        val stopped = timeline.openEditor(scan, 360_000, 370_000)!!
        assertThat(stopped.retention.reason).isEqualTo(RetentionEnd.CAPTURE_STOPPED)
        assertThat(stopped.source?.endExclusive).isEqualTo(360_000)
    }

    @Test fun replacingOrReleasingScanRejectsStaleEditorCallbacksWithoutResettingRing() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val old = timeline.acquireScan(300_000, 0)!!
        val current = timeline.acquireScan(400_000, 100_000)!!
        assertThat(timeline.openEditor(old, 400_000, 400_000)).isNull()
        timeline.releaseContext(old)
        assertThat(timeline.openEditor(current, 400_000, 400_000)).isNotNull()
        timeline.releaseContext(current)
        assertThat(timeline.refresh(current, 500_000, 500_000)).isNull()
        assertThat(timeline.retainedRanges(500_000, 500_000)).containsExactly(AudioRange(200_000, 500_000))
    }

    @Test fun queuedPreCutoffFramesCanPublishLateButPostCutoffFramesStayOut() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val scan = timeline.acquireScan(300_000, 0)!!
        val first = timeline.openEditor(scan, 300_000, 310_000)!!
        assertThat(first.source).isEqualTo(AudioRange(0, 300_000))
        val late = timeline.refresh(scan, 429_999, 500_000)!!
        assertThat(late.source).isEqualTo(AudioRange(0, 429_999))
        val last = timeline.refresh(scan, 600_000, 600_000)!!
        assertThat(last.source).isEqualTo(AudioRange(0, 430_000))
        assertThat(last.newFrames).isEqualTo(0)
    }

    @Test fun capacityFailureAndEmptyCommittedSourceDoNotInventSoundOrReviveE() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val scan = timeline.acquireScan(10_000, 0)!!
        val empty = timeline.openEditor(scan, 0, 10_000, capacityAvailable = false)!!
        assertThat(empty.source).isNull()
        assertThat(empty.retention.reason).isEqualTo(RetentionEnd.RESOURCE_LIMIT)
        val prefix = timeline.refresh(scan, 30_000, 30_000)!!
        assertThat(prefix.source).isEqualTo(AudioRange(0, 10_000))
        assertThat(timeline.openEditor(scan, 40_000, 40_000)!!.retention.cutoffMillis).isEqualTo(10_000)
    }
}
