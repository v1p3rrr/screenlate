package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.audio.AudioCandidate
import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.AudioSourceType
import com.vpr.screenlate.core.common.Language
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PopupAudioMenuTest {
    private data class Display(val request: Int, val items: JsonArray, val loading: Boolean)

    private val empty = JsonArray(emptyList())
    private val source = AudioSource(AudioSourceType.WIKTIONARY)
    private fun clips(expression: String) = PopupAudioMenu.Clips(
        listOf(AudioCandidate(expression, 0, source, "https://example.test/clip.ogg", "", Language.ENGLISH)),
        JsonArray(listOf(JsonPrimitive(expression))),
    )

    @Test
    fun `reopening the menu after choosing a clip immediately shows the same choices`() = runTest {
        val shown = mutableListOf<Display>()
        val loaded = mutableListOf<String>()
        val menu = PopupAudioMenu(this, { word, _, _ ->
            loaded += word
            clips(word)
        }) { request, items, loading ->
            shown += Display(request, items, loading)
        }

        menu.open(1, 1, "cat", "", Language.ENGLISH)
        advanceUntilIdle()
        assertThat(menu.choose(1, "cat")?.candidate).isEqualTo(clips("cat").candidates.single())
        menu.open(1, 2, "cat", "", Language.ENGLISH)

        assertThat(shown).containsExactly(
            Display(1, empty, true),
            Display(1, clips("cat").items, false),
            Display(2, clips("cat").items, false),
        ).inOrder()
        advanceUntilIdle()
        assertThat(loaded).containsExactly("cat")
        assertThat(menu.choose(1, "cat")).isNull()
        assertThat(menu.choose(2, "cat")?.term).isEqualTo("cat" to "")
    }

    @Test
    fun `different entries in a view keep their own choices`() = runTest {
        val loaded = mutableListOf<Pair<String, String>>()
        val menu = PopupAudioMenu(this, { word, reading, _ ->
            loaded += word to reading
            clips(reading)
        }) { _, _, _ -> }

        menu.open(1, 1, "read", "reed", Language.ENGLISH)
        advanceUntilIdle()
        menu.open(1, 2, "read", "red", Language.ENGLISH)
        advanceUntilIdle()
        menu.open(1, 3, "read", "reed", Language.ENGLISH)

        assertThat(menu.choose(3, "red")).isNull()
        assertThat(menu.choose(3, "reed")?.term).isEqualTo("read" to "reed")
        advanceUntilIdle()
        assertThat(loaded).containsExactly("read" to "reed", "read" to "red").inOrder()
    }

    @Test
    fun `a new view and a closed popup both discard the list`() = runTest {
        val loading = mutableListOf<Int>()
        val menu = PopupAudioMenu(this, { word, _, _ -> clips(word) }) { request, _, pending ->
            if (pending) loading += request
        }

        menu.open(1, 1, "cat", "", Language.ENGLISH)
        advanceUntilIdle()
        menu.open(3, 2, "cat", "", Language.ENGLISH)
        advanceUntilIdle()
        menu.clear()
        assertThat(menu.choose(2, "cat")).isNull()
        menu.open(3, 3, "cat", "", Language.ENGLISH)
        advanceUntilIdle()

        assertThat(loading).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `an empty list is also reused`() = runTest {
        val shown = mutableListOf<Display>()
        val menu = PopupAudioMenu(this, { _, _, _ -> PopupAudioMenu.Clips(emptyList(), empty) }) { request, items, loading ->
            shown += Display(request, items, loading)
        }

        menu.open(1, 1, "cat", "", Language.ENGLISH)
        advanceUntilIdle()
        menu.open(1, 2, "cat", "", Language.ENGLISH)

        assertThat(shown).containsExactly(Display(1, empty, true), Display(1, empty, false), Display(2, empty, false)).inOrder()
    }

    @Test
    fun `a delayed answer from an old view cannot replace the new list`() = runTest {
        val oldReply = CompletableDeferred<Unit>()
        val finished = mutableListOf<Int>()
        val menu = PopupAudioMenu(this, { word, _, _ ->
            if (word == "cat") withContext(NonCancellable) { oldReply.await() }
            clips(word)
        }) { request, _, loading ->
            if (!loading) finished += request
        }

        menu.open(1, 1, "cat", "", Language.ENGLISH)
        runCurrent()
        menu.open(2, 2, "dog", "", Language.ENGLISH)
        oldReply.complete(Unit)
        advanceUntilIdle()

        assertThat(finished).containsExactly(2)
        assertThat(menu.choose(2, "dog")?.candidate).isEqualTo(clips("dog").candidates.single())
    }
}
