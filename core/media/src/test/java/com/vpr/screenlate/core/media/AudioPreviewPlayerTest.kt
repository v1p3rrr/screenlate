package com.vpr.screenlate.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AudioPreviewPlayerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeAudioSink : AudioSink {
        val output = ByteArrayOutputStream()
        var playing = false
        var paused = false
        var stopped = false
        var closed = false

        override fun write(buffer: ByteArray, offset: Int, size: Int): Int {
            output.write(buffer, offset, size)
            return size
        }

        override fun play() { playing = true; paused = false }
        override fun pause() { paused = true; playing = false }
        override fun stop() { stopped = true; playing = false }
        override fun flush() {}
        override fun close() { closed = true }
    }

    @Test
    fun `play in Result mode skips exclusions`() {
        val history = PcmHistory(tempFolder.newFolder(), chunkFrames = 1000, maxBytes = 1_000_000, maxFiles = 100)
        // Write 1000 frames (2000 bytes)
        val data = ByteArray(2000) { (it % 128).toByte() }
        history.append(data)
        history.retain(listOf(AudioRange(0, 1000)))

        val sink = FakeAudioSink()
        val player = AudioPreviewPlayer(
            snapshotProvider = { range -> history.snapshot(range) },
            sampleRate = 48000,
            sinkFactory = { sink },
        )

        val edit = AudioEdit(
            selection = AudioRange(100, 500), // 400 frames
            exclusions = listOf(AudioRange(200, 300)), // 100 frames excluded
        )
        // kept: [100, 200] (100 frames = 200 bytes) and [300, 500] (200 frames = 400 bytes). Total = 300 frames (600 bytes)

        val latch = CountDownLatch(1)
        player.play(edit, mode = PreviewMode.RESULT, onComplete = { latch.countDown() })

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals(600, sink.output.size())
        assertEquals(500L, player.currentFrame)
        assertFalse(player.isPlaying)

        history.close()
    }

    @Test
    fun `play in Original mode plays full continuous selection`() {
        val history = PcmHistory(tempFolder.newFolder(), chunkFrames = 1000, maxBytes = 1_000_000, maxFiles = 100)
        val data = ByteArray(2000) { (it % 128).toByte() }
        history.append(data)

        val sink = FakeAudioSink()
        val player = AudioPreviewPlayer(
            snapshotProvider = { range -> history.snapshot(range) },
            sampleRate = 48000,
            sinkFactory = { sink },
        )

        val edit = AudioEdit(
            selection = AudioRange(100, 500), // 400 frames = 800 bytes
            exclusions = listOf(AudioRange(200, 300)),
        )

        val latch = CountDownLatch(1)
        player.play(edit, mode = PreviewMode.ORIGINAL, onComplete = { latch.countDown() })

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals(800, sink.output.size())
        assertEquals(500L, player.currentFrame)

        history.close()
    }

    @Test
    fun `seek into exclusion in Result mode advances to next kept frame`() {
        val history = PcmHistory(tempFolder.newFolder(), chunkFrames = 1000, maxBytes = 1_000_000, maxFiles = 100)
        val player = AudioPreviewPlayer(
            snapshotProvider = { range -> history.snapshot(range) },
            sampleRate = 48000,
            sinkFactory = { FakeAudioSink() },
        )

        val edit = AudioEdit(
            selection = AudioRange(100, 500),
            exclusions = listOf(AudioRange(200, 300)),
        )

        player.seek(250, edit = edit, mode = PreviewMode.RESULT)
        assertEquals(300L, player.currentFrame)

        player.seek(250, edit = edit, mode = PreviewMode.ORIGINAL)
        assertEquals(250L, player.currentFrame)

        history.close()
    }

    @Test
    fun `pause stops playback`() {
        val history = PcmHistory(tempFolder.newFolder(), chunkFrames = 1000, maxBytes = 1_000_000, maxFiles = 100)
        history.append(ByteArray(2000))

        val sink = FakeAudioSink()
        val player = AudioPreviewPlayer(
            snapshotProvider = { range -> history.snapshot(range) },
            sampleRate = 48000,
            sinkFactory = { sink },
        )

        val edit = AudioEdit(selection = AudioRange(0, 1000))
        player.play(edit)
        player.pause()

        assertFalse(player.isPlaying)
        assertTrue(sink.stopped)

        history.close()
    }
}
