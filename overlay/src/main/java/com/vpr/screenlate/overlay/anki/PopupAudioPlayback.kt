package com.vpr.screenlate.overlay.anki

import com.vpr.screenlate.core.anki.audio.AudioClip
import com.vpr.screenlate.core.anki.audio.Pronunciation
import com.vpr.screenlate.core.ocr.TextPosition
import com.vpr.screenlate.dictionary.api.model.LookupResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A playback request belongs to the popup word that was shown when it started. */
internal class PopupAudioPlayback(
    private val scope: CoroutineScope,
    private val isShowing: () -> Boolean,
    private val renderedTerms: suspend () -> List<Pair<String, String>>?,
    private val play: suspend (Pronunciation, canStart: () -> Boolean) -> Boolean,
    private val setFailure: (Failure?) -> Unit,
) {
    enum class Failure { NO_AUDIO, NO_VOICE }

    private class Request(val term: Pair<String, String>) {
        var job: Job? = null
    }

    private var shownWord: Pair<TextPosition, Pair<String, String>>? = null
    private var activeRequest: Request? = null

    fun onResultsShown(results: List<LookupResult>, position: TextPosition?) {
        val first = results.firstOrNull()?.term
        val word = if (position != null && first != null) position to (first.expression to first.reading) else null
        val requested = activeRequest?.term
        val entryStillShown = requested == null || results.any {
            it.term.expression == requested.first && it.term.reading == requested.second
        }
        if (word == null || word != shownWord || !entryStillShown) cancelRequest()
        shownWord = word
    }

    fun onResultsHidden() {
        cancelRequest()
        shownWord = null
    }

    fun request(
        term: Pair<String, String>,
        rememberClip: (AudioClip) -> Unit = {},
        resolve: suspend () -> Pronunciation?,
    ) {
        if (!isShowing()) return
        cancelRequest()
        val next = Request(term)
        activeRequest = next
        val canStart = { activeRequest === next && isShowing() }
        next.job = scope.launch {
            val pronunciation = resolve()
            if (!canStart()) return@launch
            // The rendered page may have changed while a button event or audio request was in flight.
            if (term !in renderedTerms().orEmpty() || !canStart()) return@launch
            if (pronunciation == null) {
                setFailure(Failure.NO_AUDIO)
                return@launch
            }
            if (pronunciation is Pronunciation.Clip) rememberClip(pronunciation.clip)
            if (!play(pronunciation, canStart) && canStart() && pronunciation is Pronunciation.Speech) {
                setFailure(Failure.NO_VOICE)
            }
        }
    }

    private fun cancelRequest() {
        val previous = activeRequest
        activeRequest = null
        previous?.job?.cancel()
        setFailure(null)
    }
}
