package com.vpr.screenlate.core.ocr.paddle

import kotlinx.coroutines.sync.Mutex

/**
 * Runs [close] under [mutex] when [request]ed: at once when the mutex is free, else once its holder lets it go and
 * calls [afterHold]. A release then waits for neither a scan nor a trim, and is not lost behind them.
 */
internal class DeferredRelease(private val mutex: Mutex, private val close: () -> Unit) {
    @Volatile
    private var pending = false

    fun request() {
        pending = true
        afterHold()
    }

    /** Call after unlocking the mutex. */
    fun afterHold() {
        // A request that failed to lock set pending before the holder unlocked, so the holder sees it here.
        if (!pending || !mutex.tryLock()) return
        try {
            if (pending) {
                pending = false
                close()
            }
        } finally {
            mutex.unlock()
        }
    }
}
