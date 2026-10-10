package com.vpr.screenlate.dictionaries

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.dictionary.api.registry.DictionaryKind
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test

class DictionaryLanguagesTest {
    private val locale = Locale.getDefault()

    @Before
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @After
    fun restore() = Locale.setDefault(locale)

    private fun dictionary(id: Long, source: String?) = DictionaryEntity(
        id = id, title = "d$id", revision = "1", kind = DictionaryKind.TERM, sourceLanguage = source, targetLanguage = "en",
        frequencyMode = null, enabled = true, priority = id.toInt(), directory = "d$id", termCount = 1, frequencyCount = 0,
        pitchCount = 0, kanjiCount = 0, mediaCount = 0, isUpdatable = false, indexUrl = null, downloadUrl = null,
        author = null, url = null, description = null, attribution = null, bundled = false, importedAt = 0,
    )

    @Test
    fun `a new order of one language's dictionaries leaves the others in their places`() {
        val ja1 = dictionary(1, "ja")
        val en1 = dictionary(2, "en")
        val ja2 = dictionary(3, "ja")
        val shared = dictionary(4, null)
        val en2 = dictionary(5, "en")
        val all = listOf(ja1, en1, ja2, shared, en2)

        assertThat(reordered(all, listOf(en2, en1)).map { it.id }).containsExactly(1L, 5L, 3L, 4L, 2L).inOrder()
        assertThat(reordered(all, listOf(shared, ja2, ja1)).map { it.id }).containsExactly(4L, 2L, 3L, 1L, 5L).inOrder()
    }

    @Test
    fun `dictionaries of languages not turned on are grouped by language with their size once all are measured`() {
        val list = listOf(dictionary(1, "ja"), dictionary(2, "ko"), dictionary(3, "en"), dictionary(4, "ko"), dictionary(5, null))

        val others = otherLanguages(list, listOf(Language.JAPANESE), sizes = mapOf(2L to 10L, 3L to 5L, 4L to 7L))
        assertThat(others.map { it.code }).containsExactly("en", "ko").inOrder()
        assertThat(others[1].dictionaries.map { it.id }).containsExactly(2L, 4L).inOrder()
        assertThat(others[1].bytes).isEqualTo(17L)

        val unmeasured = otherLanguages(list, listOf(Language.JAPANESE, Language.ENGLISH), sizes = mapOf(2L to 10L))
        assertThat(unmeasured.single().code).isEqualTo("ko")
        assertThat(unmeasured.single().bytes).isNull()
    }

    @Test
    fun `the stored sort dictionary counts while it is an enabled frequency dictionary, else the first one sorts`() {
        fun frequencies(id: Long, enabled: Boolean = true) =
            dictionary(id, "en").copy(kind = DictionaryKind.FREQUENCY, termCount = 0, frequencyCount = 1, enabled = enabled)
        val list = listOf(dictionary(1, "en"), frequencies(2), frequencies(3), frequencies(4, enabled = false))

        assertThat(sortDictionaryId(list, stored = 3)).isEqualTo(3L)
        assertThat(sortDictionaryId(list, stored = null)).isEqualTo(2L)
        assertThat(sortDictionaryId(list, stored = 4)).isEqualTo(2L)
        assertThat(sortDictionaryId(list, stored = 1)).isEqualTo(2L)
        assertThat(sortDictionaryId(listOf(dictionary(1, "en")), stored = 1)).isNull()
    }
}
