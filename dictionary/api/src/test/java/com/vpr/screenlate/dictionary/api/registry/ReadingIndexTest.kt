package com.vpr.screenlate.dictionary.api.registry

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadingIndexTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun written(builder: ReadingIndex.Builder): ReadingIndex {
        val file = File(folder.root, "reading_index.bin")
        builder.write(file)
        return ReadingIndex.read(file)
    }

    @Test
    fun `finds the words of a key and nothing for others`() {
        val index = written(
            ReadingIndex.Builder().apply {
                add("你好", "nihao")
                add("图书馆", "tushuguan")
                add("圖書館", "tushuguan")
                add("OK", null)
            },
        )

        assertThat(index.size).isEqualTo(2)
        assertThat(index.find("nihao")).containsExactly("你好")
        assertThat(index.find("tushuguan")).containsExactly("图书馆", "圖書館")
        assertThat(index.find("ok")).isEmpty()
        assertThat(index.find("")).isEmpty()
    }

    @Test
    fun `characters in more words come first, a word lists once`() {
        val index = written(
            ReadingIndex.Builder().apply {
                add("㑊", "yi")
                add("意", "yi")
                add("意", "yi")
                add("一", "yi")
                add("一些", "yixie")
                add("一定", "yiding")
                add("意思", "yisi")
            },
        )

        assertThat(index.find("yi")).containsExactly("一", "意", "㑊").inOrder()
    }

    @Test
    fun `a word is as common as its rarest character`() {
        val index = written(
            ReadingIndex.Builder().apply {
                add("市", "shi")
                add("市场", "shichang")
                add("市长", "shizhang")
                add("城市", "chengshi")
                add("事", "shi")
                add("事实", "shishi")
                add("实", "shi")
                add("实时", "shishi")
                add("时", "shi")
            },
        )

        // The rarest characters of 事实 (事) and 实时 (时) are in two words each: a tie, which goes by text.
        assertThat(index.find("shishi")).containsExactly("事实", "实时").inOrder()
        assertThat(index.find("shi").first()).isEqualTo("市")
    }

    @Test
    fun `an empty builder has nothing to write`() {
        assertThat(ReadingIndex.Builder().apply { add("OK", null) }.isEmpty).isTrue()
    }

    @Test
    fun `a file of another kind is refused`() {
        val file = folder.newFile().apply { writeBytes(ByteArray(16) { 7 }) }
        assertThrows(IOException::class.java) { ReadingIndex.read(file) }
        val short = folder.newFile().apply { writeBytes(ByteArray(3)) }
        assertThrows(IOException::class.java) { ReadingIndex.read(short) }
    }
}
