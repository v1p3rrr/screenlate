package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingNoteControllerTest {
    @Test
    fun `finish then scan runs only after a successful note has released its resources`() = runTest {
        val pending = PendingNoteController(this)
        val done = CompletableDeferred<Unit>()
        var resources = true
        var scanned = false
        pending.start {
            try { done.await(); succeeded(); afterCompletion { pending.contextEnded() } }
            finally { resources = false }
        }
        runCurrent()
        pending.finishThen(checkNotNull(pending.token)) { check(!resources); scanned = true }
        assertThat(scanned).isFalse()
        done.complete(Unit)
        runCurrent()
        assertThat(scanned).isTrue()
        val failed = CompletableDeferred<Unit>()
        pending.start { failed.await() }
        pending.finishThen(checkNotNull(pending.token)) { error("Failed note must not scan") }
        failed.complete(Unit)
        runCurrent()
        assertThat(pending.isActive).isFalse()
    }

    @Test
    fun `discard then scan waits for noncancellable cleanup and cannot run after host shutdown`() = runTest {
        val pending = PendingNoteController(this)
        val cleanup = CompletableDeferred<Unit>()
        var scanned = false
        pending.start { try { CompletableDeferred<Unit>().await() } finally { withContext(NonCancellable) { cleanup.await() } } }
        runCurrent()
        pending.cancelThen(checkNotNull(pending.token)) { scanned = true }
        runCurrent()
        assertThat(scanned).isFalse()
        assertThat(pending.isActive).isTrue()
        cleanup.complete(Unit)
        runCurrent()
        assertThat(scanned).isTrue()
        val hostJob = Job()
        val host = PendingNoteController(CoroutineScope(coroutineContext + hostJob))
        val release = CompletableDeferred<Unit>()
        var lateScan = false
        host.start { try { CompletableDeferred<Unit>().await() } finally { withContext(NonCancellable) { release.await() } } }
        runCurrent()
        host.cancelThen(checkNotNull(host.token)) { lateScan = true }
        runCurrent()
        hostJob.cancel()
        release.complete(Unit)
        runCurrent()
        assertThat(lateScan).isFalse()
    }

    @Test
    fun `a stale scan choice cannot cancel the next note`() = runTest {
        val pending = PendingNoteController(this)
        pending.start { }
        val old = checkNotNull(pending.token)
        runCurrent()
        val done = CompletableDeferred<Unit>()
        pending.start { done.await() }
        runCurrent()
        assertThat(pending.cancelThen(old) { error("Stale scan") }).isFalse()
        assertThat(pending.finishThen(old) { error("Stale scan") }).isFalse()
        assertThat(pending.isActive).isTrue()
        done.complete(Unit)
        runCurrent()
    }

    @Test
    fun `active language changes cancel preparation but preserve an attempted write acknowledgement`() = runTest {
        val pending = PendingNoteController(this)
        pending.prepare("a", Language.JAPANESE) { attachments() }
        runCurrent()
        assertThat(pending.cancelForLanguage(Language.JAPANESE)).isFalse()
        assertThat(pending.cancelForLanguage(Language.ENGLISH)).isTrue()
        runCurrent()
        assertThat(pending.isActive).isFalse()
        val write = CompletableDeferred<Unit>()
        pending.prepare("b", Language.JAPANESE) { beginWrite(); write.await() }
        runCurrent()
        assertThat(pending.cancelForLanguage(Language.ENGLISH)).isFalse()
        write.complete(Unit)
        runCurrent()
    }
    @Test
    fun `dismissal ignores other menus and releases this menu without a note`() = runTest {
        val pending = PendingNoteController(this)
        var written = false
        pending.prepare("menu-a") { attachments(); written = true }
        runCurrent()
        pending.dismiss("old")
        assertThat(pending.isActive).isTrue()
        pending.dismiss("menu-a")
        runCurrent()
        assertThat(pending.isActive).isFalse()
        assertThat(written).isFalse()
        assertThat(pending.choose("menu-a", PendingNoteController.Attachments(true, false, true))).isFalse()
    }
    @Test
    fun `attachment menu reserves its operation and accepts only its own first choice`() = runTest {
        val pending = PendingNoteController(this)
        val selections = mutableListOf<PendingNoteController.Attachments>()
        pending.prepare("menu-a") { selections += attachments() }
        runCurrent()
        assertThat(pending.isActive).isTrue()
        assertThat(pending.choose("menu-old", PendingNoteController.Attachments(true, false, true))).isFalse()
        assertThat(selections).isEmpty()
        assertThat(pending.choose("menu-a", PendingNoteController.Attachments(false, true, true))).isTrue()
        assertThat(pending.choose("menu-a", PendingNoteController.Attachments(true, false, false))).isFalse()
        runCurrent()
        assertThat(selections).containsExactly(PendingNoteController.Attachments(false, true, true))
        assertThat(pending.isActive).isFalse()
    }
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
