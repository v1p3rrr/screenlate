package com.vpr.screenlate.core.common.log

import com.vpr.screenlate.core.common.redacted

/** The process's [LogFile]; lines logged before [install] reach logcat only. */
object AppLog {
    @Volatile
    var file: LogFile? = null
        private set

    /**
     * Starts writing [Log] lines to [file] and records uncaught exceptions there before the previous handler ends the
     * process. Crash messages are left out: they may carry text (see [redacted]).
     */
    fun install(file: LogFile) {
        this.file = file
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeNow('E', CRASH_TAG, "Uncaught exception in thread ${thread.name}", error.redacted()) }
            previous?.uncaughtException(thread, error)
        }
    }

    internal fun write(level: Char, tag: String, message: String, error: Throwable?) {
        file?.append(level, tag, message, error)
    }

    private const val CRASH_TAG = "Crash"
}
