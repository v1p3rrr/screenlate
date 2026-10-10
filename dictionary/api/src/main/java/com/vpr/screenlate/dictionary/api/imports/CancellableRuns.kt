package com.vpr.screenlate.dictionary.api.imports

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Runs that can be stopped one by one by their task id, without stopping the caller. */
internal class CancellableRuns {
    private val jobs = ConcurrentHashMap<UUID, Job>()

    /** Stops the run of [id] if one is going on; a run that starts later checks its own cancel. */
    fun cancel(id: UUID) {
        jobs[id]?.cancel()
    }

    /** Stops a running task and waits until it has released its files. */
    suspend fun cancelAndJoin(id: UUID) {
        jobs[id]?.cancelAndJoin()
    }

    /**
     * Runs [block] for the task [id]; returns null when [cancel] stopped it or [isCancelled] already says so. A
     * cancellation of the caller is passed on, and so is one the block raised without being cancelled.
     */
    suspend fun <T> run(id: UUID, isCancelled: suspend () -> Boolean, block: suspend () -> T): T? = coroutineScope {
        val job = async { block() }
        jobs[id] = job
        try {
            // A cancel that came before the job was listed.
            if (isCancelled()) job.cancel()
            job.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (!isCancelled()) throw e
            null
        } finally {
            jobs.remove(id, job)
        }
    }
}
