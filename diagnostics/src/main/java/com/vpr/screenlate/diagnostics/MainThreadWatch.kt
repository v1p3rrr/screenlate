package com.vpr.screenlate.diagnostics

import android.os.SystemClock
import android.util.Printer

/**
 * Times every message of the main thread through the looper's message log (`>>>>> Dispatching to …` before a
 * message, `<<<<< Finished to …` after it) and reports those that held the thread for [thresholdMs] or longer, with
 * the handler and callback that ran. The bubble, the popup and the app's screens all draw on this thread, so a long
 * message is a frozen overlay.
 */
internal class MainThreadWatch(
    private val thresholdMs: Long,
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val onSlow: (durationMs: Long, what: String) -> Unit,
) : Printer {
    private var started = 0L
    private var what: String? = null

    override fun println(line: String) {
        when {
            line.startsWith(DISPATCH) -> {
                started = clock()
                what = line
            }
            line.startsWith(FINISHED) -> {
                val dispatched = what ?: return
                what = null
                val duration = clock() - started
                if (duration >= thresholdMs) onSlow(duration, describe(dispatched))
            }
        }
    }

    companion object {
        private const val DISPATCH = ">>>>> Dispatching to "
        private const val FINISHED = "<<<<< Finished to "
        private val HASH = Regex("""\s*\{[0-9a-f]+\}|@[0-9a-f]+""")
        private val HANDLER = Regex("""^Handler \(([^)]*)\)""")

        /** `Handler (android.view.Choreographer$FrameHandler) {1a2b} …Receiver@3c4d: 0` without the hashes. */
        internal fun describe(line: String): String {
            val rest = line.removePrefix(DISPATCH).replace(HASH, "")
            return HANDLER.replace(rest) { it.groupValues[1] }.trim()
        }
    }
}
