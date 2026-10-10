package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertThrows
import org.junit.Test

class CancellableRunsTest {
    private val runs = CancellableRuns()
    private val id = UUID.randomUUID()
    private val cancelled = mutableSetOf<UUID>()

    private fun cancel(id: UUID) {
        cancelled += id
        runs.cancel(id)
    }

    @Test
    fun `a finished run returns its result`() = runTest {
        assertThat(runs.run(id, { id in cancelled }) { "done" }).isEqualTo("done")
    }

    @Test
    fun `a cancel stops the run and the caller goes on`() = runTest {
        val started = CompletableDeferred<Unit>()
        val run = async {
            runs.run<String>(id, { id in cancelled }) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()

        cancel(id)

        assertThat(run.await()).isNull()
    }

    @Test
    fun `a cancel of another task does not stop the run`() = runTest {
        val release = CompletableDeferred<Unit>()
        val run = async {
            runs.run(id, { id in cancelled }) {
                release.await()
                "done"
            }
        }
        yield()

        cancel(UUID.randomUUID())
        release.complete(Unit)

        assertThat(run.await()).isEqualTo("done")
    }

    @Test
    fun `a task cancelled before its run starts does not run`() = runTest {
        cancelled += id
        var ran = false

        assertThat(runs.run(id, { id in cancelled }) { ran = true }).isNull()
        assertThat(ran).isFalse()
    }

    @Test
    fun `waiting for a cancel includes the running step's cleanup`() = runTest {
        val started = CompletableDeferred<Unit>()
        val cleaning = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val run = async {
            runs.run<String>(id, { id in cancelled }) {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleaning.complete(Unit)
                        release.await()
                    }
                }
            }
        }
        started.await()
        cancelled += id
        val deletion = async { runs.cancelAndJoin(id) }
        cleaning.await()
        assertThat(deletion.isCompleted).isFalse()
        release.complete(Unit)
        deletion.await()
        assertThat(run.await()).isNull()
    }

    @Test
    fun `a cancellation that is not the user's is passed on`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runs.run(id, { id in cancelled }) { throw CancellationException("stopped by the system") }
            }
        }
    }

    @Test
    fun `an error of the run is passed on`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { runs.run(id, { id in cancelled }) { error("broken") } }
        }
    }
}
