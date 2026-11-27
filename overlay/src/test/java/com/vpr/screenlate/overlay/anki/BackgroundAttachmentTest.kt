package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.fail
import org.junit.Test

class BackgroundAttachmentTest {

    @Test
    fun failingAttachmentDoesNotCancelParentOrSiblingAndRetriesUntilSuccessThenMemoizes() = runBlocking {
        val attempts = AtomicInteger(0)
        val initialFailed = CompletableDeferred<Unit>()
        val siblingStarted = CompletableDeferred<Unit>()
        val releaseSibling = CompletableDeferred<Unit>()
        var siblingFinished = false

        coroutineScope {
            val sibling = launch {
                siblingStarted.complete(Unit)
                releaseSibling.await()
                siblingFinished = true
            }
            val attachment = BackgroundAttachment(this) {
                if (attempts.incrementAndGet() == 1) {
                    initialFailed.complete(Unit)
                    throw IOException("disk full")
                }
                "ready"
            }

            siblingStarted.await()
            initialFailed.await()
            delay(20)
            assertThat(coroutineContext[Job]!!.isCancelled).isFalse()
            assertThat(sibling.isCancelled).isFalse()

            try {
                attachment.await()
                fail("Expected IOException on first await")
            } catch (_: IOException) {
            }

            releaseSibling.complete(Unit)
            sibling.join()
            assertThat(siblingFinished).isTrue()

            assertThat(attachment.await()).isEqualTo("ready")
            assertThat(attachment.await()).isEqualTo("ready")
            assertThat(attempts.get()).isEqualTo(2)
        }
    }

    @Test
    fun cancellationPropagatesWithoutCachingAndCancelAndJoinWaitsForCleanup() = runBlocking {
        try {
            runCatchingNonCancel { throw CancellationException("cancelled") }
            fail("Expected CancellationException to rethrow")
        } catch (_: CancellationException) {
        }

        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var cleanedUp = false
        val attachment = BackgroundAttachment(this) {
            started.complete(Unit)
            try {
                gate.await()
                "done"
            } finally {
                withContext(NonCancellable) {
                    delay(20)
                    cleanedUp = true
                }
            }
        }
        started.await()
        attachment.cancelAndJoin()
        assertThat(cleanedUp).isTrue()
    }
}
