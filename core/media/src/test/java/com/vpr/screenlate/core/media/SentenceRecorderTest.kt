package com.vpr.screenlate.core.media

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

class SentenceRecorderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun statusShowsUnseenFramesWithoutOpeningOrMovingTheShownSource() = runBlocking {
        val time = AtomicLong(10_000)
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()),
            { time.get() }, { time.get() * 1_000_000L })
        val source = FakeAudioSource()
        try {
            recorder.start(source)
            source.emitFrames(100)
            withTimeout(3000) { while (recorder.waveformSummary(AudioRange(0, 100)) == null) delay(1) }
            time.set(10_100)
            val id = checkNotNull(recorder.acquireScan())
            val prepared = checkNotNull(recorder.prepareEditor(id))
            source.emitFrames(100)
            time.set(10_200)
            withTimeout(3000) { while (recorder.waveformSummary(AudioRange(0, 200)) == null) delay(1) }
            val status = checkNotNull(recorder.view(id))
            assertThat(status.source).isEqualTo(prepared.source)
            assertThat(status.revision).isEqualTo(prepared.revision)
            assertThat(status.newFrames).isEqualTo(100)
            assertThat(status.retention.stage).isEqualTo(RetentionStage.WAITING)
            assertThat(status.retention.firstEditorMillis).isNull()
        } finally { recorder.close() }
    }

    @Test
    fun pendingEditorLeaseKeepsItsStoppedSessionAcrossRestartAndReleasesOnlyThatSession() = runBlocking {
        val time = AtomicLong(10_000)
        val root = temporary.newFolder()
        val recorder = SentenceRecorder(root, RecordingSettingsRepository(MemoryDataStore()),
            { time.get() }, { time.get() * 1_000_000L })
        val first = FakeAudioSource()
        var retained: SentenceRecorder.ContextLease? = null
        try {
            assertThat(recorder.start(first)).isTrue()
            first.emitFrames(100)
            withTimeout(3000) {
                while (recorder.waveformSummary(AudioRange(0, 100)) == null) delay(1)
            }
            time.set(10_100)
            val old = checkNotNull(recorder.acquireScan())
            val lease = checkNotNull(recorder.retainContext(old))
            retained = lease
            assertThat(recorder.acquireScan()).isNull()
            val range = checkNotNull(recorder.prepareEditor(old)?.source)
            val oldDirectory = root.listFiles()!!.single()
            recorder.stop()
            recorder.releaseContext(old)
            assertThat(recorder.retainContext(old)).isNull()
            val next = FakeAudioSource()
            assertThat(recorder.start(next)).isTrue()
            next.emitFrames(200)
            withTimeout(3000) {
                while (recorder.waveformSummary(AudioRange(0, 200)) == null) delay(1)
            }
            val fresh = checkNotNull(recorder.acquireScan())
            assertThat(recorder.openEditor(old)?.source).isEqualTo(range)
            assertThat(recorder.isRecording(old)).isFalse()
            assertThat(recorder.isRecording(fresh)).isTrue()
            val snapshot = checkNotNull(recorder.snapshot(range, old))
            snapshot.use { assertThat(it.openStream().use { stream -> stream.readBytes() })
                .isEqualTo(first.expectedPcm(range.start, range.endExclusive)) }
            lease.close()
            lease.close()
            assertThat(recorder.prepareEditor(old)).isNull()
            assertThat(recorder.prepareEditor(fresh)).isNotNull()
            withTimeout(3000) { while (oldDirectory.exists()) delay(1) }
        } finally { retained?.close(); recorder.close() }
    }

    @Test
    fun closingRecorderPreservesALiveContextUntilItsConsumerCloses() = runBlocking {
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()))
        var lease: SentenceRecorder.ContextLease? = null
        try {
            assertThat(recorder.start(FakeAudioSource())).isTrue()
            val id = checkNotNull(recorder.acquireScan())
            lease = checkNotNull(recorder.retainContext(id))
            recorder.close()
            assertThat(recorder.retainContext(id)).isNull()
            assertThat(recorder.prepareEditor(id)).isNotNull()
            lease.close()
            assertThat(recorder.prepareEditor(id)).isNull()
        } finally { lease?.close(); recorder.close() }
    }

    @Test
    fun editorReadsCannotUseAnotherScanWithMatchingFramePositions() = runBlocking {
        val time = AtomicLong(10_000L)
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()),
            { time.get() }, { time.get() * 1_000_000L })
        val source = FakeAudioSource()
        try {
            recorder.start(source)
            source.emitFrames(100)
            val range = AudioRange(0, 100)
            withTimeout(3000) {
                while (recorder.waveformSummary(range) == null) delay(1)
            }
            time.set(10_100)
            val old = checkNotNull(recorder.acquireScan())
            recorder.openEditor(old)
            val current = checkNotNull(recorder.acquireScan())
            recorder.openEditor(current)
            assertThat(recorder.snapshot(range, old)).isNull()
            assertThat(recorder.waveformSummary(range, contextId = old)).isNull()
            assertThat(recorder.waveformSummary(range, contextId = current)).isNotNull()
        } finally { recorder.close() }
    }

    @Test
    fun previewAndPronunciationReplaceEachOtherWithoutRevivingAnOldSeek() = runBlocking {
        val playback = PlaybackCoordinator()
        val sinks = java.util.concurrent.CopyOnWriteArrayList<HeldSink>()
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()),
            playback = playback, previewSinkFactory = { HeldSink().also { sinks += it } })
        val source = FakeAudioSource(sampleRate = 48000, blockFrames = 4800)
        try {
            recorder.start(source)
            source.emitFrames(4800)
            withTimeout(3000) {
                while (true) {
                    val snapshot = recorder.snapshot(AudioRange(0, 4800))
                    if (snapshot != null) { snapshot.close(); break }
                    delay(1)
                }
            }
            val interrupted = AtomicBoolean()
            recorder.startPreview(AudioEdit(AudioRange(0, 4800)), onStopped = { interrupted.set(true) })
            withTimeout(3000) { while (sinks.isEmpty()) delay(1) }
            assertThat(sinks[0].started.await(3, TimeUnit.SECONDS)).isTrue()
            var pronunciationStopped = false
            val pronunciation = checkNotNull(playback.acquire { pronunciationStopped = true; true })
            assertThat(recorder.isPreviewPlaying).isFalse()
            assertThat(interrupted.get()).isTrue()
            assertThat(sinks[0].closed.get()).isTrue()
            recorder.seekPreview(100)
            assertThat(sinks.size).isEqualTo(1)
            assertThat(pronunciation.isCurrent).isTrue()
            recorder.startPreview(AudioEdit(AudioRange(0, 4800)))
            assertThat(pronunciationStopped).isTrue()
            assertThat(pronunciation.isCurrent).isFalse()
        } finally {
            recorder.close()
        }
    }

    private class HeldSink : AudioSink {
        val started = CountDownLatch(1)
        val closed = AtomicBoolean()
        private val stopped = CountDownLatch(1)
        override val playedFrames: Long get() = 0
        override fun write(buffer: ByteArray, offset: Int, size: Int) = size
        override fun awaitProgress(timeoutMillis: Long) { stopped.await(timeoutMillis, TimeUnit.MILLISECONDS) }
        override fun finishInput() = true
        override fun play() { started.countDown() }
        override fun pause() { stopped.countDown() }
        override fun stop() { stopped.countDown() }
        override fun flush() = Unit
        override fun close() { closed.set(true); stopped.countDown() }
    }

    @Test
    fun privateSpeechProtectionCoversDrainingAfterNativeInputRelease() = runBlocking {
        val playback = PlaybackCoordinator()
        val inputClosed = AtomicBoolean()
        val drainHeld = CountDownLatch(1)
        val finishDrain = CountDownLatch(1)
        val running = AtomicBoolean()
        val source = object : AudioSource {
            override val sampleRate = 1000
            override val channelCount = 1
            override fun start(): Boolean { running.set(true); return true }
            override fun timestampNanos(): Long? = null
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                Thread.sleep(1)
                return if (running.get()) 0 else -1
            }
            override fun stop() { running.set(false) }
            override fun close() { inputClosed.set(true) }
        }
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()),
            nowMillis = {
                if (inputClosed.get()) { drainHeld.countDown(); check(finishDrain.await(3, TimeUnit.SECONDS)) }
                10_000L
            }, nowNanos = { 10_000_000_000L }, playback = playback)
        recorder.start(source)
        val stopping = launch(Dispatchers.Default) { recorder.stop() }
        try {
            assertThat(drainHeld.await(3, TimeUnit.SECONDS)).isTrue()
            assertThat(recorder.recordingState.value).isEqualTo(SentenceRecordingState.Stopping)
            assertThat(playback.requiresPrivateSpeech).isTrue()
        } finally {
            finishDrain.countDown()
            stopping.join()
            recorder.close()
        }
        assertThat(playback.requiresPrivateSpeech).isFalse()
    }

    @Test
    fun queuedBusyFeedbackFromAnOlderPreviewRequestIsDiscarded() = runBlocking {
        val playback = PlaybackCoordinator()
        playback.acquire { false }
        val callbacks = ArrayDeque<Runnable>()
        val feedback = mutableListOf<String>()
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()),
            previewCallbackExecutor = Executor { callbacks += it }, playback = playback)
        val edit = AudioEdit(AudioRange(0, 1))
        recorder.startPreview(edit, onError = { feedback += "old" })
        recorder.startPreview(edit, onError = { feedback += "current" })
        while (callbacks.isNotEmpty()) callbacks.removeFirst().run()
        assertThat(feedback).containsExactly("current")
        recorder.close()
    }

    @Test
    fun startStopLifecycle() = runTest {
        val root = temporary.newFolder()
        val dataStore = MemoryDataStore()
        val settings = RecordingSettingsRepository(dataStore)
        val recorder = SentenceRecorder(root, settings)

        assertThat(recorder.isRecording).isFalse()
        assertThat(recorder.recordingState.value).isEqualTo(SentenceRecordingState.Idle)

        val source = FakeAudioSource(sampleRate = 48000, blockFrames = 4800)
        val started = recorder.start(source)
        assertThat(started).isTrue()
        assertThat(recorder.isRecording).isTrue()
        assertThat(recorder.recordingState.value).isInstanceOf(SentenceRecordingState.Recording::class.java)

        source.emitFrames(48000)
        val scanId = recorder.acquireScan()
        assertThat(scanId).isNotNull()

        recorder.stop()
        assertThat(recorder.isRecording).isFalse()
        assertThat(recorder.recordingState.value).isInstanceOf(SentenceRecordingState.Stopped::class.java)

        // After stop, new scan cannot be acquired
        assertThat(recorder.acquireScan()).isNull()

        recorder.close()
        assertThat(recorder.recordingState.value).isEqualTo(SentenceRecordingState.Idle)
    }

    @Test
    fun failedStart() = runTest {
        val root = temporary.newFolder()
        val dataStore = MemoryDataStore()
        val settings = RecordingSettingsRepository(dataStore)
        val recorder = SentenceRecorder(root, settings)

        val failingSource = FakeAudioSource(sampleRate = 48000, failOnStart = true)
        val started = recorder.start(failingSource)
        assertThat(started).isFalse()
        assertThat(recorder.isRecording).isFalse()
        assertThat(recorder.recordingState.value).isInstanceOf(SentenceRecordingState.Failed::class.java)

        recorder.close()
    }

    @Test
    fun failedStartReleasesTheSource() = runTest {
        val released = AtomicBoolean(false)
        val source = object : AudioSource {
            override val sampleRate = 48000
            override val channelCount = 1
            override fun start() = false
            override fun read(buffer: ByteArray, offset: Int, length: Int) = -1
            override fun timestampNanos(): Long? = null
            override fun stop() = Unit
            override fun close() { released.set(true) }
        }
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()))
        try {
            assertThat(recorder.start(source)).isFalse()
            assertThat(released.get()).isTrue()
        } finally {
            recorder.close()
        }
    }

    @Test
    fun asynchronousReadFailureReachesThePublicState() = runBlocking {
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()))
        try {
            assertThat(recorder.start(FakeAudioSource(sampleRate = 48000, failOnRead = true))).isTrue()
            val failure = withTimeout(3000) {
                recorder.recordingState.first { it is SentenceRecordingState.Failed }
            }
            assertThat(failure).isEqualTo(SentenceRecordingState.Failed(PcmCoordinatorFailure.SOURCE_READ_FAILED))
            assertThat(recorder.isRecording).isFalse()
        } finally {
            recorder.close()
        }
    }

    @Test
    fun cancelledStartupDoesNotLeaveCaptureRunning() = runBlocking {
        val starting = CountDownLatch(1)
        val allowStart = CountDownLatch(1)
        val released = AtomicBoolean(false)
        val source = object : AudioSource {
            override val sampleRate = 48000
            override val channelCount = 1
            override fun start(): Boolean {
                starting.countDown()
                check(allowStart.await(3, TimeUnit.SECONDS))
                return true
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int { Thread.sleep(1); return 0 }
            override fun timestampNanos(): Long? = null
            override fun stop() = Unit
            override fun close() { released.set(true) }
        }
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()))
        val job = launch(Dispatchers.Default) { recorder.start(source) }
        try {
            assertThat(starting.await(3, TimeUnit.SECONDS)).isTrue()
            job.cancel()
            allowStart.countDown()
            job.join()
            assertThat(recorder.isCaptureActive).isFalse()
            assertThat(released.get()).isTrue()
        } finally {
            allowStart.countDown()
            recorder.close()
        }
    }

    @Test
    fun stoppedSessionReleasesHistoryAfterItsLastReaderCloses() = runBlocking {
        val root = temporary.newFolder()
        val recorder = SentenceRecorder(root, RecordingSettingsRepository(MemoryDataStore()))
        val source = FakeAudioSource()
        try {
            recorder.start(source)
            source.emitFrames(100)
            val snapshot = withTimeout(3000) {
                var lease = recorder.snapshot(AudioRange(0, 100))
                while (lease == null) {
                    delay(1)
                    lease = recorder.snapshot(AudioRange(0, 100))
                }
                lease
            }
            val reader = snapshot.openStream()
            snapshot.close()
            recorder.stop()
            reader.use { assertThat(it.readBytes()).isEqualTo(source.expectedPcm(0, 100)) }
            withTimeout(3000) {
                while (root.listFiles()?.isNotEmpty() == true) delay(1)
            }
            assertThat(recorder.isCaptureActive).isFalse()
            assertThat(recorder.recordingState.value).isInstanceOf(SentenceRecordingState.Stopped::class.java)
        } finally {
            recorder.close()
        }
    }

    @Test
    fun stoppedSessionKeepsItsScanUntilTheMatchingContextIsReleased() = runBlocking {
        val root = temporary.newFolder()
        val time = AtomicLong(10_000L)
        val recorder = SentenceRecorder(root, RecordingSettingsRepository(MemoryDataStore()),
            { time.get() }, { time.get() * 1_000_000L })
        val source = FakeAudioSource()
        try {
            recorder.start(source)
            source.emitFrames(100)
            withTimeout(3000) {
                while (true) {
                    val snapshot = recorder.snapshot(AudioRange(0, 100))
                    if (snapshot != null) { snapshot.close(); break }
                    delay(1)
                }
            }
            time.set(10_100)
            val context = checkNotNull(recorder.acquireScan())
            recorder.stop()
            assertThat(recorder.openEditor(context)?.source).isEqualTo(AudioRange(0, 100))
            recorder.releaseContext(AudioContextId(UUID.randomUUID(), context.scan))
            assertThat(recorder.refresh(context)?.source).isEqualTo(AudioRange(0, 100))
            recorder.snapshot(AudioRange(0, 100))!!.use { snapshot ->
                assertThat(snapshot.openStream().use { it.readBytes() }).isEqualTo(source.expectedPcm(0, 100))
            }
            recorder.releaseContext(context)
            withTimeout(3000) {
                while (root.listFiles()?.isNotEmpty() == true) delay(1)
            }
            assertThat(recorder.openEditor(context)).isNull()
        } finally {
            recorder.close()
        }
    }

    @Test
    fun failedCaptureWithoutAScanReleasesItsSessionDirectory() = runBlocking {
        val root = temporary.newFolder()
        val recorder = SentenceRecorder(root, RecordingSettingsRepository(MemoryDataStore()))
        try {
            recorder.start(FakeAudioSource(failOnRead = true))
            withTimeout(3000) {
                recorder.recordingState.first { it is SentenceRecordingState.Failed }
                while (root.listFiles()?.isNotEmpty() == true) delay(1)
            }
            assertThat(recorder.recordingState.value)
                .isEqualTo(SentenceRecordingState.Failed(PcmCoordinatorFailure.SOURCE_READ_FAILED))
        } finally {
            recorder.close()
        }
    }

    @Test
    fun previousSessionSnapshotSurvivesRestart() = runBlocking {
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()))
        val source = FakeAudioSource()
        try {
            recorder.start(source)
            source.emitFrames(100)
            val snapshot = withTimeout(3000) {
                var lease = recorder.snapshot(AudioRange(0, 100))
                while (lease == null) {
                    delay(1)
                    lease = recorder.snapshot(AudioRange(0, 100))
                }
                lease
            }
            snapshot.use {
                recorder.stop()
                recorder.start(FakeAudioSource())
                assertThat(it.openStream().use { stream -> stream.readBytes() }).isEqualTo(source.expectedPcm(0, 100))
            }
        } finally {
            recorder.close()
        }
    }

    @Test
    fun slowSourceStopDoesNotBlockTheCallingDispatcher() = runBlocking {
        val stopping = CountDownLatch(1)
        val allowStop = CountDownLatch(1)
        val pulse = CountDownLatch(1)
        val running = AtomicBoolean(false)
        val source = object : AudioSource {
            override val sampleRate = 48000
            override val channelCount = 1
            override fun start(): Boolean { running.set(true); return true }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                Thread.sleep(1)
                return if (running.get()) 0 else -1
            }
            override fun timestampNanos(): Long? = null
            override fun stop() {
                stopping.countDown()
                check(allowStop.await(3, TimeUnit.SECONDS))
                running.set(false)
            }
            override fun close() { running.set(false) }
        }
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()))
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { ui ->
            recorder.start(source)
            val stop = launch(ui) { recorder.stop() }
            try {
                assertThat(stopping.await(3, TimeUnit.SECONDS)).isTrue()
                val event = launch(ui) { pulse.countDown() }
                assertThat(pulse.await(1, TimeUnit.SECONDS)).isTrue()
                event.join()
                assertThat(recorder.recordingState.value).isEqualTo(SentenceRecordingState.Stopping)
            } finally {
                allowStop.countDown()
                stop.join()
                recorder.close()
            }
        }
    }

    @Test
    fun stuckStopReportsFailureAndRejectsAnotherSourceUntilReleased() = runBlocking {
        val playback = PlaybackCoordinator()
        val release = CountDownLatch(1)
        val reading = AtomicBoolean(true)
        val emitted = AtomicBoolean(false)
        val time = AtomicLong(10_000L)
        val source = object : AudioSource {
            override val sampleRate = 1000
            override val channelCount = 1
            override fun start() = true
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (emitted.compareAndSet(false, true)) {
                    buffer.fill(17, offset, offset + 200)
                    return 200
                }
                Thread.sleep(1)
                return if (reading.get()) 0 else -1
            }
            override fun timestampNanos(): Long? = null
            override fun stop() { release.await(); reading.set(false) }
            override fun close() { reading.set(false) }
        }
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()),
            { time.get() }, { time.get() * 1_000_000L }, playback = playback)
        recorder.start(source)
        withTimeout(3000) {
            var ready = false
            while (!ready) {
                recorder.snapshot(AudioRange(0, 100))?.use { ready = true }
                if (!ready) delay(1)
            }
        }
        time.set(10_100)
        val context = checkNotNull(recorder.acquireScan())
        recorder.openEditor(context)
        val stopped = FutureTask { runBlocking { recorder.stop() } }
        Thread(stopped, "test-stop-caller").apply { isDaemon = true; start() }
        try {
            stopped.get(3, TimeUnit.SECONDS)
            assertThat(recorder.recordingState.value).isInstanceOf(SentenceRecordingState.Failed::class.java)
            assertThat(recorder.isCaptureActive).isTrue()
            assertThat(playback.requiresPrivateSpeech).isTrue()
            assertThat(recorder.start(FakeAudioSource())).isFalse()
            assertThat(recorder.refresh(context)?.source).isEqualTo(AudioRange(0, 100))
        } finally {
            release.countDown()
            stopped.get(3, TimeUnit.SECONDS)
            recorder.close()
            assertThat(playback.requiresPrivateSpeech).isFalse()
        }
    }

    @Test
    fun nextScanUsesChangedLimitsWithoutChangingTheExistingContext() = runBlocking {
        val settings = RecordingSettingsRepository(MemoryDataStore())
        settings.setLimits(RecordingLimits(30_000, 60_000, 90_000))
        val time = AtomicLong(10_000L)
        val recorder = SentenceRecorder(temporary.newFolder(), settings, { time.get() }, { time.get() * 1_000_000L })
        try {
            recorder.start(FakeAudioSource())
            val first = checkNotNull(recorder.acquireScan())
            settings.setLimits(RecordingLimits(60_000, 30_000, 30_000))
            time.set(10_001)
            assertThat(recorder.openEditor(first)?.retention?.cutoffMillis).isEqualTo(100_001L)
            time.set(10_002)
            val next = checkNotNull(recorder.acquireScan())
            assertThat(recorder.openEditor(next)?.retention?.cutoffMillis).isEqualTo(40_002L)
        } finally {
            recorder.close()
        }
    }

    @Test
    fun oldLeasedSessionCountsAgainstNewCaptureWithoutLosingItsSamples() = runBlocking {
        val root = temporary.newFolder()
        val time = AtomicLong(10_000L)
        val ledger = PcmStorageLedger(maxBytes = 400, maxFiles = 8)
        val recorder = SentenceRecorder(root, RecordingSettingsRepository(MemoryDataStore()),
            { time.get() }, { time.get() * 1_000_000L }, storageLedger = ledger)
        val first = FakeAudioSource()
        var retained: SentenceRecorder.ContextLease? = null
        try {
            recorder.start(first)
            first.emitFrames(100)
            withTimeout(3000) {
                while (recorder.snapshot(AudioRange(0, 100))?.also { it.close() } == null) delay(1)
            }
            time.set(10_100)
            val old = checkNotNull(recorder.acquireScan())
            retained = checkNotNull(recorder.retainContext(old))
            recorder.stop()
            recorder.releaseContext(old)

            val next = FakeAudioSource()
            assertThat(recorder.start(next)).isTrue()
            next.emitFrames(100)
            withTimeout(3000) {
                while (recorder.snapshot(AudioRange(0, 100))?.also { it.close() } == null) delay(1)
            }
            assertThat(ledger.usedBytes).isEqualTo(400)
            assertThat(ledger.highWaterBytes).isEqualTo(400)

            next.emitFrames(100)
            val failed = withTimeout(3000) {
                recorder.recordingState.first { it is SentenceRecordingState.Failed }
            }
            assertThat(failed).isEqualTo(
                SentenceRecordingState.Failed(PcmCoordinatorFailure.STORAGE_QUOTA_EXCEEDED))
            recorder.snapshot(AudioRange(0, 100), old).use { snapshot ->
                assertThat(snapshot).isNotNull()
                assertThat(snapshot!!.openStream().use { it.readBytes() })
                    .isEqualTo(first.expectedPcm(0, 100))
            }
        } finally {
            retained?.close()
            recorder.close()
        }
        assertThat(ledger.usedBytes).isEqualTo(0)
        assertThat(ledger.usedFiles).isEqualTo(0)
    }

    @Test
    fun oneWaveformCacheIsRebuiltAcrossLeasedSessionsWithoutTouchingPcm() = runBlocking {
        val time = AtomicLong(10_000L)
        val recorder = SentenceRecorder(temporary.newFolder(), RecordingSettingsRepository(MemoryDataStore()),
            { time.get() }, { time.get() * 1_000_000L })
        val first = FakeAudioSource()
        var retained: SentenceRecorder.ContextLease? = null
        try {
            recorder.start(first)
            first.emitFrames(240)
            withTimeout(3000) {
                while (recorder.snapshot(AudioRange(0, 240))?.also { it.close() } == null) delay(1)
            }
            time.set(10_240)
            val old = checkNotNull(recorder.acquireScan())
            retained = checkNotNull(recorder.retainContext(old))
            val oldRange = checkNotNull(recorder.openEditor(old)?.source)
            assertThat(recorder.waveformSummary(oldRange, contextId = old)).isNotNull()
            assertThat(recorder.waveformCacheContext).isEqualTo(old)
            assertThat(recorder.waveformCacheBytes).isGreaterThan(0)
            recorder.stop()
            recorder.releaseContext(old)

            val next = FakeAudioSource()
            assertThat(recorder.start(next)).isTrue()
            next.emitFrames(480)
            withTimeout(3000) {
                while (recorder.snapshot(AudioRange(0, 480))?.also { it.close() } == null) delay(1)
            }
            time.set(10_480)
            val fresh = checkNotNull(recorder.acquireScan())
            val freshRange = checkNotNull(recorder.openEditor(fresh)?.source)
            assertThat(recorder.waveformSummary(freshRange, contextId = fresh)).isNotNull()
            assertThat(recorder.waveformCacheContext).isEqualTo(fresh)

            assertThat(recorder.waveformSummary(oldRange, contextId = old)).isNotNull()
            assertThat(recorder.waveformCacheContext).isEqualTo(old)
            recorder.snapshot(oldRange, old).use { snapshot ->
                assertThat(snapshot).isNotNull()
                assertThat(snapshot!!.openStream().use { it.readBytes() })
                    .isEqualTo(first.expectedPcm(oldRange.start, oldRange.endExclusive))
            }
        } finally {
            retained?.close()
            recorder.close()
        }
        assertThat(recorder.waveformCacheBytes).isEqualTo(0)
    }

    @Test
    fun firstStartClearsAbandonedPcmWithoutDeletingUnrelatedFiles() = runBlocking {
        val root = temporary.newFolder()
        val legacy = File(root, "legacy.pcm").apply { writeBytes(ByteArray(2)) }
        val abandoned = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        val chunk = File(abandoned, "chunk.pcm").apply { writeBytes(ByteArray(2)) }
        val unrelated = File(root, "keep.txt").apply { writeText("Synthetic fixture") }
        val recorder = SentenceRecorder(root, RecordingSettingsRepository(MemoryDataStore()))
        try {
            recorder.start(FakeAudioSource())
            assertThat(legacy.exists()).isFalse()
            assertThat(chunk.exists()).isFalse()
            assertThat(unrelated.exists()).isTrue()
        } finally {
            recorder.close()
        }
    }
}
