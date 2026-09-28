package com.vpr.screenlate.dictionary.api.languages

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DictionarySampleTest {

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

    @Test
    fun `reads headwords and definitions without furigana or images`() {
        val structured = """{"type":"structured-content","content":[{"tag":"ruby","content":["食",{"tag":"rt","content":"た"}]},""" +
            """{"tag":"span","title":"noun","content":"to eat"},{"tag":"img","path":"a.png"}]}"""
        val sample = DictionarySample.of(
            archive(
                "index.json" to "{}",
                "term_bank_2.json" to """[["後","あと","","",0,["later"],2,""]]""",
                "term_bank_1.json" to """[["食べる","たべる","v1","v1",0,[$structured,"to \"have\" a meal"],1,""]]""",
            ),
        )

        assertThat(sample.headwords.trim()).isEqualTo("食べる たべる")
        assertThat(sample.definitions).contains("食")
        assertThat(sample.definitions).contains("to eat")
        assertThat(sample.definitions).contains("to \"have\" a meal")
        assertThat(sample.definitions).doesNotContain("た ")
        assertThat(sample.definitions).doesNotContain("a.png")
        assertThat(sample.definitions).doesNotContain("later")
    }

    @Test
    fun `kanji banks give characters with readings and meanings, meta banks headwords only`() {
        val kanji = DictionarySample.of(archive("kanji_bank_1.json" to """[["日","ニチ ジツ","ひ -び","",["day","sun"],{}]]"""))
        assertThat(kanji.headwords).contains("ニチ")
        assertThat(kanji.definitions.trim()).isEqualTo("day sun")

        val frequencies = DictionarySample.of(archive("term_meta_bank_1.json" to """[["の","freq",1],["に","freq",2]]"""))
        assertThat(frequencies.headwords.trim()).isEqualTo("の に")
        assertThat(frequencies.definitions).isEmpty()
    }

    @Test
    fun `reads only the first rows of a large bank`() {
        val rows = (1..1000).joinToString(",", "[", "]") { """["語$it","ご","","",0,["word [$it]"],$it,""]""" }

        val first = DictionarySample.firstRows(rows.byteInputStream(), maxRows = 10)
        assertThat(first).hasSize(10)

        // A byte limit in the middle of a row drops that row.
        val limited = DictionarySample.firstRows(rows.byteInputStream(), maxBytes = 100)
        assertThat(limited).hasSize(2)
        assertThat(DictionarySample.firstRows("not json".byteInputStream())).isEmpty()
    }
}
