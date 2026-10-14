package com.vpr.screenlate.dictionary.api

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import org.junit.Test

class ReadingWordsTest {
    private val chinese = Language.CHINESE.support
    private val simplified = chinese.scriptTags[0]
    private val traditional = chinese.scriptTags[1]

    private fun spellings(lists: List<List<String>>, script: String?) = readingWords(lists, script, chinese).map { it.spelling }

    @Test
    fun `the lists of the dictionaries take turns`() {
        val lists = listOf(listOf("市", "事", "石"), listOf("是", "事", "十", "时"))
        assertThat(spellings(lists, null)).containsExactly("市", "是", "事", "石", "十", "时").inOrder()
    }

    @Test
    fun `words in the other script are spelled in the chosen one, once`() {
        val lists = listOf(listOf("圖書館", "图书馆"), listOf("圖書館"))
        val words = readingWords(lists, simplified, chinese)
        assertThat(words).containsExactly(ReadingWord("图书馆", "圖書館"))
    }

    @Test
    fun `words written alike in both scripts stay as they are`() {
        val lists = listOf(listOf("你好", "後", "后"))
        assertThat(spellings(lists, traditional)).containsExactly("你好", "後", "后").inOrder()
        assertThat(spellings(lists, simplified)).containsExactly("你好", "后").inOrder()
    }

    @Test
    fun `nothing listed looks nothing up`() {
        assertThat(readingWords(emptyList(), simplified, chinese)).isEmpty()
        assertThat(readingWords(listOf(emptyList()), null, chinese)).isEmpty()
    }
}
