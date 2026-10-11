package com.vpr.screenlate.overlay.anki

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.AnkiStatus
import com.vpr.screenlate.core.common.Language
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AnkiStatusCacheTest {
    private var now = 0L
    private val cache = AnkiStatusCache(5_000) { now }

    @Test
    fun `a configured language never inherits another language's unconfigured status`() = runTest {
        val read: suspend (Language) -> AnkiStatus = {
            if (it == Language.JAPANESE) AnkiStatus.NotConfigured else AnkiStatus.Ready
        }
        assertThat(cache.get(Language.JAPANESE, read)).isEqualTo(AnkiStatus.NotConfigured)
        assertThat(cache.get(Language.ENGLISH, read)).isEqualTo(AnkiStatus.Ready)
        assertThat(now).isEqualTo(0L)
    }

    @Test
    fun `an old language's late answer does not replace the new language's cached status`() = runTest {
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val old = async {
            cache.get(Language.JAPANESE) {
                started.complete(Unit)
                resume.await()
                AnkiStatus.NotConfigured
            }
        }
        started.await()
        assertThat(cache.get(Language.ENGLISH) { AnkiStatus.Ready }).isEqualTo(AnkiStatus.Ready)
        resume.complete(Unit)
        old.await()
        assertThat(cache.get(Language.ENGLISH) { error("The new status should still be cached") }).isEqualTo(AnkiStatus.Ready)
    }

    @Test
    fun `closing a view invalidates pending answers as well as the saved status`() = runTest {
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val old = async {
            cache.get(Language.JAPANESE) {
                started.complete(Unit)
                resume.await()
                AnkiStatus.NotConfigured
            }
        }
        started.await()
        cache.clear()
        resume.complete(Unit)
        old.await()
        assertThat(cache.get(Language.JAPANESE) { AnkiStatus.Ready }).isEqualTo(AnkiStatus.Ready)
    }

    @Test
    fun `a status is reused within its TTL and refreshed when it expires`() = runTest {
        assertThat(cache.get(Language.ENGLISH) { AnkiStatus.Ready }).isEqualTo(AnkiStatus.Ready)
        now = 4_999
        assertThat(cache.get(Language.ENGLISH) { error("Still fresh") }).isEqualTo(AnkiStatus.Ready)
        now = 5_000
        assertThat(cache.get(Language.ENGLISH) { AnkiStatus.NotConfigured }).isEqualTo(AnkiStatus.NotConfigured)
    }
}
