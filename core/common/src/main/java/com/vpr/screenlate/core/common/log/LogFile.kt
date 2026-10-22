package com.vpr.screenlate.core.common.log

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The app's own log in [directory], kept across process deaths: [CURRENT] takes new lines and becomes [PREVIOUS] once
 * it reaches [maxFileBytes], so at most about twice that is kept. Lines are written in call order on one background
 * thread, each straight to the file, so a process that is killed loses only lines still queued.
 */
class LogFile(
    private val directory: File,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val processId: Int = android.os.Process.myPid(),
) {
    private val lock = Any()

    @Volatile
    private var writerThread: Thread? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, THREAD_NAME).apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            writerThread = this
        }
    }
    private var output: OutputStream? = null
    private var size = 0L
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Queues a line; [error]'s stack trace follows it as logcat prints one. */
    fun append(level: Char, tag: String, message: String, error: Throwable? = null) {
        val text = format(level, tag, message, error)
        try {
            executor.execute { write(text) }
        } catch (_: RuntimeException) {
            // Rejected after a shutdown; nothing is lost that logcat does not have.
        }
    }

    /** Writes a line on the calling thread after the queued ones, for a process that is about to end. */
    fun writeNow(level: Char, tag: String, message: String, error: Throwable? = null) {
        val text = format(level, tag, message, error)
        if (Thread.currentThread() !== writerThread) flush(CRASH_FLUSH_MS)
        write(text)
    }

    /** Waits up to [timeoutMs] for the queued lines to reach the file. */
    fun flush(timeoutMs: Long = FLUSH_MS) {
        runCatching { executor.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS) }
    }

    /** Copies the whole log, oldest line first, after the queued lines are written. */
    fun copyTo(out: OutputStream) {
        flush()
        synchronized(lock) {
            for (name in listOf(PREVIOUS, CURRENT)) {
                val file = File(directory, name)
                if (file.exists()) file.inputStream().use { it.copyTo(out) }
            }
        }
    }

    internal fun format(level: Char, tag: String, message: String, error: Throwable?): String {
        val stamp = time.format(Instant.ofEpochMilli(clock()).atZone(zone))
        val prefix = String.format(Locale.US, "%s %5d %5d %c %s: ", stamp, processId, android.os.Process.myTid(), level, tag)
        val body = if (error == null) message else message + "\n" + error.stackTraceToString().trimEnd()
        return buildString { body.lineSequence().forEach { append(prefix).append(it).append('\n') } }
    }

    private fun write(text: String) = synchronized(lock) {
        try {
            val out = output ?: open()
            val bytes = text.toByteArray()
            out.write(bytes)
            size += bytes.size
            if (size >= maxFileBytes) rotate()
        } catch (e: IOException) {
            // A full disk drops the line; the next one opens the file again.
            runCatching { output?.close() }
            output = null
            android.util.Log.w(TAG, "Cannot write the log file: ${e.javaClass.simpleName}")
        }
    }

    private fun open(): OutputStream {
        directory.mkdirs()
        val file = File(directory, CURRENT)
        size = file.length()
        return FileOutputStream(file, true).also { output = it }
    }

    private fun rotate() {
        output?.close()
        output = null
        val previous = File(directory, PREVIOUS)
        previous.delete()
        File(directory, CURRENT).renameTo(previous)
        size = 0
    }

    companion object {
        const val CURRENT = "screenlate.log"
        const val PREVIOUS = "screenlate.1.log"
        const val DEFAULT_MAX_FILE_BYTES = 4L * 1024 * 1024
        private const val THREAD_NAME = "AppLog"
        private const val TAG = "AppLog"
        private const val FLUSH_MS = 2_000L
        private const val CRASH_FLUSH_MS = 1_000L
    }
}
