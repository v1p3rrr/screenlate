package com.vpr.screenlate.audio

import android.content.ContextWrapper
import android.os.SystemClock
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.backup.BackupPreferences
import com.vpr.screenlate.backup.BackupSection
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.common.audio.PlaybackCoordinator
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.core.media.AudioSource
import com.vpr.screenlate.core.media.RecordingLimits
import com.vpr.screenlate.core.media.RecordingSettings
import com.vpr.screenlate.core.media.RecordingSettingsRepository
import com.vpr.screenlate.core.media.SentenceRecorder
import com.vpr.screenlate.core.media.SentenceRecordingState
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import com.vpr.screenlate.settings.EInkSizes
import com.vpr.screenlate.settings.SettingsReset
import com.vpr.screenlate.settings.SettingsSection
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** File-backed reset/restore checks against the production recorder in an isolated application. */
@RunWith(AndroidJUnit4::class)
class RecordingSettingsDeviceTest {
    private val unrelatedKey = booleanPreferencesKey("offline_mode")

    @Test
    fun audioResetPreservesCaptureUnrelatedSettingsAndExistingScanLimits() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.repository.setRecordingEnabled(true)
            fixture.repository.setDefaultEditor(false)
            fixture.repository.setLimits(RecordingLimits(90_000, 60_000, 30_000))
            fixture.repository.setMinSilenceMillis(2_100)
            fixture.repository.setSilenceMarginMillis(300)
            fixture.preferences.edit { it[unrelatedKey] = true }
            val source = BlockingSource()
            val recorder = fixture.recorder()
            try {
                assertThat(recorder.start(source)).isTrue()
                val scanMillis = SystemClock.elapsedRealtime()
                val first = checkNotNull(recorder.acquireScan(scanMillis))
                val before = checkNotNull(recorder.view(first))
                assertThat(before.retention.cutoffMillis).isEqualTo(scanMillis + 60_000)

                fixture.reset().reset(SettingsSection.AUDIO)

                assertThat(fixture.repository.settings.first()).isEqualTo(RecordingSettings())
                assertThat(fixture.preferences.data.first()[unrelatedKey]).isTrue()
                assertThat(recorder.isRecording).isTrue()
                assertThat(recorder.isCaptureActive).isTrue()
                assertThat(source.starts.get()).isEqualTo(1)
                assertThat(source.stops.get()).isEqualTo(0)
                assertThat(checkNotNull(recorder.view(first)).retention.cutoffMillis)
                    .isEqualTo(before.retention.cutoffMillis)

                val nextScanMillis = SystemClock.elapsedRealtime()
                val next = checkNotNull(recorder.acquireScan(nextScanMillis))
                assertThat(checkNotNull(recorder.view(next)).retention.cutoffMillis)
                    .isEqualTo(nextScanMillis + RecordingLimits().waitingMillis)
            } finally {
                recorder.stop()
                recorder.close()
            }
            assertThat(source.stops.get()).isEqualTo(1)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun restoreValidatesSettingsAndNeverStartsCaptureWithoutAnExplicitStart() = runBlocking {
        val fixture = Fixture()
        try {
            val restoredLimits = RecordingLimits(60_000, 90_000, 30_000)
            fixture.repository.setRecordingEnabled(true)
            fixture.repository.setDefaultEditor(false)
            fixture.repository.setLimits(restoredLimits)
            fixture.repository.setMinSilenceMillis(2_500)
            fixture.repository.setSilenceMarginMillis(400)
            fixture.preferences.edit { it[unrelatedKey] = true }
            val validBackup = fixture.backup()
            fixture.reset().reset(SettingsSection.AUDIO)
            fixture.restore(validBackup)

            val restored = fixture.repository.settings.first()
            assertThat(restored.recordingEnabled).isTrue()
            assertThat(restored.defaultEditor).isFalse()
            assertThat(restored.limits).isEqualTo(restoredLimits)
            assertThat(restored.minSilenceMillis).isEqualTo(2_500)
            assertThat(restored.silenceMarginMillis).isEqualTo(400)
            assertThat(fixture.preferences.data.first()[unrelatedKey]).isTrue()
            fixture.assertRestoreDoesNotStartThenAcceptsExplicitStart()

            fixture.preferences.edit {
                it[RecordingSettingsRepository.MIN_SILENCE_MILLIS] = -1L
                it[RecordingSettingsRepository.SILENCE_MARGIN_MILLIS] = Long.MAX_VALUE
            }
            val invalidBackup = fixture.backup()
            fixture.restore(invalidBackup)

            val recovered = fixture.repository.settings.first()
            assertThat(recovered.minSilenceMillis)
                .isEqualTo(RecordingSettings.DEFAULT_MIN_SILENCE_MILLIS)
            assertThat(recovered.silenceMarginMillis)
                .isEqualTo(RecordingSettings.DEFAULT_SILENCE_MARGIN_MILLIS)
            assertThat(recovered.limits).isEqualTo(restoredLimits)
            assertThat(recovered.recordingEnabled).isTrue()
            fixture.assertRestoreDoesNotStartThenAcceptsExplicitStart()
        } finally {
            fixture.close()
        }
    }

    private suspend fun Fixture.assertRestoreDoesNotStartThenAcceptsExplicitStart() {
        val source = BlockingSource()
        val recorder = recorder()
        try {
            kotlinx.coroutines.delay(50)
            assertThat(recorder.recordingState.value).isEqualTo(SentenceRecordingState.Idle)
            assertThat(recorder.isCaptureActive).isFalse()
            assertThat(source.starts.get()).isEqualTo(0)
            assertThat(recorder.start(source)).isTrue()
            assertThat(recorder.isRecording).isTrue()
            assertThat(source.starts.get()).isEqualTo(1)
        } finally {
            recorder.stop()
            recorder.close()
        }
    }

    private suspend fun Fixture.backup() = BackupPreferences.encode(
        preferences.data.first(),
        sections = setOf(BackupSection.AUDIO),
    ).let(BackupPreferences::decode)

    private suspend fun Fixture.restore(backup: Map<androidx.datastore.preferences.core.Preferences.Key<*>, Any>) {
        preferences.edit { mutable ->
            BackupPreferences.restore(mutable, backup, sections = setOf(BackupSection.AUDIO))
        }
    }

    private inner class Fixture {
        val root = File(context.cacheDir, "recording-settings-${UUID.randomUUID()}").apply { mkdirs() }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = PreferenceDataStoreFactory.create(scope = scope) {
            File(root, "settings.preferences_pb")
        }
        val repository = RecordingSettingsRepository(preferences)
        private val isolatedContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = File(root, "no-backup").apply { mkdirs() }
        }

        fun recorder() = SentenceRecorder(isolatedContext, repository, PlaybackCoordinator())

        fun reset() = SettingsReset(
            isolatedContext,
            preferences,
            EInkSizes(
                AppSettingsRepository(preferences),
                OverlaySettingsRepository(preferences),
                PopupAppearanceRepository(preferences),
            ),
            AudioSettingsRepository(preferences),
        )

        fun close() {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    private class BlockingSource : AudioSource {
        override val sampleRate = 48_000
        override val channelCount = 1
        val starts = AtomicInteger()
        val stops = AtomicInteger()
        private val stopped = CountDownLatch(1)
        private var emitted = false

        override fun start(): Boolean {
            starts.incrementAndGet()
            return true
        }

        override fun timestampNanos(): Long = SystemClock.elapsedRealtimeNanos()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!emitted) {
                emitted = true
                val count = minOf(length, 9_600)
                buffer.fill(0, offset, offset + count)
                return count
            }
            stopped.await()
            return -1
        }

        override fun stop() {
            stops.incrementAndGet()
            stopped.countDown()
        }

        override fun close() = stopped.countDown()
    }

    private companion object {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
    }
}
