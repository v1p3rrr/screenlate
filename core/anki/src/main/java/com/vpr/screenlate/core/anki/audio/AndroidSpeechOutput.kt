package com.vpr.screenlate.core.anki.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** A bounded native lane keeps engine IPC off Main and prevents a late queued Speak from passing Stop. */
internal class AndroidSpeechOutput(private val context: Context) : SpeechOutput {
    private val lane = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(2),
        { task -> Thread(task, "pronunciation-tts").apply { isDaemon = true } })
    private val control = Any()
    @Volatile private var engine: TextToSpeech? = null
    private var initialized: CompletableDeferred<Boolean>? = null
    private var stopping: CompletableDeferred<Boolean>? = null
    @Volatile private var stopFailed = false
    private val utterance = AtomicReference<Utterance?>()

    override suspend fun prepare(language: Language, canStart: () -> Boolean): Boolean {
        synchronized(control) { stopping = null }
        val ready = synchronized(control) {
            initialized ?: CompletableDeferred<Boolean>().also { started ->
                initialized = started
                try {
                    lane.execute {
                        try {
                            engine = TextToSpeech(context) { status -> started.complete(status == TextToSpeech.SUCCESS) }
                            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) = Unit
                                override fun onDone(utteranceId: String?) = finish(utteranceId, true)
                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) = finish(utteranceId, false)
                                override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)
                                override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, false)
                            })
                        } catch (_: Exception) { started.complete(false) }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) { started.complete(false) }
            }
        }
        if (!ready.await()) {
            native {
                engine?.shutdown()
                engine = null
                synchronized(control) { if (initialized === ready) initialized = null }
                false
            }
            return false
        }
        return native {
            if (!canStart()) return@native false
            val tts = engine ?: return@native false
            val tags = listOf(language.support.speechTag) + language.support.otherSpeechTags
            val available = tags.any { tts.setLanguage(Locale.forLanguageTag(it)) >= TextToSpeech.LANG_AVAILABLE }
            if (available) tts.setAudioAttributes(speechAudioAttributes())
            available
        }
    }

    override suspend fun synthesize(text: String, file: File, canStart: () -> Boolean): Boolean {
        val result = CompletableDeferred<Boolean>()
        val request = Utterance(UUID.randomUUID().toString()) { result.complete(it) }
        utterance.set(request)
        val accepted = native {
            if (!canStart() || text.length > TextToSpeech.getMaxSpeechInputLength()) return@native false
            (engine?.synthesizeToFile(text, Bundle(), file, request.id) == TextToSpeech.SUCCESS)
                .also { request.submitted.set(it) }
        }
        if (!accepted) finish(request.id, false)
        return result.await()
    }

    override suspend fun speak(text: String, volume: Float, canStart: () -> Boolean, finished: () -> Unit): Boolean {
        val request = Utterance(UUID.randomUUID().toString()) { finished() }
        utterance.set(request)
        val accepted = native {
            if (!canStart() || text.length > TextToSpeech.getMaxSpeechInputLength()) return@native false
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
            (engine?.speak(text, TextToSpeech.QUEUE_FLUSH, params, request.id) == TextToSpeech.SUCCESS)
                .also { request.submitted.set(it) }
        }
        if (!accepted) finish(request.id, false)
        return accepted
    }

    override suspend fun stop(): Boolean {
        val pending = synchronized(control) {
            if (stopFailed) { stopping = null; stopFailed = false }
            stopping ?: CompletableDeferred<Boolean>().also { result ->
                stopping = result
                val request = utterance.get()
                try {
                    lane.execute {
                        val stopped = runCatching { engine?.stop() != TextToSpeech.ERROR }.getOrDefault(false)
                        if (!stopped) { stopFailed = true; result.complete(false) }
                        else if (request == null) result.complete(true)
                        else {
                            if (!request.submitted.get()) finish(request.id, false)
                            request.terminal.invokeOnCompletion { result.complete(true) }
                        }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) { stopFailed = true; result.complete(false) }
            }
        }
        // Timeout does not cancel queued/in-flight Stop or admit another engine around it.
        return withTimeoutOrNull(2000) { pending.await() } == true
    }

    private fun finish(id: String?, success: Boolean) {
        val request = utterance.get()?.takeIf { it.id == id } ?: return
        if (utterance.compareAndSet(request, null)) {
            request.complete(success)
            request.terminal.complete(Unit)
        }
    }

    private suspend fun native(action: () -> Boolean): Boolean = suspendCancellableCoroutine { continuation ->
        val task = FutureTask {
            if (continuation.isActive) {
                try { continuation.resume(action()) }
                catch (error: Exception) { continuation.resumeWithException(error) }
            }
        }
        continuation.invokeOnCancellation { task.cancel(false); lane.remove(task) }
        try { lane.execute(task) }
        catch (_: java.util.concurrent.RejectedExecutionException) { continuation.resume(false) }
    }

    private class Utterance(val id: String, val complete: (Boolean) -> Unit) {
        val submitted = AtomicBoolean()
        val terminal = CompletableDeferred<Unit>()
    }
}
