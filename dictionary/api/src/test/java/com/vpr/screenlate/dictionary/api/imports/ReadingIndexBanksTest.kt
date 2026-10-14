package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.registry.ReadingIndex
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadingIndexBanksTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun archive(vararg files: Pair<String, String>): File = folder.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, text) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun read(archive: File) = runBlocking { ReadingIndexBanks.read(archive, Language.CHINESE.support) }

    private fun index(builder: ReadingIndex.Builder): ReadingIndex {
        val file = File(folder.root, "reading_index.bin")
        builder.write(file)
        return ReadingIndex.read(file)
    }

    @Test
    fun `keys the readings of term rows as the language shows them`() {
        val builder = read(
            archive(
                "index.json" to """{"title":"Chinese"}""",
                "term_bank_1.json" to """[
                    ["你好", "nǐhǎo", "", "", 0, ["hello"], 1, ""],
                    ["行", "xíng (xing²)", "", "", 0, ["to walk"], 2, ""],
                    ["健康保险", "", "", "", 0, [["健康保險", ["Simplified-Chinese"]]], 3, ""],
                    ["book", "buk⁷", "", "", 0, ["book"], 4, ""],
                    "broken row",
                    [7, "qī"]
                ]""",
                "term_bank_2.json" to """[["西安", "Xī'ān", "", "", 0, ["Xi'an"], 5, ""]]""",
                "term_meta_bank_1.json" to """[["你好", "freq", 5]]""",
            ),
        )!!
        val index = index(builder)

        assertThat(index.find("nihao")).containsExactly("你好")
        assertThat(index.find("xing")).containsExactly("行")
        assertThat(index.find("xian")).containsExactly("西安")
        assertThat(index.find("buk")).isEmpty()
        assertThat(index.find("qi")).isEmpty()
        assertThat(index.size).isEqualTo(3)
    }

    @Test
    fun `an archive without readings gives no index`() {
        val builder = read(archive("term_bank_1.json" to """[["健康保险", "", "", "", 0, ["x"], 1, ""]]"""))
        assertThat(builder).isNull()
    }
}
