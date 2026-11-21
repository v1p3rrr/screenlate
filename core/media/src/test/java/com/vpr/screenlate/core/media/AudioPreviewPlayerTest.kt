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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class AudioPreviewPlayerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private open class FakeAudioSink : AudioSink {
        val output = ByteArrayOutputStream()
        override val playedFrames: Long get() = output.size() / 2L
        override fun awaitProgress(timeoutMillis: Long) { CountDownLatch(1).await(timeoutMillis, TimeUnit.MILLISECONDS) }
        override fun finishInput() = true
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
        assertTrue("Natural completion must release the output", sink.closed)

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

    @Test
    fun `seek while playing restarts playback from target frame`() {
        val history = PcmHistory(tempFolder.newFolder(), chunkFrames = 1000, maxBytes = 1_000_000, maxFiles = 100)
        val data = ByteArray(4000) { ((it / 2) % 251).toByte() }
        history.retain(listOf(AudioRange(0, 2000)))
        history.append(data)

        val writing = CountDownLatch(1)
        val allowWrite = CountDownLatch(1)
        val first = AtomicBoolean(true)
        val oldSink = object : FakeAudioSink() {
            override fun write(buffer: ByteArray, offset: Int, size: Int): Int {
                writing.countDown()
                check(allowWrite.await(3, TimeUnit.SECONDS))
                return -1
            }
            override fun pause() { super.pause(); allowWrite.countDown() }
        }
        val sink = FakeAudioSink()
        val player = AudioPreviewPlayer(
            snapshotProvider = { range -> history.snapshot(range) },
            sampleRate = 48000,
            sinkFactory = { if (first.compareAndSet(true, false)) oldSink else sink },
        )

        val edit = AudioEdit(selection = AudioRange(0, 2000))
        val completeLatch = CountDownLatch(1)
        try {
            player.play(edit, onComplete = { completeLatch.countDown() })
            assertTrue(writing.await(3, TimeUnit.SECONDS))
            player.seek(1500, edit = edit)
            assertTrue(completeLatch.await(3, TimeUnit.SECONDS))
            assertEquals(2000L, player.currentFrame)
            assertTrue(sink.output.toByteArray().contentEquals(data.copyOfRange(3000, 4000)))
            assertTrue(oldSink.closed)
        } finally {
            allowWrite.countDown()
            player.close()
            history.close()
        }
    }

    @Test
    fun `cancelled snapshot cannot revive output and replacement waits for retirement`() {
        val history = PcmHistory(tempFolder.newFolder(), 1000, 1_000_000, 100)
        val data = ByteArray(2000) { it.toByte() }
        history.append(data)
        history.retain(listOf(AudioRange(0, 1000)))
        val reading = CountDownLatch(1)
        val allowRead = CountDownLatch(1)
        val oldReleased = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val firstRead = AtomicBoolean(true)
        val outputs = AtomicInteger()
        val failure = AtomicReference<PreviewFailure?>()
        val oldSink = object : FakeAudioSink() {
            override fun close() { super.close(); oldReleased.countDown() }
        }
        val newSink = FakeAudioSink()
        val player = AudioPreviewPlayer(
            snapshotProvider = { range ->
                if (firstRead.compareAndSet(true, false)) {
                    reading.countDown()
                    check(allowRead.await(3, TimeUnit.SECONDS))
                }
                history.snapshot(range)
            },
            sinkFactory = { if (outputs.incrementAndGet() == 1) oldSink else newSink },
            stopTimeoutMillis = 50,
        )
        try {
            player.play(AudioEdit(AudioRange(0, 100)))
            assertTrue(reading.await(3, TimeUnit.SECONDS))
            repeat(3) {
                player.play(AudioEdit(AudioRange(200, 300)), onError = { failure.set(it) })
                assertEquals(PreviewFailure.OUTPUT_BUSY, failure.get())
            }
            assertEquals(1, outputs.get())
            allowRead.countDown()
            assertTrue(oldReleased.await(3, TimeUnit.SECONDS))
            failure.set(null)
            player.play(AudioEdit(AudioRange(200, 300)), onComplete = { completed.countDown() },
                onError = { failure.set(it); completed.countDown() })
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(null, failure.get())
            assertEquals(0, oldSink.output.size())
            assertTrue(newSink.output.toByteArray().contentEquals(data.copyOfRange(400, 600)))
            assertEquals(300L, player.currentFrame)
        } finally {
            allowRead.countDown()
            player.close()
            history.close()
        }
    }

    @Test
    fun `completion waits for audible frames and cursor does not lead queued output`() {
        val history = PcmHistory(tempFolder.newFolder(), 1000, 1_000_000, 100)
        history.append(ByteArray(200))
        val written = CountDownLatch(1)
        val complete = CountDownLatch(1)
        val head = java.util.concurrent.atomic.AtomicLong()
        val progress = java.lang.Object()
        val sink = object : FakeAudioSink() {
            override val playedFrames: Long get() = head.get()
            override fun write(buffer: ByteArray, offset: Int, size: Int): Int {
                return super.write(buffer, offset, size).also { written.countDown() }
            }
            override fun awaitProgress(timeoutMillis: Long) { synchronized(progress) { progress.wait(timeoutMillis) } }
        }
        val player = AudioPreviewPlayer({ history.snapshot(it) }, sinkFactory = { sink })
        try {
            player.play(AudioEdit(AudioRange(0, 100)), onComplete = { complete.countDown() })
            assertTrue(written.await(3, TimeUnit.SECONDS))
            assertTrue(player.isPlaying)
            assertFalse(sink.closed)
            assertEquals(0L, player.currentFrame)
            head.set(100)
            synchronized(progress) { progress.notifyAll() }
            assertTrue(complete.await(3, TimeUnit.SECONDS))
            assertEquals(100L, player.currentFrame)
            assertTrue(sink.closed)
        } finally {
            player.close()
            history.close()
        }
    }

    @Test
    fun `short write failure does not advance over unwritten audio`() {
        val history = PcmHistory(tempFolder.newFolder(), 1000, 1_000_000, 100)
        history.append(ByteArray(200))
        val failed = CountDownLatch(1)
        val error = AtomicReference<PreviewFailure?>()
        val complete = AtomicBoolean(false)
        val sink = object : FakeAudioSink() {
            override fun write(buffer: ByteArray, offset: Int, size: Int): Int =
                if (output.size() == 0) super.write(buffer, offset, 2) else -1
        }
        val player = AudioPreviewPlayer({ history.snapshot(it) }, sinkFactory = { sink })
        try {
            player.play(AudioEdit(AudioRange(0, 100)), onComplete = { complete.set(true) },
                onError = { error.set(it); failed.countDown() })
            assertTrue(failed.await(3, TimeUnit.SECONDS))
            assertEquals(PreviewFailure.OUTPUT_FAILED, error.get())
            assertEquals(1L, player.currentFrame)
            assertFalse(complete.get())
            assertTrue(sink.closed)
        } finally { player.close(); history.close() }
    }

    @Test
    fun `queued callbacks from a stopped generation are ignored on delivery`() {
        val history = PcmHistory(tempFolder.newFolder(), 1000, 1_000_000, 100)
        history.append(ByteArray(200))
        val queued = CountDownLatch(2)
        val callbacks = LinkedBlockingQueue<Runnable>()
        val progress = AtomicInteger()
        val completed = AtomicBoolean(false)
        val player = AudioPreviewPlayer({ history.snapshot(it) }, sinkFactory = { FakeAudioSink() },
            callbackExecutor = Executor { callbacks.add(it); queued.countDown() })
        try {
            player.play(AudioEdit(AudioRange(0, 100)), onProgress = { progress.incrementAndGet() },
                onComplete = { completed.set(true) })
            assertTrue(queued.await(3, TimeUnit.SECONDS))
            player.stop()
            while (true) (callbacks.poll() ?: break).run()
            assertEquals(0, progress.get())
            assertFalse(completed.get())
        } finally { player.close(); history.close() }
    }

    @Test
    fun `busy callback dispatcher retains a bounded latest progress update`() {
        val history = PcmHistory(tempFolder.newFolder(), 1000, 1_000_000, 100)
        history.append(ByteArray(2000))
        val clock = java.util.concurrent.atomic.AtomicLong()
        val closed = CountDownLatch(1)
        val callbacks = LinkedBlockingQueue<Runnable>()
        val complete = AtomicBoolean(false)
        val lastFrame = java.util.concurrent.atomic.AtomicLong()
        val sink = object : FakeAudioSink() {
            override fun write(buffer: ByteArray, offset: Int, size: Int): Int {
                clock.addAndGet(50_000_000L)
                return super.write(buffer, offset, 2)
            }
            override fun close() { super.close(); closed.countDown() }
        }
        val player = AudioPreviewPlayer({ history.snapshot(it) }, sinkFactory = { sink },
            callbackExecutor = Executor { callbacks.add(it) }, nowNanos = { clock.get() })
        try {
            player.play(AudioEdit(AudioRange(0, 1000)), onProgress = { lastFrame.set(it) }, onComplete = { complete.set(true) })
            assertTrue(closed.await(3, TimeUnit.SECONDS))
            assertTrue("Progress must not fill an unavailable UI queue: ${callbacks.size}", callbacks.size <= 2)
            while (!complete.get()) checkNotNull(callbacks.poll(3, TimeUnit.SECONDS)).run()
            assertEquals(1000L, lastFrame.get())
        } finally { player.close(); history.close() }
    }

    @Test
    fun `output that accepts no frames fails without skipping the source`() {
        val history = PcmHistory(tempFolder.newFolder(), 1000, 1_000_000, 100)
        history.append(ByteArray(200))
        val clock = java.util.concurrent.atomic.AtomicLong()
        val done = CountDownLatch(1)
        val error = AtomicReference<PreviewFailure?>()
        val sink = object : FakeAudioSink() {
            override fun write(buffer: ByteArray, offset: Int, size: Int) = 0
            override fun awaitProgress(timeoutMillis: Long) { clock.addAndGet(timeoutMillis * 1_000_000L) }
        }
        val player = AudioPreviewPlayer({ history.snapshot(it) }, sinkFactory = { sink },
            nowNanos = { clock.get() }, stallTimeoutMillis = 50)
        try {
            player.play(AudioEdit(AudioRange(0, 100)), onError = { error.set(it); done.countDown() })
            assertTrue(done.await(3, TimeUnit.SECONDS))
            assertEquals(PreviewFailure.OUTPUT_STALLED, error.get())
            assertEquals(0L, player.currentFrame)
            assertTrue(sink.closed)
            assertFalse(player.isPlaying)
        } finally { player.close(); history.close() }
    }

    @Test
    fun `unavailable source reports failure and releases its output`() {
        val done = CountDownLatch(1)
        val error = AtomicReference<PreviewFailure?>()
        val sink = FakeAudioSink()
        val player = AudioPreviewPlayer({ null }, sinkFactory = { sink })
        try {
            player.play(AudioEdit(AudioRange(0, 100)), onError = { error.set(it); done.countDown() })
            assertTrue(done.await(3, TimeUnit.SECONDS))
            assertEquals(PreviewFailure.SOURCE_UNAVAILABLE, error.get())
            assertTrue(sink.closed)
            assertFalse(player.isPlaying)
        } finally { player.close() }
    }
}
