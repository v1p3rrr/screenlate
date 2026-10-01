package com.vpr.screenlate.dictionary.api.registry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IndexMovesTest {

    private val old = "https://old.example/kty-ja-ru-index.json"
    private val new = "https://new.example/wty-ja-ru-index.json"

    @Test
    fun `follows an index that names a new address`() {
        val indexes = mapOf(
            old to RemoteIndex("2026.03.05", "https://new.example/wty-ja-ru.zip", new),
            new to RemoteIndex("2026.09.20", "https://new.example/wty-ja-ru.zip", new),
        )
        assertThat(IndexMoves.follow(old, indexes::get)?.revision).isEqualTo("2026.09.20")
    }

    @Test
    fun `keeps an index that names itself or nothing`() {
        val same = RemoteIndex("1", "https://x/a.zip", old)
        assertThat(IndexMoves.follow(old) { same }).isEqualTo(same)
        val none = RemoteIndex("1", "https://x/a.zip")
        assertThat(IndexMoves.follow(old) { none }).isEqualTo(none)
    }

    @Test
    fun `falls back to the old index when the new address fails`() {
        val first = RemoteIndex("2026.03.05", "https://new.example/wty-ja-ru.zip", new)
        assertThat(IndexMoves.follow(old) { if (it == old) first else error("offline") }).isEqualTo(first)
        assertThat(IndexMoves.follow(old) { if (it == old) first else null }).isEqualTo(first)
    }
}
