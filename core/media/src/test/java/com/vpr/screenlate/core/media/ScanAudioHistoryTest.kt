package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import java.io.EOFException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScanAudioHistoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun longReadingRefreshAndLaterRingRetainOldSamplesWithoutKeepingTheGap() {
        val history = PcmHistory(temporary.newFolder(), 1000, 3_000_000, 1200)
        val timeline = ScanAudioTimeline(PcmClock(1000, 0), RecordingLimits())
        try {
            appendUntil(history, timeline, 300)
            val scan = timeline.acquireScan(300_000, 0)!!
            appendUntil(history, timeline, 570)
            val opened = timeline.openEditor(scan, history.committedEnd, 570_000)!!
            history.snapshot(opened.source!!).use { original ->
                appendUntil(history, timeline, 1100)
                val refresh = timeline.refresh(scan, history.committedEnd, 1_100_000)!!
                assertThat(refresh.source).isEqualTo(AudioRange(0, 690_000))
                assertThat(refresh.retention.reason).isEqualTo(RetentionEnd.EDITOR_LIMIT)
                assertThat(original.openStream().use { it.readBytes() }).isEqualTo(pcm(0, 570_000))
                history.snapshot(refresh.source!!).use { updated ->
                    assertThat(updated.openStream().use { it.readBytes() }).isEqualTo(pcm(0, 690_000))
                }
                assertThrows(EOFException::class.java) { history.snapshot(AudioRange(0, 1_100_000)) }
                assertThat(history.bytes).isEqualTo((690_000L + 300_000) * 2)
            }
            timeline.releaseContext(scan)
            history.retain(timeline.retainedRanges(history.committedEnd, 1_100_000))
            assertThat(history.bytes).isEqualTo(300_000L * 2)
        } finally {
            history.close()
        }
        assertThat(history.files).isEqualTo(0)
    }

    @Test fun freshScanKeepsRecentRingAfterOldScanReleases() {
        val history = PcmHistory(temporary.newFolder(), 1000, 3_000_000, 1200)
        val timeline = ScanAudioTimeline(PcmClock(1000, 0), RecordingLimits())
        try {
            appendUntil(history, timeline, 300)
            val old = timeline.acquireScan(300_000, 0)!!
            appendUntil(history, timeline, 900)
            timeline.releaseContext(old)
            history.retain(timeline.retainedRanges(history.committedEnd, 900_000))
            val fresh = timeline.acquireScan(900_000, 600_000)!!
            val opened = timeline.openEditor(fresh, history.committedEnd, 900_000)!!
            assertThat(opened.source).isEqualTo(AudioRange(600_000, 900_000))
            history.snapshot(opened.source!!).use { snapshot ->
                assertThat(snapshot.openStream().use { it.readBytes() }).isEqualTo(pcm(600_000, 900_000))
            }
            assertThat(timeline.refresh(old, history.committedEnd, 900_000)).isNull()
        } finally {
            history.close()
        }
    }

    private fun appendUntil(history: PcmHistory, timeline: ScanAudioTimeline, seconds: Int) {
        for (second in (history.committedEnd / 1000).toInt() until seconds) {
            val end = (second + 1) * 1000L
            history.retain(timeline.retainedRanges(end, end))
            history.append(pcm(second * 1000, (second + 1) * 1000))
        }
    }

    private fun pcm(start: Int, end: Int) = ByteArray((end - start) * 2).also { output ->
        for (frame in start until end) {
            output[(frame - start) * 2] = frame.toByte()
            output[(frame - start) * 2 + 1] = (frame shr 8).toByte()
        }
    }
}
