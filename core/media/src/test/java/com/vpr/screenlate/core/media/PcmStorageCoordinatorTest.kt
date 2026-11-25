package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.FutureTask

class PcmStorageCoordinatorTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun startCaptureWritesFramesAndAllowsScanEditorLifecycle() {
        val dir = temporary.newFolder()
        val limits = RecordingLimits(historyMillis = 60_000, waitingMillis = 60_000, editorMillis = 60_000)
        val config = PcmCoordinatorConfig(
            sampleRate = 1000,
            chunkFrames = 100,
            blockFrames = 50,
            queueCapacity = 20,
            maxStorageBytes = 1_000_000,
            maxStorageFiles = 100,
            minFreeSpaceBytes = 1024,
        )

        val time = AtomicLong(10_000L)
        val source = FakeAudioSource(sampleRate = 1000, blockFrames = 50)
        val coordinator = PcmStorageCoordinator(
            directory = dir,
            limits = limits,
            config = config,
            nowMillis = { time.get() },
            nowNanos = { time.get() * 1_000_000L },
        )

        try {
            val started = coordinator.start(source)
            assertThat(started).isTrue()
            assertThat(coordinator.currentState).isEqualTo(PcmCoordinatorState.RUNNING)
            assertThat(coordinator.queueBufferBytes).isEqualTo(config.maxQueueBytes.toLong())

            // Let source emit some blocks (at least 200 frames = 4 blocks)
            source.emitFrames(200)
            waitForCondition { coordinator.committedEndFrame >= 200 }

            time.set(10_200L)
            val scanId = coordinator.acquireScan(10_200L)
            assertThat(scanId).isNotNull()

            // Emit more frames
            source.emitFrames(150)
            waitForCondition { coordinator.committedEndFrame >= 350 }

            time.set(10_350L)
            val view = checkNotNull(coordinator.openEditor(scanId!!, 10_350L))
            val initialSource = checkNotNull(view.source)
            assertThat(initialSource.start).isEqualTo(0L) // Scan was at 200, history is 60s
            assertThat(initialSource.endExclusive).isAtLeast(200L)

            // Emit more frames and refresh
            source.emitFrames(100)
            waitForCondition { coordinator.committedEndFrame >= 450 }

            time.set(10_450L)
            val refreshed = checkNotNull(coordinator.refresh(scanId, 10_450L))
            val refreshedSource = checkNotNull(refreshed.source)
            assertThat(refreshedSource.endExclusive).isGreaterThan(initialSource.endExclusive)

            // Verify snapshot reads real bytes matching source
            coordinator.snapshot(AudioRange(0, 100)).use { snapshot ->
                val bytes = snapshot.openStream().use { it.readBytes() }
                assertThat(bytes.size).isEqualTo(200)
                assertThat(bytes).isEqualTo(source.expectedPcm(0, 100))
            }

            // Stopping capture preserves the current context
            coordinator.stop(10_500L)
            assertThat(coordinator.currentState).isEqualTo(PcmCoordinatorState.STOPPED)
            assertThat(coordinator.queueBufferBytes).isEqualTo(0)

            // We can still read the snapshot after stop
            coordinator.snapshot(AudioRange(0, 100)).use { snapshot ->
                val bytes = snapshot.openStream().use { it.readBytes() }
                assertThat(bytes.size).isEqualTo(200)
            }

            coordinator.releaseContext(scanId)
        } finally {
            coordinator.close()
        }
        assertThat(coordinator.currentState).isEqualTo(PcmCoordinatorState.CLOSED)
    }

    @Test
    fun stopCoordinatesWithWriterDrainWhenWriterAdvancesTime() {
        val dir = temporary.newFolder()
        val limits = RecordingLimits(historyMillis = 60_000, waitingMillis = 60_000, editorMillis = 60_000)
        val config = PcmCoordinatorConfig(
            sampleRate = 1000,
            chunkFrames = 100,
            blockFrames = 50,
            queueCapacity = 20,
            maxStorageBytes = 1_000_000,
            maxStorageFiles = 100,
            minFreeSpaceBytes = 1024,
        )

        val time = AtomicLong(10_000L)
        val source = FakeAudioSource(sampleRate = 1000, blockFrames = 50)
        val coordinator = PcmStorageCoordinator(
            directory = dir,
            limits = limits,
            config = config,
            nowMillis = { time.get() },
            nowNanos = { time.get() * 1_000_000L },
        )

        try {
            coordinator.start(source)
            time.set(10_100L)
            val scanId = checkNotNull(coordinator.acquireScan(10_100L))

            // Emit frames that queue up
            source.emitFrames(150)
            waitForCondition { coordinator.committedEndFrame >= 50 }

            // Simulate race: time moves forward while writer drains remaining queue blocks during stop
            // When stop is called with an older timestamp (as was passed from SentenceRecorder before drain),
            // the writer observes a later time, but stop coordinates without throwing IllegalArgumentException.
            time.set(10_500L)
            val staleStopMillis = 10_200L

            coordinator.stop(staleStopMillis)

            assertThat(coordinator.currentState).isEqualTo(PcmCoordinatorState.STOPPED)
            assertThat(coordinator.committedEndFrame).isAtLeast(150L)

            // The context is sealed, retaining the accepted prefix
            val view = checkNotNull(coordinator.openEditor(scanId, 10_600L))
            assertThat(view.retention.stage).isEqualTo(RetentionStage.SEALED)
            assertThat(view.retention.reason).isEqualTo(RetentionEnd.CAPTURE_STOPPED)
            assertThat(view.source).isNotNull()
            assertThat(view.source?.endExclusive).isAtLeast(150L)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun sourceStartFailureTransitionsToFailedState() {
        val dir = temporary.newFolder()
        val limits = RecordingLimits()
        val source = FakeAudioSource(failOnStart = true)
        val coordinator = PcmStorageCoordinator(dir, limits)
        try {
            val started = coordinator.start(source)
            assertThat(started).isFalse()
            assertThat(coordinator.currentState).isEqualTo(PcmCoordinatorState.FAILED)
            assertThat(coordinator.failureReason).isEqualTo(PcmCoordinatorFailure.SOURCE_START_FAILED)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun sourceReadFailureTransitionsToFailedState() {
        val dir = temporary.newFolder()
        val limits = RecordingLimits()
        val config = PcmCoordinatorConfig(blockFrames = 10, queueCapacity = 10)
        val source = FakeAudioSource(failOnRead = true)
        val coordinator = PcmStorageCoordinator(dir, limits, config)
        try {
            coordinator.start(source)
            waitForCondition { coordinator.currentState == PcmCoordinatorState.FAILED }
            assertThat(coordinator.failureReason).isEqualTo(PcmCoordinatorFailure.SOURCE_READ_FAILED)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun cleanAbandonedFilesDeletesPcmAndTmpFiles() {
        val dir = temporary.newFolder()
        File(dir, "abandoned1.pcm").writeBytes(ByteArray(100))
        File(dir, "abandoned2.tmp").writeBytes(ByteArray(50))
        File(dir, "keep_this.txt").writeBytes(ByteArray(10))

        val deleted = PcmStorageCoordinator.cleanAbandonedFiles(dir)
        assertThat(deleted).isEqualTo(2)
        assertThat(File(dir, "abandoned1.pcm").exists()).isFalse()
        assertThat(File(dir, "abandoned2.tmp").exists()).isFalse()
        assertThat(File(dir, "keep_this.txt").exists()).isTrue()
    }

    @Test
    fun refreshDuringPublicationPreservesTheContiguousSamples() {
        val time = AtomicLong(10_000L)
        val pauseClock = AtomicBoolean(false)
        val sampled = CountDownLatch(1)
        val release = CountDownLatch(1)
        val refreshed = CountDownLatch(1)
        val workerError = AtomicReference<Throwable?>()
        val source = FakeAudioSource(blockFrames = 10)
        val coordinator = PcmStorageCoordinator(
            temporary.newFolder(), RecordingLimits(),
            PcmCoordinatorConfig(sampleRate = 1000, chunkFrames = 100, blockFrames = 10, minFreeSpaceBytes = 0),
            nowMillis = {
                val value = time.get()
                if (pauseClock.compareAndSet(true, false)) {
                    Thread.currentThread().uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> workerError.set(error) }
                    sampled.countDown()
                    check(release.await(3, TimeUnit.SECONDS))
                }
                value
            },
            nowNanos = { time.get() * 1_000_000L },
        )
        try {
            coordinator.start(source)
            val context = checkNotNull(coordinator.acquireScan(10_000))
            coordinator.openEditor(context, 10_000)
            time.set(10_010)
            pauseClock.set(true)
            source.emitFrames(10)
            assertThat(sampled.await(3, TimeUnit.SECONDS)).isTrue()
            time.set(10_020)
            val refresh = FutureTask {
                coordinator.refresh(context, 10_020).also { refreshed.countDown() }
            }
            Thread(refresh, "test-refresh-caller").apply { isDaemon = true; start() }
            // The former independent writer allowed Refresh to finish while its timestamp was paused.
            // A serialized owner may keep Refresh pending until publication completes.
            refreshed.await(100, TimeUnit.MILLISECONDS)
            release.countDown()
            assertThat(refresh.get(3, TimeUnit.SECONDS)?.scanFrame).isEqualTo(0L)
            assertThat(workerError.get()).isNull()
            coordinator.snapshot(AudioRange(0, 10)).use { snapshot ->
                assertThat(snapshot.openStream().use { it.readBytes() }).isEqualTo(source.expectedPcm(0, 10))
            }
            coordinator.stop()
            assertThat(coordinator.failureReason).isNull()
        } finally {
            release.countDown()
            coordinator.close()
        }
    }

    @Test
    fun throwingStopReportsFailureAndReleasesTheSource() {
        val released = AtomicBoolean(false)
        val source = object : AudioSource {
            override val sampleRate = 48000
            override val channelCount = 1
            override fun start() = true
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int { Thread.sleep(1); return 0 }
            override fun timestampNanos(): Long? = null
            override fun stop() { throw IllegalStateException("Synthetic stop failure") }
            override fun close() { released.set(true) }
        }
        val coordinator = PcmStorageCoordinator(temporary.newFolder(), RecordingLimits())
        try {
            coordinator.start(source)
            coordinator.stop()
            assertThat(coordinator.currentState).isEqualTo(PcmCoordinatorState.FAILED)
            assertThat(released.get()).isTrue()
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun closedSessionStopAndCloseAreIdempotent() {
        val coordinator = PcmStorageCoordinator(temporary.newFolder(), RecordingLimits())
        coordinator.start(FakeAudioSource(sampleRate = 48000))
        coordinator.close()
        coordinator.stop()
        coordinator.close()
        assertThat(coordinator.currentState).isEqualTo(PcmCoordinatorState.CLOSED)
    }

    @Test
    fun lowSpaceRejectsABlockBeforeItCrossesTheSafetyReserve() {
        val source = FakeAudioSource(sampleRate = 1000, blockFrames = 50)
        val coordinator = PcmStorageCoordinator(
            temporary.newFolder(), RecordingLimits(),
            PcmCoordinatorConfig(sampleRate = 1000, chunkFrames = 100, blockFrames = 50,
                minFreeSpaceBytes = 100, usableSpace = { 150 }),
            nowMillis = { 10_000 }, nowNanos = { 10_000_000_000L },
        )
        try {
            coordinator.start(source)
            source.emitFrames(50)
            waitForCondition {
                coordinator.currentState == PcmCoordinatorState.FAILED || coordinator.committedEndFrame >= 50
            }
            assertThat(coordinator.failureReason).isEqualTo(PcmCoordinatorFailure.STORAGE_QUOTA_EXCEEDED)
            assertThat(coordinator.committedEndFrame).isEqualTo(0)
            assertThat(coordinator.retainedBytes).isEqualTo(0)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun lowSpaceAfterAValidBlockPreservesItsExactPrefix() {
        val freeBytes = AtomicLong(1000)
        val source = FakeAudioSource(sampleRate = 1000, blockFrames = 50)
        val coordinator = PcmStorageCoordinator(
            temporary.newFolder(), RecordingLimits(),
            PcmCoordinatorConfig(sampleRate = 1000, chunkFrames = 100, blockFrames = 50,
                minFreeSpaceBytes = 100, usableSpace = { freeBytes.get() }),
            nowMillis = { 10_000 }, nowNanos = { 10_000_000_000L },
        )
        try {
            coordinator.start(source)
            source.emitFrames(50)
            waitForCondition { coordinator.committedEndFrame >= 50 }
            freeBytes.set(150)
            source.emitFrames(50)
            waitForCondition { coordinator.currentState == PcmCoordinatorState.FAILED }

            assertThat(coordinator.failureReason).isEqualTo(PcmCoordinatorFailure.STORAGE_QUOTA_EXCEEDED)
            assertThat(coordinator.committedEndFrame).isEqualTo(50)
            coordinator.snapshot(AudioRange(0, 50)).use { snapshot ->
                assertThat(snapshot.openStream().use { it.readBytes() })
                    .isEqualTo(source.expectedPcm(0, 50))
            }
        } finally {
            coordinator.close()
        }
    }

    private fun waitForCondition(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        if (!condition()) throw AssertionError("Condition was not met within $timeoutMs ms")
    }

    private class FakeAudioSource(
        override val sampleRate: Int = 1000,
        val blockFrames: Int = 50,
        val failOnStart: Boolean = false,
        val failOnRead: Boolean = false,
    ) : AudioSource {
        override val channelCount: Int = 1
        private val running = AtomicBoolean(false)
        private val totalEmittedFrames = AtomicLong(0)
        private val targetFrames = AtomicLong(0)

        override fun start(): Boolean {
            if (failOnStart) return false
            running.set(true)
            return true
        }

        fun emitFrames(count: Int) {
            targetFrames.addAndGet(count.toLong())
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!running.get()) return -1
            if (failOnRead) return -1
            val needed = minOf(blockFrames.toLong(), targetFrames.get() - totalEmittedFrames.get())
            if (needed <= 0) {
                Thread.sleep(10)
                return 0
            }
            val framesToRead = minOf(needed, (length / 2).toLong()).toInt()
            val start = totalEmittedFrames.get()
            for (i in 0 until framesToRead) {
                val frame = start + i
                buffer[offset + i * 2] = frame.toByte()
                buffer[offset + i * 2 + 1] = (frame shr 8).toByte()
            }
            totalEmittedFrames.addAndGet(framesToRead.toLong())
            return framesToRead * 2
        }

        fun expectedPcm(startFrame: Long, endFrame: Long): ByteArray {
            val count = (endFrame - startFrame).toInt()
            val bytes = ByteArray(count * 2)
            for (i in 0 until count) {
                val frame = startFrame + i
                bytes[i * 2] = frame.toByte()
                bytes[i * 2 + 1] = (frame shr 8).toByte()
            }
            return bytes
        }

        override fun timestampNanos(): Long? = 10_000_000_000L

        override fun stop() {
            running.set(false)
        }

        override fun close() {
            stop()
        }
    }
}
