package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScanAudioTimelineTest {
    private val clock = PcmClock(1000, 0)

    @Test fun loadingDoesNotSpendEditorAllowanceAndReadyIncludesSoundThroughOpening() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val scan = timeline.acquireScan(300_000, 0)!!
        val loading = timeline.prepareEditor(scan, 400_000, 400_000)!!
        assertThat(loading.source).isEqualTo(AudioRange(0, 400_000))
        assertThat(loading.retention.firstEditorMillis).isNull()
        assertThat(loading.retention.cutoffMillis).isEqualTo(600_000)
        val ready = timeline.openEditor(scan, 450_000, 450_000)!!
        assertThat(ready.source).isEqualTo(AudioRange(0, 450_000))
        assertThat(ready.retention.firstEditorMillis).isEqualTo(450_000)
        assertThat(ready.retention.cutoffMillis).isEqualTo(570_000)
        val rotated = timeline.openEditor(scan, 470_000, 470_000)!!
        assertThat(rotated.source).isEqualTo(ready.source)
        assertThat(rotated.retention.cutoffMillis).isEqualTo(570_000)
    }

    @Test fun waitingExpiryDuringLoadingCannotGrantEditorAllowance() {
        val timeline = ScanAudioTimeline(clock, RecordingLimits())
        val scan = timeline.acquireScan(300_000, 0)!!
        timeline.prepareEditor(scan, 590_000, 590_000)
        val late = timeline.openEditor(scan, 650_000, 650_000)!!
        assertThat(late.source).isEqualTo(AudioRange(0, 600_000))
        assertThat(late.retention.firstEditorMillis).isNull()
        assertThat(late.retention.reason).isEqualTo(RetentionEnd.WAITING_LIMIT)
    }

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

    @Test fun suspendGapCapsPriorContextAdvancesFloorAndRecalibratesClock() {
        val timeline = ScanAudioTimeline(PcmClock(1000, 0), RecordingLimits())
        val firstScan = timeline.acquireScan(300_000, 0, committedEnd = 300_000)!!
        val opened = timeline.openEditor(firstScan, 305_000, 305_000)!!
        assertThat(opened.source).isEqualTo(AudioRange(0, 305_000))

        // Simulate 95s device sleep before resuming capture at frame 305_500.
        timeline.onSuspendGap(gapFrame = 305_000, endFrame = 305_500, endNanos = 400_000_000_000L, nowMillis = 400_000)

        val capped = timeline.refresh(firstScan, 320_000, 401_000)!!
        assertThat(capped.source).isEqualTo(AudioRange(0, 305_000))
        assertThat(capped.retention.reason).isEqualTo(RetentionEnd.CAPTURE_STOPPED)
        assertThat(timeline.retainedRanges(320_000, 401_000))
            .containsExactly(AudioRange(305_000, 320_000), AudioRange(0, 305_000))

        val secondScan = timeline.acquireScan(410_000, availableStartFrame = 0, committedEnd = 315_500)!!
        timeline.releaseContext(firstScan)
        val afterSuspend = timeline.openEditor(secondScan, 315_500, 410_000)!!
        assertThat(afterSuspend.source?.start).isAtLeast(305_000)
        assertThat(timeline.retainedRanges(315_500, 410_000).all { it.start >= 305_000 }).isTrue()
        timeline.releaseContext(secondScan)
        assertThat(timeline.retainedRanges(315_500, 410_000)).containsExactly(AudioRange(305_000, 315_500))
    }

    @Test fun acquireScanDoesNotThrowWhenClockSkewPrecedesOrJumpsPastCommittedFrames() {
        val timeline = ScanAudioTimeline(PcmClock(1000, 10_000_000_000L), RecordingLimits())
        val ahead = timeline.acquireScan(nowMillis = 500_000, availableStartFrame = 100, committedEnd = 50)
        assertThat(ahead).isNotNull()

        val behind = timeline.acquireScan(nowMillis = 5_000, availableStartFrame = 5_000, committedEnd = 10_000)
        assertThat(behind).isNotNull()
        val view = timeline.openEditor(behind!!, 10_000, 20_000)!!
        assertThat(view.source).isEqualTo(AudioRange(5_000, 10_000))
    }

    @Test fun suspendGapPreservesEarlierExpiredWaitingCutoffFrame() {
        val timeline = ScanAudioTimeline(PcmClock(1000, 0), RecordingLimits())
        val scan = timeline.acquireScan(nowMillis = 0, availableStartFrame = 0, committedEnd = 5_000)!!

        // Waiting limit expired at t=300_000 (frame 300_000) before suspend gap at frame 350_000 -> t=500_000.
        timeline.onSuspendGap(gapFrame = 350_000, endFrame = 350_500, endNanos = 500_000_000_000L, nowMillis = 500_000)

        val view = timeline.openEditor(scan, committedEnd = 355_000, nowMillis = 501_000)!!
        assertThat(view.source).isEqualTo(AudioRange(0, 300_000))
        assertThat(view.retention.reason).isEqualTo(RetentionEnd.WAITING_LIMIT)
        assertThat(timeline.retainedRanges(355_000, 501_000))
            .containsExactly(AudioRange(350_000, 355_000), AudioRange(0, 300_000))
    }
}
