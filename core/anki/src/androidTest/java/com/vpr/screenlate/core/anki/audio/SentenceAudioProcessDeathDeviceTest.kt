package com.vpr.screenlate.core.anki.audio

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.AnkiDroid
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/** Real process death and restart inside the isolated core-Anki test package. */
@RunWith(AndroidJUnit4::class)
class SentenceAudioProcessDeathDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun restartedStagingCleansOrphansAndPreservesLaterFiles() = runBlocking<Unit> {
        val id = UUID.randomUUID().toString()
        val reports = File(context.cacheDir, "sentence-process-reports-$id").apply { mkdirs() }
        val crashed = File(reports, "crashed.properties")
        val restarted = File(reports, "restarted.properties")
        val service = Intent(context, SentenceStagingCrashService::class.java)
            .putExtra(SentenceStagingCrashService.EXTRA_REPORT_DIRECTORY, reports.path)
        try {
            context.startService(Intent(service).setAction(SentenceStagingCrashService.ACTION_CRASH))
            val before = awaitReport(crashed)
            assertThat(before.getProperty("phase")).isEqualTo("ready")
            val crashedPid = before.getProperty("pid").toInt()
            awaitProcessDeath(crashedPid)

            context.startService(Intent(service).setAction(SentenceStagingCrashService.ACTION_RESTART))
            val after = awaitReport(restarted)
            assertThat(after.getProperty("phase")).isEqualTo("complete")
            assertThat(after.getProperty("pid").toInt()).isNotEqualTo(crashedPid)
            assertThat(after.getProperty("firstOrphanExists").toBoolean()).isFalse()
            assertThat(after.getProperty("secondOrphanExists").toBoolean()).isFalse()
            assertThat(after.getProperty("unrelatedPreserved").toBoolean()).isTrue()
            assertThat(after.getProperty("firstNewExists").toBoolean()).isTrue()
            assertThat(after.getProperty("secondNewExists").toBoolean()).isTrue()
            File(after.getProperty("firstNew")).delete()
            File(after.getProperty("secondNew")).delete()
            File(after.getProperty("unrelatedAudio")).delete()
            File(after.getProperty("unrelatedMedia")).delete()
        } finally {
            context.stopService(service)
            reports.deleteRecursively()
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

/** Test-only service that abandons real staging files by killing its own process. */
class SentenceStagingCrashService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val reports = File(checkNotNull(intent?.getStringExtra(EXTRA_REPORT_DIRECTORY)))
        Thread {
            when (action) {
                ACTION_CRASH -> crashWithStaging(File(reports, "crashed.properties"))
                ACTION_RESTART -> restartStaging(File(reports, "restarted.properties"))
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun crashWithStaging(report: File) {
        try {
            val directory = AnkiDroid.mediaDirectory(this)
            val first = SentenceAudioStaging(this).create().apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val second = File(directory, "screenlate_sentence_${UUID.randomUUID()}.opus")
                .apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val unrelatedAudio = File(directory, "screenlate_sentence_${UUID.randomUUID()}.wav")
                .apply { writeBytes(byteArrayOf(7)) }
            val unrelatedMedia = File(directory, "other-${UUID.randomUUID()}.opus")
                .apply { writeBytes(byteArrayOf(8)) }
            writeReport(
                report,
                "phase" to "ready",
                "pid" to Process.myPid(),
                "firstOrphan" to first.path,
                "secondOrphan" to second.path,
                "unrelatedAudio" to unrelatedAudio.path,
                "unrelatedMedia" to unrelatedMedia.path,
            )
            Process.killProcess(Process.myPid())
        } catch (error: Throwable) {
            writeReport(report, "phase" to "failed", "error" to error.toString())
        }
    }

    private fun restartStaging(report: File) {
        try {
            val before = readReport(File(report.parentFile, "crashed.properties"))
            val staging = SentenceAudioStaging(this)
            val firstNew = staging.create()
            val secondNew = staging.create()
            writeReport(
                report,
                "phase" to "complete",
                "pid" to Process.myPid(),
                "firstOrphanExists" to File(before.getProperty("firstOrphan")).exists(),
                "secondOrphanExists" to File(before.getProperty("secondOrphan")).exists(),
                "unrelatedPreserved" to (File(before.getProperty("unrelatedAudio")).isFile &&
                    File(before.getProperty("unrelatedMedia")).isFile),
                "firstNewExists" to firstNew.isFile,
                "secondNewExists" to secondNew.isFile,
                "firstNew" to firstNew.path,
                "secondNew" to secondNew.path,
                "unrelatedAudio" to before.getProperty("unrelatedAudio"),
                "unrelatedMedia" to before.getProperty("unrelatedMedia"),
            )
            stopSelf()
        } catch (error: Throwable) {
            writeReport(report, "phase" to "failed", "error" to error.toString())
        }
    }

    companion object {
        const val EXTRA_REPORT_DIRECTORY = "reports"
        const val ACTION_CRASH = "crash"
        const val ACTION_RESTART = "restart"
    }
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
