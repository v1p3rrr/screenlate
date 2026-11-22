package com.vpr.screenlate.overlay.anki

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Main-confined lifetime of the entire attachment and note-writing pipeline. */
internal class PendingNoteController(private val scope: CoroutineScope) {
    data class Attachments(val screenshot: Boolean, val force: Boolean, val audio: Boolean?)

    class Operation internal constructor(
        override val coroutineContext: CoroutineContext,
        private val onContextRequirement: (Boolean) -> Unit,
        private val choice: CompletableDeferred<Attachments>,
    ) : CoroutineScope {
        fun requireContext() = onContextRequirement(true)
        fun attachmentsReady() = onContextRequirement(false)
        suspend fun attachments(): Attachments = choice.await()
    }

    private class Running {
        lateinit var job: Job
        var requiresContext = false
        var request: String? = null
        val choice = CompletableDeferred<Attachments>()
    }

    private var active: Running? = null
    val isActive: Boolean get() = active != null
    val retainsContext: Boolean get() = active?.requiresContext == true

    fun start(block: suspend Operation.() -> Unit): Boolean = launch(null, block)

    fun prepare(request: String, block: suspend Operation.() -> Unit): Boolean = launch(request, block)

    fun choose(request: String, choice: Attachments): Boolean =
        active?.takeIf { it.request == request }?.choice?.complete(choice) == true

    fun dismiss(request: String) {
        active?.takeIf { it.request == request && !it.choice.isCompleted }?.job?.cancel()
    }

    private fun launch(request: String?, block: suspend Operation.() -> Unit): Boolean {
        if (active != null) return false
        val running = Running()
        running.request = request
        running.requiresContext = request != null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { Operation(coroutineContext, { running.requiresContext = it }, running.choice).block() } finally {
                if (active === running) active = null
            }
        }
        running.job = job
        active = running
        job.invokeOnCompletion { if (active === running) active = null }
        job.start()
        return true
    }

    /** Keep ownership until cancellation has unwound editors and released their resources. */
    fun cancel() { active?.job?.cancel() }

    fun contextEnded() { active?.takeIf { it.requiresContext }?.job?.cancel() }
}
