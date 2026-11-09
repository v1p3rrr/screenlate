package com.vpr.screenlate.overlay.anki

import com.vpr.screenlate.core.anki.audio.AudioCandidate
import com.vpr.screenlate.core.common.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray

/** Keeps the lists requested for one popup view, independent of whether its menu is open. */
internal class PopupAudioMenu(
    private val scope: CoroutineScope,
    private val load: suspend (expression: String, reading: String, language: Language) -> Clips,
    private val show: (request: Int, items: JsonArray, loading: Boolean) -> Unit,
) {
    data class Clips(val candidates: List<AudioCandidate>, val items: JsonArray)
    data class Choice(val term: Pair<String, String>, val candidate: AudioCandidate)

    private data class Entry(val expression: String, val reading: String, val language: Language)
    private data class Offer(val request: Int, val entry: Entry, val clips: Clips)

    private var view: Int? = null
    private val cache = mutableMapOf<Entry, Clips>()
    private var job: Job? = null
    private var offer: Offer? = null

    fun open(view: Int, request: Int, expression: String, reading: String, language: Language) {
        if (this.view != view) {
            clear()
            this.view = view
        }
        job?.cancel()
        val entry = Entry(expression, reading, language)
        val cached = cache[entry]
        if (cached != null) {
            present(request, entry, cached)
            return
        }
        offer = null
        show(request, JsonArray(emptyList()), true)
        job = scope.launch {
            val clips = load(expression, reading, language)
            ensureActive()
            cache[entry] = clips
            present(request, entry, clips)
        }
    }

    private fun present(request: Int, entry: Entry, clips: Clips) {
        offer = Offer(request, entry, clips)
        show(request, clips.items, false)
    }

    fun choose(request: Int, id: String): Choice? {
        val shown = offer?.takeIf { it.request == request } ?: return null
        val candidate = shown.clips.candidates.firstOrNull { it.id == id } ?: return null
        return Choice(shown.entry.expression to shown.entry.reading, candidate)
    }

    fun clear() {
        job?.cancel()
        job = null
        view = null
        cache.clear()
        offer = null
    }
}
