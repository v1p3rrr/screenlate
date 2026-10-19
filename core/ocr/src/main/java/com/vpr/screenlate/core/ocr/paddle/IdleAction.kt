package com.vpr.screenlate.core.ocr.paddle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Runs [action] in [scope] once [delayMs] ms pass after the last [schedule]; [cancel] drops a run that is pending. */
internal class IdleAction(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val action: suspend () -> Unit,
) {
    private var pending: Job? = null

    @Synchronized
    fun schedule() {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            action()
        }
    }

    @Synchronized
    fun cancel() {
        pending?.cancel()
        pending = null
    }
}
