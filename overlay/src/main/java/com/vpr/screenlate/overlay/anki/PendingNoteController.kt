package com.vpr.screenlate.overlay.anki

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.vpr.screenlate.core.common.Language
import kotlin.coroutines.CoroutineContext

/** Main-confined lifetime of the entire attachment and note-writing pipeline. */
internal class PendingNoteController(private val scope: CoroutineScope) {
    data class Attachments(val screenshot: Boolean, val force: Boolean, val audio: Boolean?)
    class Token internal constructor()

    class Operation internal constructor(
        override val coroutineContext: CoroutineContext,
        private val onContextRequirement: (Boolean) -> Unit,
        private val choice: CompletableDeferred<Attachments>,
        private val onWrite: () -> Unit,
        private val onSuccess: () -> Unit,
        private val onCompletedUi: (action: () -> Unit) -> Unit,
    ) : CoroutineScope {
        fun requireContext() = onContextRequirement(true)
        fun attachmentsReady() = onContextRequirement(false)
        suspend fun attachments(): Attachments = choice.await()
        fun beginWrite() = onWrite()
        fun succeeded() = onSuccess()
        fun afterCompletion(action: () -> Unit) = onCompletedUi(action)
    }

    private class Running {
        lateinit var job: Job
        var requiresContext = false
        var request: String? = null
        val choice = CompletableDeferred<Attachments>()
        val token = Token()
        var language: Language? = null
        var writing = false
        var succeeded = false
        var afterSuccess: (() -> Unit)? = null
        var afterCancellation: (() -> Unit)? = null
        var completedUi: (() -> Unit)? = null
    }

    private var active: Running? = null
    val isActive: Boolean get() = active != null
    val retainsContext: Boolean get() = active?.requiresContext == true
    val token: Token? get() = active?.token

    fun start(block: suspend Operation.() -> Unit): Boolean = launch(null, null, {}, block)

    fun prepare(
        request: String,
        language: Language? = null,
        onRetired: suspend () -> Unit = {},
        block: suspend Operation.() -> Unit,
    ): Boolean = launch(request, language, onRetired, block)

    fun finishThen(token: Token, action: () -> Unit): Boolean {
        val running = active?.takeIf { it.token === token } ?: return false
        running.afterSuccess = action
        return true
    }

    fun cancelThen(token: Token, action: () -> Unit): Boolean {
        val running = active?.takeIf { it.token === token && !it.writing } ?: return false
        running.afterCancellation = action
        running.job.cancel()
        return true
    }

    fun keepEditing(token: Token) { active?.takeIf { it.token === token }?.afterSuccess = null }

    fun cancelForLanguage(language: Language): Boolean {
        val running = active?.takeIf { it.language != null && it.language != language && !it.writing } ?: return false
        running.afterSuccess = null
        running.afterCancellation = null
        running.job.cancel()
        return true
    }

    fun choose(request: String, choice: Attachments): Boolean =
        active?.takeIf { it.request == request }?.choice?.complete(choice) == true

    fun dismiss(request: String) {
        active?.takeIf { it.request == request && !it.choice.isCompleted }?.job?.cancel()
    }

    private fun launch(request: String?, language: Language?, onRetired: suspend () -> Unit, block: suspend Operation.() -> Unit): Boolean {
        if (active != null) return false
        val running = Running()
        running.request = request
        running.language = language
        running.requiresContext = request != null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            Operation(coroutineContext, { running.requiresContext = it }, running.choice,
                { running.writing = true }, { running.succeeded = true }, { running.completedUi = it }).block()
        }
        running.job = job
        active = running
        job.invokeOnCompletion { cause ->
            // A last child may complete on an IO thread. Retire and run queued UI intent on the owner's dispatcher.
            CoroutineScope(scope.coroutineContext + NonCancellable).launch {
                if (active !== running) return@launch
                // Reserved resources exist even if cancellation prevented the body from starting.
                try { onRetired() } finally { active = null }
                if (!scope.isActive) return@launch
                when {
                    cause == null && running.succeeded -> {
                        running.completedUi?.invoke()
                        if (scope.isActive) running.afterSuccess?.invoke()
                    }
                    cause != null -> running.afterCancellation?.invoke()
                }
            }
        }
        job.start()
        return true
    }

    /** Keep ownership until cancellation has unwound editors and released their resources. */
    fun cancel() {
        active?.let { it.afterSuccess = null; it.afterCancellation = null; it.job.cancel() }
    }

    fun contextEnded() {
        active?.let {
            it.afterSuccess = null
            it.afterCancellation = null
            if (it.requiresContext) it.job.cancel()
        }
    }
}
