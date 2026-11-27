package com.vpr.screenlate.overlay.anki

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Starts [compute] eagerly without failing the parent [scope] on non-cancellation exceptions, memoizing the first
 * successful result and retrying only after a failed initial attempt.
 */
internal class BackgroundAttachment<T>(
    scope: CoroutineScope,
    context: CoroutineContext = EmptyCoroutineContext,
    private val compute: suspend () -> T,
) {
    private val mutex = Mutex()
    private var resolved = false
    private var cached: T? = null
    private var initialConsumed = false
    private val deferred: Deferred<Result<T>> = scope.async(context) {
        runCatchingNonCancel { compute() }
    }

    suspend fun await(): T = mutex.withLock {
        if (resolved) {
            @Suppress("UNCHECKED_CAST")
            return@withLock cached as T
        }
        val result = if (!initialConsumed) {
            initialConsumed = true
            deferred.await()
        } else {
            runCatchingNonCancel { compute() }
        }
        val value = result.getOrThrow()
        cached = value
        resolved = true
        value
    }

    fun cancel() {
        deferred.cancel()
    }

    suspend fun cancelAndJoin() {
        deferred.cancelAndJoin()
    }
}

internal suspend fun <T> runCatchingNonCancel(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
