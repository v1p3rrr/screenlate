package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class PcmAudioEncoderTest {
    @Test
    fun truncatedInputClosesTheEncoderWithoutQueuingAHalfSample() {
        var writes = 0
        var closes = 0
        val encoder = object : PcmEncoder {
            override fun write(bytes: ByteArray, offset: Int, length: Int, presentationTimeUs: Long, endOfInput: Boolean): Int {
                writes++
                return length
            }
            override fun drain() = PcmEncoder.Drain.FINISHED
            override fun close() { closes++ }
        }
        assertThrows(IOException::class.java) {
            PcmAudioEncoder.encode(ByteArrayInputStream(byteArrayOf(1)), 1, 1000, { encoder }, { 0 })
        }
        assertThat(writes).isEqualTo(0)
        assertThat(closes).isEqualTo(1)
    }

    @Test
    fun overallDeadlineStillBoundsAnEncoderThatMakesSlowContinuousProgress() {
        var time = 0L
        var closes = 0
        val encoder = object : PcmEncoder {
            override fun write(bytes: ByteArray, offset: Int, length: Int, presentationTimeUs: Long, endOfInput: Boolean): Int {
                time += 1000
                return 2
            }
            override fun drain() = PcmEncoder.Drain.NONE
            override fun close() { closes++ }
        }
        val failure = assertThrows(IOException::class.java) {
            PcmAudioEncoder.encode(ByteArrayInputStream(ByteArray(400)), 200, 1000, { encoder }, { time })
        }
        assertThat(failure.message).contains("deadline")
        assertThat(time).isLessThan(40_000)
        assertThat(closes).isEqualTo(1)
    }
    @Test
    fun lateProgressCannotRenewAnAlreadyExpiredStallDeadline() {
        var time = 0L
        var closes = 0
        val encoder = object : PcmEncoder {
            override fun write(bytes: ByteArray, offset: Int, length: Int, presentationTimeUs: Long, endOfInput: Boolean): Int? = null
            override fun drain(): PcmEncoder.Drain { time += 11_000; return PcmEncoder.Drain.PROGRESS }
            override fun close() { closes++ }
        }
        val failure = assertThrows(IOException::class.java) {
            PcmAudioEncoder.encode(ByteArrayInputStream(ByteArray(2000)), 1000, 1000, { encoder }, { time })
        }
        assertThat(failure.message).contains("no progress")
        assertThat(time).isEqualTo(11_000)
        assertThat(closes).isEqualTo(1)
    }

    @Test
    fun cancellationAfterInputStopsBeforeAnotherNativeCallAndClosesTheEncoder() {
        var cancelled = false
        var drains = 0
        var closes = 0
        val encoder = object : PcmEncoder {
            override fun write(bytes: ByteArray, offset: Int, length: Int, presentationTimeUs: Long, endOfInput: Boolean): Int {
                cancelled = true
                return length
            }
            override fun drain(): PcmEncoder.Drain { drains++; return PcmEncoder.Drain.FINISHED }
            override fun close() { closes++ }
        }
        assertThrows(CancellationException::class.java) {
            PcmAudioEncoder.encode(ByteArrayInputStream(ByteArray(2)), 1, 1000, { encoder }, { 0 }) {
                if (cancelled) throw CancellationException("Test cancellation")
            }
        }
        assertThat(drains).isEqualTo(0)
        assertThat(closes).isEqualTo(1)
    }
    @Test
    fun shortInputAndSmallCodecBuffersPreserveSamplesAndContinuousTimestamps() {
        val bytes = byteArrayOf(0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0)
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int) = super.read(buffer, offset, minOf(1, length))
        }
        val output = ByteArrayOutputStream()
        val timestamps = mutableListOf<Long>()
        var ended = false
        var closes = 0
        val sink = object : PcmEncoder {
            override fun write(bytes: ByteArray, offset: Int, length: Int, presentationTimeUs: Long, endOfInput: Boolean): Int {
                val accepted = minOf(4, length)
                output.write(bytes, offset, accepted)
                timestamps += presentationTimeUs
                ended = endOfInput && accepted == length
                return accepted
            }
            override fun drain() = if (ended) PcmEncoder.Drain.FINISHED else PcmEncoder.Drain.NONE
            override fun close() { closes++ }
        }
        PcmAudioEncoder.encode(input, frames = 6, sampleRate = 1000, factory = { sink }, nowMillis = { 0 })
        assertThat(output.toByteArray()).isEqualTo(bytes)
        assertThat(timestamps).containsExactly(0L, 2000L, 4000L).inOrder()
        assertThat(ended).isTrue()
        assertThat(closes).isEqualTo(1)
    }

    @Test
    fun progressingLongExportMayTakeMoreThanThirtySeconds() {
        var time = 0L
        var ended = false
        var frames = 0L
        val sink = object : PcmEncoder {
            override fun write(bytes: ByteArray, offset: Int, length: Int, presentationTimeUs: Long, endOfInput: Boolean): Int {
                time += 50
                frames += length / 2
                ended = endOfInput
                return length
            }
            override fun drain() = if (ended) PcmEncoder.Drain.FINISHED else PcmEncoder.Drain.NONE
            override fun close() = Unit
        }
        val quiet = PcmAudioEncoder.encode(object : InputStream() {
            override fun read() = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int { buffer.fill(0, offset, offset + length); return length }
        }, frames = 2_880_000, sampleRate = 48000, factory = { sink }, nowMillis = { time })
        assertThat(time).isGreaterThan(30_000)
        assertThat(frames).isEqualTo(2_880_000)
        assertThat(quiet).isTrue()
    }
}
