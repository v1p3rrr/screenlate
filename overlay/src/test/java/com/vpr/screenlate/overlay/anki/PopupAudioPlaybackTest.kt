package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.audio.AudioClip
import com.vpr.screenlate.core.anki.audio.Pronunciation
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.TextPosition
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.TermEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PopupAudioPlaybackTest {
    private val cat = "cat" to ""
    private val dog = "dog" to ""
    private val clip = Pronunciation.Clip(AudioClip(File("fixture.ogg"), "https://example.test/fixture.ogg", "ogg"))
    private val voice = Pronunciation.Speech("cat", Language.ENGLISH)

    private class View(scope: CoroutineScope) {
        var visible = true
        var rendered = emptyList<Pair<String, String>>()
        var readRendered: suspend () -> List<Pair<String, String>> = { rendered }
        var failure: PopupAudioPlayback.Failure? = null
        val failures = mutableListOf<PopupAudioPlayback.Failure>()
        val remembered = mutableListOf<AudioClip>()
        val prepared = mutableListOf<() -> Boolean>()
        var prepare: suspend (Pronunciation) -> Boolean = { true }

        val playback = PopupAudioPlayback(scope, { visible }, { readRendered() }, { pronunciation, canStart ->
            prepared += canStart
            prepare(pronunciation)
        }) { next ->
            failure = next
            if (next != null) failures += next
        }

        fun show(vararg terms: Pair<String, String>, at: TextPosition? = TextPosition(0, 0)) {
            rendered = terms.toList()
            playback.onResultsShown(rendered.map { (word, reading) ->
                LookupResult(word, word, term = TermEntry(word, reading))
            }, at)
        }

        fun hide() {
            visible = false
            rendered = emptyList()
            playback.onResultsHidden()
        }
    }

    @Test
    fun `a current missing recording shows a failure that is dismissed on leaving the word`() = runTest {
        val view = View(this)
        view.show(cat)
        view.playback.request(cat) { null }
        advanceUntilIdle()
        assertThat(view.failure).isEqualTo(PopupAudioPlayback.Failure.NO_AUDIO)

        view.show(dog)
        assertThat(view.failure).isNull()
    }

    @Test
    fun `an unanswered lookup cannot show a failure after changing the word and coming back`() = runTest {
        val view = View(this)
        val reply = CompletableDeferred<Unit>()
        view.show(cat)
        view.playback.request(cat) {
            withContext(NonCancellable) { reply.await() }
            null
        }
        runCurrent()
        view.show(dog)
        view.show(cat)
        reply.complete(Unit)
        advanceUntilIdle()

        assertThat(view.failures).isEmpty()
        assertThat(view.prepared).isEmpty()
    }

    @Test
    fun `closing and reopening the same word drops a delayed selected recording`() = runTest {
        val view = View(this)
        val reply = CompletableDeferred<Unit>()
        view.show(cat)
        view.playback.request(cat, rememberClip = { view.remembered += it }) {
            withContext(NonCancellable) { reply.await() }
            clip
        }
        runCurrent()
        view.hide()
        view.visible = true
        view.show(cat)
        reply.complete(Unit)
        advanceUntilIdle()
        assertThat(view.remembered).isEmpty()
        assertThat(view.prepared).isEmpty()

        view.playback.request(cat, rememberClip = { view.remembered += it }) { clip }
        advanceUntilIdle()
        assertThat(view.remembered).containsExactly(clip.clip)
        assertThat(view.prepared.single()()).isTrue()
    }

    @Test
    fun `same word redraw keeps playback eligible until the selected entry disappears`() = runTest {
        val view = View(this)
        view.show(cat, dog)
        view.playback.request(dog) { clip }
        advanceUntilIdle()
        val canStart = view.prepared.single()
        assertThat(canStart()).isTrue()

        view.show(cat, dog)
        assertThat(canStart()).isTrue()
        view.show(cat)
        assertThat(canStart()).isFalse()
    }

    @Test
    fun `preparation cannot start playback at another occurrence of the same word`() = runTest {
        val view = View(this)
        view.show(cat, at = TextPosition(0, 2))
        view.playback.request(cat) { clip }
        advanceUntilIdle()
        val canStart = view.prepared.single()
        assertThat(canStart()).isTrue()

        view.show(cat, at = TextPosition(0, 8))
        assertThat(canStart()).isFalse()
    }

    @Test
    fun `navigation and closing invalidate the guard even after preparation was submitted`() = runTest {
        val view = View(this)
        view.show(cat)
        view.playback.request(cat) { clip }
        advanceUntilIdle()
        val root = view.prepared.single()

        view.show(cat, at = null)
        assertThat(root()).isFalse()
        view.playback.request(cat) { clip }
        advanceUntilIdle()
        val linked = view.prepared.last()
        assertThat(linked()).isTrue()
        view.hide()
        assertThat(linked()).isFalse()
    }

    @Test
    fun `an older speech failure is dropped while a current one is shown`() = runTest {
        val view = View(this)
        val initialized = CompletableDeferred<Unit>()
        view.prepare = {
            withContext(NonCancellable) { initialized.await() }
            false
        }
        view.show(cat)
        view.playback.request(cat) { voice }
        runCurrent()
        view.hide()
        initialized.complete(Unit)
        advanceUntilIdle()
        assertThat(view.failures).isEmpty()

        view.visible = true
        view.show(cat)
        view.playback.request(cat) { voice }
        advanceUntilIdle()
        assertThat(view.failures).containsExactly(PopupAudioPlayback.Failure.NO_VOICE)
    }

    @Test
    fun `a stale button event is rejected when its term is absent from the rendered page`() = runTest {
        val view = View(this)
        view.show(cat)
        view.rendered = listOf(dog)
        view.playback.request(cat) { clip }
        advanceUntilIdle()

        assertThat(view.prepared).isEmpty()
        assertThat(view.failures).isEmpty()
    }

    @Test
    fun `closing during the page check rejects its late snapshot`() = runTest {
        val view = View(this)
        val reply = CompletableDeferred<Unit>()
        view.show(cat)
        view.readRendered = {
            val snapshot = view.rendered
            withContext(NonCancellable) { reply.await() }
            snapshot
        }
        view.playback.request(cat) { clip }
        runCurrent()
        view.hide()
        reply.complete(Unit)
        advanceUntilIdle()

        assertThat(view.prepared).isEmpty()
        assertThat(view.failures).isEmpty()
    }

    @Test
    fun `a new request on the same word supersedes the previous prepared start`() = runTest {
        val view = View(this)
        view.show(cat)
        view.playback.request(cat) { clip }
        advanceUntilIdle()
        val first = view.prepared.single()

        view.playback.request(cat) { voice }
        advanceUntilIdle()

        assertThat(first()).isFalse()
        assertThat(view.prepared.last()()).isTrue()
    }

    @Test
    fun `a hidden popup rejects both new requests and a previously submitted start`() = runTest {
        val view = View(this)
        view.show(cat)
        view.playback.request(cat) { clip }
        advanceUntilIdle()

        view.visible = false
        assertThat(view.prepared.single()()).isFalse()
        view.playback.request(cat) { error("Hidden requests must not resolve audio") }
        advanceUntilIdle()
        assertThat(view.prepared).hasSize(1)
    }
}
