package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PcmHistoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun snapshotIncludesFixedPartialPrefixAndRefreshAddsNoDuplicateFrames() {
        val history = history()
        history.retain(listOf(AudioRange(0, 100)))
        history.append(pcm(0, 6))
        val old = history.snapshot(AudioRange(0, 6))
        history.append(pcm(6, 14))
        val refreshed = history.snapshot(AudioRange(0, 14))
        assertThat(read(old)).isEqualTo(pcm(0, 6))
        assertThat(read(refreshed)).isEqualTo(pcm(0, 14))
        old.close()
        refreshed.close()
        history.close()
        assertThat(history.files).isEqualTo(0)
    }

    @Test fun ringPruningCannotDeletePinnedFilesAndReaderOutlivesSnapshotAndStore() {
        val history = history()
        history.retain(listOf(AudioRange(0, 16)))
        history.append(pcm(0, 16))
        val snapshot = history.snapshot(AudioRange(2, 10))
        val reader = snapshot.openStream()
        snapshot.close()
        history.retain(listOf(AudioRange(12, 16)))
        history.close()
        assertThat(history.files).isEqualTo(3)
        assertThat(reader.use { it.readBytes() }).isEqualTo(pcm(2, 10))
        assertThat(history.files).isEqualTo(0)
        assertThat(history.bytes).isEqualTo(0)
    }

    @Test fun disjointRingAndProtectedSourceDoNotKeepUnboundedMiddle() {
        val history = history()
        history.retain(listOf(AudioRange(0, 24)))
        history.append(pcm(0, 24))
        history.retain(listOf(AudioRange(0, 4), AudioRange(20, 24)))
        assertThat(history.files).isEqualTo(2)
        assertThat(history.bytes).isEqualTo(16)
        assertThrows(EOFException::class.java) { history.snapshot(AudioRange(0, 24)) }
        history.snapshot(AudioRange(0, 4)).use { assertThat(read(it)).isEqualTo(pcm(0, 4)) }
        history.snapshot(AudioRange(20, 24)).use { assertThat(read(it)).isEqualTo(pcm(20, 24)) }
        history.close()
    }

    @Test fun byteQuotaDoesNotPublishAnOverBudgetWrite() {
        val history = history(maxBytes = 16)
        history.retain(listOf(AudioRange(0, 100)))
        history.append(pcm(0, 8))
        assertThrows(HistoryQuotaException::class.java) { history.append(pcm(8, 10)) }
        assertThat(history.committedEnd).isEqualTo(8)
        assertThat(history.bytes).isEqualTo(16)
        history.snapshot(AudioRange(0, 8)).use { assertThat(read(it)).isEqualTo(pcm(0, 8)) }
        history.close()
    }

    @Test fun fileQuotaAndUnalignedWritesAreRejected() {
        val history = history(maxFiles = 1)
        history.retain(listOf(AudioRange(0, 100)))
        history.append(pcm(0, 4))
        assertThrows(HistoryQuotaException::class.java) { history.append(pcm(4, 8)) }
        assertThrows(IllegalArgumentException::class.java) { history.append(byteArrayOf(1)) }
        assertThat(history.committedEnd).isEqualTo(4)
        history.close()
    }

    @Test fun corruptPinnedChunkFailsRatherThanJoiningAcrossMissingSamples() {
        val directory = temporary.newFolder()
        val history = PcmHistory(directory, 4, 1024, 32)
        history.retain(listOf(AudioRange(0, 8)))
        history.append(pcm(0, 8))
        val snapshot = history.snapshot(AudioRange(0, 8))
        val first = directory.listFiles()!!.first { RandomAccessFile(it, "r").use { file -> file.read() == 0 } }
        RandomAccessFile(first, "rw").use { it.setLength(2) }
        assertThrows(EOFException::class.java) { snapshot.openStream().use { it.readBytes() } }
        snapshot.close()
        history.close()
    }

    @Test fun rolloverAndPartialTailKeepBoundedPhysicalSlack() {
        val history = history(maxBytes = 100, maxFiles = 4)
        for (frame in 0 until 200) {
            history.retain(listOf(AudioRange(maxOf(0, frame - 5).toLong(), (frame + 1).toLong())))
            history.append(pcm(frame, frame + 1))
            assertThat(history.files).isAtMost(3)
            assertThat(history.bytes).isAtMost(24)
        }
        assertThat(history.committedEnd).isEqualTo(200)
        history.close()
    }

    private fun history(maxBytes: Long = 1024, maxFiles: Int = 32) =
        PcmHistory(temporary.newFolder(), chunkFrames = 4, maxBytes = maxBytes, maxFiles = maxFiles)

    @Test fun partialFailedWriteRollsBackWithoutPublishingOrAcceptingLaterSamples() {
        var writes = 0
        val writer = PcmWriter { file, position, bytes, offset, length ->
            RandomAccessFile(file, "rw").use { output ->
                output.seek(position)
                if (writes++ == 0) output.write(bytes, offset, length) else {
                    output.write(bytes, offset, 2)
                    throw IOException("Injected partial write")
                }
            }
        }
        val history = PcmHistory(temporary.newFolder(), 8, 1024, 32, writer)
        history.retain(listOf(AudioRange(0, 100)))
        history.append(pcm(0, 3))
        assertThrows(IOException::class.java) { history.append(pcm(3, 7)) }
        assertThat(history.committedEnd).isEqualTo(3)
        assertThat(history.bytes).isEqualTo(6)
        history.snapshot(AudioRange(0, 3)).use { assertThat(read(it)).isEqualTo(pcm(0, 3)) }
        assertThrows(IllegalStateException::class.java) { history.append(pcm(7, 8)) }
        history.close()
    }

    @Test fun rejectsUnaccountedFilesFromAnAbandonedSession() {
        val directory = temporary.newFolder()
        java.io.File(directory, "abandoned.pcm").writeBytes(pcm(0, 4))
        assertThrows(IllegalStateException::class.java) { PcmHistory(directory, 4, 1024, 32) }
    }

    private fun read(snapshot: PcmHistory.Snapshot): ByteArray = snapshot.openStream().use { it.readBytes() }

    private fun pcm(start: Int, end: Int): ByteArray = ByteArray((end - start) * 2).also { output ->
        for (frame in start until end) {
            output[(frame - start) * 2] = frame.toByte()
            output[(frame - start) * 2 + 1] = (frame shr 8).toByte()
        }
    }
}
