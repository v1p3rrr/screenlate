package com.vpr.screenlate.diagnostics

import android.os.Process
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copies the app's own logcat lines (Android shows an app only its own) into rotating files, so the existing log
 * lines outlive the system's small ring buffer: a `logcat -f` child process, which rotates the files itself
 * (`logcat.txt`, `logcat.txt.1`, …). A new process continues after the last line already copied. The child dies with
 * the app; one left over from an earlier process is stopped first.
 */
internal class LogcatTee(private val directory: File) {
    private var process: java.lang.Process? = null
    private val file get() = File(directory, FILE)

    /** Copies from [since] (logcat's `-T` time); by default after the last line already copied, else the whole buffer. */
    fun start(since: String? = lastTimestamp(file)) {
        directory.mkdirs()
        stopLeftovers()
        val command = buildList {
            addAll(listOf("logcat", "-v", "threadtime", "-v", "year", "-f", file.path, "-r", ROTATE_KB.toString(), "-n", KEPT.toString()))
            if (since != null) addAll(listOf("-T", since))
        }
        process = runCatching {
            ProcessBuilder(command).redirectErrorStream(true).redirectOutput(File(directory, "logcat.err")).start()
        }.getOrNull()
    }

    fun stop() {
        process?.destroy()
        process = null
    }

    fun files(): List<File> = directory.listFiles { f -> f.name.startsWith(FILE) }.orEmpty().sortedByDescending { it.name }

    /** Deletes the copies and goes on with lines from now. */
    fun clear() {
        stop()
        files().forEach(File::delete)
        start(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
    }

    /** Copies started by an earlier process of the app that did not die with it. */
    private fun stopLeftovers() {
        val own = Process.myPid()
        File("/proc").listFiles().orEmpty().mapNotNull { it.name.toIntOrNull() }.filter { it != own }.forEach { pid ->
            val command = runCatching { File("/proc/$pid/cmdline").readText() }.getOrNull() ?: return@forEach
            if (command.startsWith("logcat") && file.path in command) Process.killProcess(pid)
        }
    }

    companion object {
        private const val FILE = "logcat.txt"
        private const val ROTATE_KB = 2048
        private const val KEPT = 7

        /** The time of the last line in [file] (`-v year` format), for logcat's `-T`. */
        internal fun lastTimestamp(file: File): String? {
            if (!file.exists() || file.length() == 0L) return null
            val tail = RandomAccessFile(file, "r").use { input ->
                val start = (input.length() - TAIL_BYTES).coerceAtLeast(0)
                input.seek(start)
                ByteArray((input.length() - start).toInt()).also(input::readFully).decodeToString()
            }
            return tail.lineSequence().mapNotNull { TIMESTAMP.find(it)?.value }.lastOrNull()
        }

        private const val TAIL_BYTES = 8 * 1024L
        private val TIMESTAMP = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}""")
    }
}
