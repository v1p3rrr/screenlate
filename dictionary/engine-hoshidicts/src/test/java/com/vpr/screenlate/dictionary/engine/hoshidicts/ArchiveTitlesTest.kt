package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test

class ArchiveTitlesTest {
    private val directory: File = Files.createTempDirectory("titles").toFile()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun archive(index: String): File = File(directory, "${index.hashCode()}.zip").apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("index.json"))
            zip.write(index.encodeToByteArray())
            zip.closeEntry()
        }
    }

    private fun reason(index: String): String? =
        runCatching { ArchiveTitles.check(archive(index)) }.exceptionOrNull()?.let { (it as DictionaryImportException).message }

    @Test
    fun `titles that stay in the output directory pass`() {
        for (title in listOf("JMdict [2026-09-27]", "a/b", "...", "..a", "a..", "\\u002e")) {
            assertThat(reason("""{"title":"$title","revision":"1","format":3}""")).isNull()
        }
        // No title: the importer reports that itself.
        assertThat(reason("""{"revision":"1"}""")).isNull()
    }

    @Test
    fun `titles that leave it are refused`() {
        for (title in listOf("..", "../..", "a/../../b", "/data/data", "\\u002e\\u002e/x", "a/..")) {
            assertThat(reason("""{"title":"$title"}""")).isEqualTo("invalid dictionary title")
        }
        assertThat(ArchiveTitles.isSafe("\\..")).isTrue()
    }

    @Test
    fun `an index or archive that cannot be read is refused`() {
        assertThat(reason("""{"title":"../..",""")).isEqualTo("failed to parse index.json")
        assertThat(reason("\uFEFF" + """{"title":"../.."}""")).isEqualTo("invalid dictionary title")
        val notZip = File(directory, "plain.zip").apply { writeText("not a zip") }
        assertThrows(DictionaryImportException::class.java) { ArchiveTitles.check(notZip) }
    }
}
