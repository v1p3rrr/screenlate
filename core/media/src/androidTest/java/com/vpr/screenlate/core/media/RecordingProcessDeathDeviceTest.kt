package com.vpr.screenlate.core.media

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/** Real process death and restart inside the isolated media test package. */
@RunWith(AndroidJUnit4::class)
class RecordingProcessDeathDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun restartedRecorderCleansOnlyAbandonedAudio() = runBlocking {
        val id = UUID.randomUUID().toString()
        val root = File(context.noBackupFilesDir, "recording-process-death-$id")
        val reportDirectory = File(context.cacheDir, "recording-process-reports-$id").apply { mkdirs() }
        val crashed = File(reportDirectory, "crashed.properties")
        val restarted = File(reportDirectory, "restarted.properties")
        val service = Intent(context, RecordingCrashService::class.java).putExtra(RecordingCrashService.EXTRA_ROOT, root.path)
        try {
            context.startService(Intent(service).setAction(RecordingCrashService.ACTION_CRASH))
            val before = awaitReport(crashed)
            assertThat(before.getProperty("phase")).isEqualTo("ready")
            val crashedPid = before.getProperty("pid").toInt()
            awaitProcessDeath(crashedPid)

            context.startService(Intent(service).setAction(RecordingCrashService.ACTION_RESTART))
            val after = awaitReport(restarted)
            assertThat(after.getProperty("phase")).isEqualTo("complete")
            assertThat(after.getProperty("pid").toInt()).isNotEqualTo(crashedPid)
            assertThat(after.getProperty("started").toBoolean()).isTrue()
            assertThat(after.getProperty("oldSessionExists").toBoolean()).isFalse()
            assertThat(after.getProperty("rootTemporaryExists").toBoolean()).isFalse()
            assertThat(after.getProperty("unrelatedPreserved").toBoolean()).isTrue()
            assertThat(after.getProperty("audioFilesLeft").toInt()).isEqualTo(0)
        } finally {
            context.stopService(service)
            root.deleteRecursively()
            reportDirectory.deleteRecursively()
        }
    }

    private suspend fun awaitReport(file: File): Properties = withTimeout(15_000) {
        while (!file.isFile || file.length() == 0L) delay(25)
        Properties().apply { file.inputStream().use(::load) }
    }

    private suspend fun awaitProcessDeath(pid: Int) = withTimeout(10_000) {
        while (File("/proc/$pid").exists()) delay(25)
    }
}

/** Test-only service whose process is killed without closing its recorder. */
class RecordingCrashService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val root = File(checkNotNull(intent?.getStringExtra(EXTRA_ROOT)))
        val reports = File(cacheDir, root.name.replace("recording-process-death", "recording-process-reports"))
        Thread {
            when (action) {
                ACTION_CRASH -> crashWithLiveRecording(root, File(reports, "crashed.properties"))
                ACTION_RESTART -> restartRecorder(root, File(reports, "restarted.properties"))
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun crashWithLiveRecording(root: File, report: File) {
        try {
            check(root.isDirectory || root.mkdirs())
            val unrelated = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
            File(unrelated, "keep.bin").writeBytes(byteArrayOf(7, 8, 9))
            val recorder = SentenceRecorder(root, settings())
            runBlocking { check(recorder.start(BlockingSource())) }
            runBlocking {
                withTimeout(5_000) {
                    while (true) {
                        val complete = recorder.snapshot(AudioRange(0, 4))?.use { snapshot ->
                            snapshot.openStream().use { it.readBytes().contentEquals(ByteArray(8) { it.toByte() }) }
                        } == true
                        if (complete) break
                        delay(1)
                    }
                }
            }
            val session = checkNotNull(root.listFiles()?.singleOrNull { directory ->
                directory.isDirectory && directory.listFiles()?.any { it.extension == "pcm" } == true
            })
            val temporary = File(root, "abandoned.tmp").apply { writeBytes(byteArrayOf(1, 2)) }
            writeReport(
                report,
                "phase" to "ready",
                "pid" to Process.myPid(),
                "session" to session.path,
                "temporary" to temporary.path,
                "unrelated" to File(unrelated, "keep.bin").path,
            )
            Process.killProcess(Process.myPid())
        } catch (error: Throwable) {
            writeReport(report, "phase" to "failed", "error" to error.toString())
        }
    }

    private fun restartRecorder(root: File, report: File) {
        try {
            val before = readReport(File(report.parentFile, "crashed.properties"))
            val recorder = SentenceRecorder(root, settings())
            val started = runBlocking { recorder.start(BlockingSource()) }
            runBlocking {
                recorder.stop()
                recorder.close()
            }
            val audioFilesLeft = root.walkTopDown().count { it.isFile && it.extension in setOf("pcm", "tmp") }
            writeReport(
                report,
                "phase" to "complete",
                "pid" to Process.myPid(),
                "started" to started,
                "oldSessionExists" to File(before.getProperty("session")).exists(),
                "rootTemporaryExists" to File(before.getProperty("temporary")).exists(),
                "unrelatedPreserved" to File(before.getProperty("unrelated")).isFile,
                "audioFilesLeft" to audioFilesLeft,
            )
            stopSelf()
        } catch (error: Throwable) {
            writeReport(report, "phase" to "failed", "error" to error.toString())
        }
    }

    private class BlockingSource : AudioSource {
        override val sampleRate = 1000
        override val channelCount = 1
        private val stopped = CountDownLatch(1)
        private var emitted = false

        override fun start(): Boolean = true
        override fun timestampNanos(): Long = SystemClock.elapsedRealtimeNanos()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!emitted) {
                emitted = true
                repeat(8) { buffer[offset + it] = it.toByte() }
                return 8
            }
            stopped.await()
            return -1
        }
        override fun stop() { stopped.countDown() }
        override fun close() { stopped.countDown() }
    }

    companion object {
        const val EXTRA_ROOT = "root"
        const val ACTION_CRASH = "crash"
        const val ACTION_RESTART = "restart"
    }
}

private fun settings(): RecordingSettingsRepository {
    val store = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    return RecordingSettingsRepository(store)
}

private fun readReport(file: File): Properties = Properties().apply { file.inputStream().use(::load) }

private fun writeReport(file: File, vararg values: Pair<String, Any?>) {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, file.name + ".writing")
    FileOutputStream(temporary).use { output ->
        Properties().apply { values.forEach { (key, value) -> setProperty(key, value.toString()) } }
            .store(output, "Isolated process fixture")
        output.fd.sync()
    }
    check(temporary.renameTo(file))
}
