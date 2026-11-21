package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingNoteControllerTest {
    @Test
    fun `context closure cancels audio preparation but preserves ordinary note writes`() = runTest {
        val pending = PendingNoteController(this)
        val done = CompletableDeferred<Unit>()
        var writes = 0
        pending.start { done.await(); writes++ }
        runCurrent()
        pending.contextEnded()
        done.complete(Unit)
        runCurrent()
        assertThat(writes).isEqualTo(1)
        val audio = CompletableDeferred<Unit>()
        pending.start { requireContext(); audio.await(); writes++ }
        runCurrent()
        pending.contextEnded()
        runCurrent()
        audio.complete(Unit)
        runCurrent()
        assertThat(writes).isEqualTo(1)
        assertThat(pending.isActive).isFalse()
        val writing = CompletableDeferred<Unit>()
        pending.start { requireContext(); attachmentsReady(); writing.await(); writes++ }
        runCurrent()
        pending.contextEnded()
        writing.complete(Unit)
        runCurrent()
        assertThat(writes).isEqualTo(2)
    }
    @Test
    fun `one pending note owns the attachment pipeline before its first suspension`() = runTest {
        val pending = PendingNoteController(this)
        val wait = CompletableDeferred<Unit>()
        var writes = 0
        assertThat(pending.start { wait.await(); writes++ }).isTrue()
        assertThat(pending.start { writes++ }).isFalse()
        runCurrent()
        assertThat(writes).isEqualTo(0)
        wait.complete(Unit)
        runCurrent()
        assertThat(writes).isEqualTo(1)
        assertThat(pending.isActive).isFalse()
    }

    @Test
    fun `cancellation retains ownership until old cleanup finishes`() = runTest {
        val pending = PendingNoteController(this)
        val oldCleanup = CompletableDeferred<Unit>()
        val nextDone = CompletableDeferred<Unit>()
        pending.start { withContext(NonCancellable) { oldCleanup.await() } }
        runCurrent()
        pending.cancel()
        assertThat(pending.start { nextDone.await() }).isFalse()
        runCurrent()
        oldCleanup.complete(Unit)
        runCurrent()
        assertThat(pending.isActive).isFalse()
        assertThat(pending.start { nextDone.await() }).isTrue()
        runCurrent()
        nextDone.complete(Unit)
        runCurrent()
        assertThat(pending.isActive).isFalse()
    }
}
