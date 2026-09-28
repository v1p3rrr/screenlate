package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TagBanksTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun archive(vararg files: Pair<String, String>): File = folder.newFile("dictionary.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, text) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
        }
    }

    private val banks = arrayOf(
        "index.json" to """{"title":"JMdict"}""",
        "term_bank_1.json" to """[["猫","ねこ","n","",0,["cat"],1,""]]""",
        "tag_bank_1.json" to """[["n","partOfSpeech",0,"noun (common) (futsuumeishi)",0],["vs","partOfSpeech",0,"",0]]""",
        "tag_bank_2.json" to """[["col","misc",0,"colloquial\nexpression",0],["broken"]]""",
    )

    @Test
    fun `reads descriptions and skips tags without one`() {
        val notes = TagBanks.read(archive(*banks))

        assertThat(notes).containsExactly(
            "n", "noun (common) (futsuumeishi)",
            "col", "colloquial\nexpression",
        )
    }

    @Test
    fun `streams the same from an asset`() {
        val file = archive(*banks)

        val notes = ZipInputStream(file.inputStream()).use(TagBanks::read)

        assertThat(notes).isEqualTo(TagBanks.read(file))
    }

    @Test
    fun `an archive without tag banks or with a broken one gives none`() {
        assertThat(TagBanks.read(archive("index.json" to "{}", "tag_bank_1.json" to "not json"))).isEmpty()
    }
}
