package com.vpr.screenlate.backup

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a backup reads back with its fonts and dictionary files`() = runTest {
        val font = temp.newFile("font-0.ttf").apply { writeText("font") }
        val first = temp.newFolder("a").apply {
            File(this, "terms").mkdirs()
            File(this, "terms/bank.bin").writeText("terms of a")
            File(this, "index.json").writeText("{}")
        }
        val third = temp.newFolder("c").apply { File(this, "data.bin").writeText("c") }
        val contents = BackupArchive.Contents(
            manifest = BackupManifest(appVersion = "1.0", createdAt = 1, sections = BackupSection.entries),
            settings = JsonObject(mapOf("theme_mode" to JsonPrimitive("x"))),
            dictionaries = BackupDictionaryList(
                listOf(dictionary("A", files = true), dictionary("B", files = false), dictionary("C", files = true)),
                sortDictionary = "B",
            ),
            fonts = listOf(font),
            dictionaryFiles = mapOf(0 to first, 2 to third),
        )
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, contents) }.toByteArray()

        val head = BackupArchive.readHead(ByteArrayInputStream(bytes))
        assertThat(head.manifest.appVersion).isEqualTo("1.0")
        assertThat(head.dictionaries.sortDictionary).isEqualTo("B")

        val reader = Recorder()
        var headFirst = false
        BackupArchive.read(ByteArrayInputStream(bytes), reader) { headFirst = reader.events.isEmpty() && it.settings.isNotEmpty() }
        assertThat(headFirst).isTrue()
        assertThat(reader.events).containsExactly(
            "font font-0.ttf=font",
            "file 0 index.json={}",
            "file 0 terms/bank.bin=terms of a",
            "done 0",
            "file 2 data.bin=c",
            "done 2",
        ).inOrder()
    }

    @Test
    fun `other zip files are not backups`() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use {
                it.putNextEntry(ZipEntry("index.json"))
                it.write("{}".toByteArray())
                it.closeEntry()
            }
        }.toByteArray()
        assertThrows(BackupArchive.NotBackupException::class.java) { BackupArchive.readHead(ByteArrayInputStream(zip)) }
    }

    @Test
    fun `a newer format is refused`() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use {
                it.putNextEntry(ZipEntry(BackupLayout.MANIFEST))
                it.write("""{"format":99,"appVersion":"9","createdAt":0,"sections":[]}""".toByteArray())
                it.closeEntry()
            }
        }.toByteArray()
        assertThrows(BackupArchive.NewerFormatException::class.java) { BackupArchive.readHead(ByteArrayInputStream(zip)) }
    }

    private class Recorder : BackupArchive.Reader {
        val events = mutableListOf<String>()

        override suspend fun font(name: String, input: InputStream) {
            events += "font $name=${input.readBytes().decodeToString()}"
        }

        override suspend fun dictionaryFile(index: Int, path: String, input: InputStream) {
            events += "file $index $path=${input.readBytes().decodeToString()}"
        }

        override suspend fun dictionaryDone(index: Int) {
            events += "done $index"
        }
    }

    private fun dictionary(title: String, files: Boolean) =
        BackupDictionary(title = title, revision = "1", kind = "TERM", enabled = true, priority = 0, files = files)
}
