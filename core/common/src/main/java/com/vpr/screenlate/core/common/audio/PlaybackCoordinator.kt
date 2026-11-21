package com.vpr.screenlate.core.common.audio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes Screenlate outputs only. A failed shutdown retains ownership and declines replacement. */
@Singleton
class PlaybackCoordinator @Inject constructor() {
    private val commands = Mutex()
    private val current = AtomicReference<Lease?>()
    private val captures = AtomicInteger()

    val requiresPrivateSpeech: Boolean get() = captures.get() > 0

    suspend fun acquire(stop: suspend () -> Boolean): Lease? = commands.withLock {
        if (!retireCurrent()) return@withLock null
        currentCoroutineContext().ensureActive()
        Lease(stop).also { current.set(it) }
    }

    /** Hold before requesting consent; transfer protection to actual input lifetime before releasing it. */
    suspend fun beginCapture(): CaptureLease? {
        val hold = CaptureLease().also { captures.incrementAndGet() }
        try {
            return commands.withLock {
                if (!retireCurrent()) { hold.close(); return@withLock null }
                currentCoroutineContext().ensureActive()
                hold
            }
        } catch (error: Throwable) {
            hold.close()
            throw error
        }
    }

    private suspend fun retireCurrent(): Boolean {
        val old = current.get() ?: return true
        old.valid.set(false)
        val stopped = withContext(NonCancellable) { runCatching { old.stop() }.getOrDefault(false) }
        if (stopped) current.compareAndSet(old, null)
        return stopped
    }

    /** Close only after native output releases; invalidation alone does not prove that it has stopped. */
    inner class Lease internal constructor(internal val stop: suspend () -> Boolean) : AutoCloseable {
        internal val valid = AtomicBoolean(true)
        val isCurrent: Boolean get() = valid.get() && current.get() === this
        override fun close() {
            valid.set(false)
            current.compareAndSet(this, null)
        }
    }

    inner class CaptureLease internal constructor() : AutoCloseable {
        private val released = AtomicBoolean()
        override fun close() { if (released.compareAndSet(false, true)) captures.decrementAndGet() }
    }
}
