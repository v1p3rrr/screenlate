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
            fontIndex = "[]",
            fonts = listOf(font),
            dictionaryFiles = mapOf(0 to first, 2 to third),
        )
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, contents) }.toByteArray()

        val head = BackupArchive.readHead(ByteArrayInputStream(bytes))
        assertThat(head.manifest.appVersion).isEqualTo("1.0")
        assertThat(head.dictionaries.sortDictionary).isEqualTo("B")
        assertThat(head.fontIndex).isEqualTo("[]")
        assertThat(head.fontSizes).containsExactly("font-0.ttf", 4L)

        val reader = Recorder()
        var headFirst = false
        BackupArchive.read(ByteArrayInputStream(bytes), reader) { headFirst = reader.events.isEmpty() && it.settings.isNotEmpty() }
        assertThat(headFirst).isTrue()
        assertThat(reader.events).containsExactly(
            "font fonts.json=[]",
            "font font-0.ttf=font",
            "file 0 index.json={}",
            "file 0 terms/bank.bin=terms of a",
            "done 0",
            "file 2 data.bin=c",
            "done 2",
        ).inOrder()
    }

    @Test
    fun `without dictionary files reading stops where they start`() = runTest {
        val big = temp.newFolder("big").apply { File(this, "data.bin").writeBytes(kotlin.random.Random(1).nextBytes(1 shl 20)) }
        val contents = BackupArchive.Contents(
            manifest = BackupManifest(appVersion = "1.0", createdAt = 1, sections = BackupSection.entries),
            settings = JsonObject(emptyMap()),
            dictionaries = BackupDictionaryList(listOf(dictionary("A", files = true))),
            dictionaryFiles = mapOf(0 to big),
        )
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, contents) }.toByteArray()
        val input = ByteArrayInputStream(bytes)
        val reader = Recorder()
        val head = BackupArchive.read(input, reader, dictionaryFiles = false)
        assertThat(head.dictionaries.dictionaries.single().title).isEqualTo("A")
        assertThat(reader.events).isEmpty()
        assertThat(input.available()).isGreaterThan(bytes.size / 2)
    }

    @Test
    fun `model files come between the fonts and the dictionary files`() = runTest {
        val font = temp.newFile("font-0.ttf").apply { writeText("font") }
        val model = temp.newFolder("detector").apply {
            File(this, "inference.onnx").writeText("weights")
            File(this, "dict").mkdirs()
            File(this, "dict/keys.txt").writeText("abc")
        }
        val dictionary = temp.newFolder("a").apply { File(this, "data.bin").writeText("a") }
        val manifest = BackupManifest(
            appVersion = "1.0",
            createdAt = 1,
            sections = BackupSection.entries,
            languages = listOf("ko"),
            models = listOf(BackupModel("detector", "2", "paddle", listOf("ko"), bytes = 10)),
        )
        val contents = BackupArchive.Contents(
            manifest = manifest,
            settings = JsonObject(emptyMap()),
            dictionaries = BackupDictionaryList(listOf(dictionary("A", files = true))),
            fontIndex = "[]",
            fonts = listOf(font),
            models = mapOf("detector" to (model to listOf(File(model, "dict/keys.txt"), File(model, "inference.onnx")))),
            dictionaryFiles = mapOf(0 to dictionary),
        )
        assertThat(BackupArchive.size(contents)).isEqualTo(11)
        val progress = mutableListOf<Long>()
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, contents) { written -> progress += written } }.toByteArray()
        assertThat(progress).containsExactly(3L, 10L, 11L).inOrder()

        val head = BackupArchive.readHead(ByteArrayInputStream(bytes))
        assertThat(head.manifest).isEqualTo(manifest)
        assertThat(head.fontSizes).containsExactly("font-0.ttf", 4L)

        val reader = Recorder()
        BackupArchive.read(ByteArrayInputStream(bytes), reader)
        assertThat(reader.events).containsExactly(
            "font fonts.json=[]",
            "font font-0.ttf=font",
            "model detector dict/keys.txt=abc",
            "model detector inference.onnx=weights",
            "file 0 data.bin=a",
            "done 0",
        ).inOrder()

        val withoutModels = Recorder()
        BackupArchive.read(ByteArrayInputStream(bytes), withoutModels, models = false)
        assertThat(withoutModels.events).containsExactly("font fonts.json=[]", "font font-0.ttf=font", "file 0 data.bin=a", "done 0").inOrder()

        val settingsOnly = Recorder()
        BackupArchive.read(ByteArrayInputStream(bytes), settingsOnly, dictionaryFiles = false, models = false)
        assertThat(settingsOnly.events).containsExactly("font fonts.json=[]", "font font-0.ttf=font").inOrder()
    }

    @Test
    fun `a backup of an older version has no languages`() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use {
                it.putNextEntry(ZipEntry(BackupLayout.MANIFEST))
                it.write("""{"format":1,"appVersion":"1","createdAt":0,"sections":["GENERAL"]}""".toByteArray())
                it.closeEntry()
            }
        }.toByteArray()
        val manifest = BackupArchive.readHead(ByteArrayInputStream(zip)).manifest
        assertThat(manifest.languages).isNull()
        assertThat(manifest.models).isEmpty()
    }

    @Test
    fun `sections of a newer version are left out`() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use {
                it.putNextEntry(ZipEntry(BackupLayout.MANIFEST))
                it.write("""{"format":1,"appVersion":"9","createdAt":0,"sections":["GENERAL","SOMETHING_NEW","ANKI"]}""".toByteArray())
                it.closeEntry()
            }
        }.toByteArray()
        assertThat(BackupArchive.readHead(ByteArrayInputStream(zip)).manifest.sections)
            .containsExactly(BackupSection.GENERAL, BackupSection.ANKI).inOrder()
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

        override suspend fun modelFile(id: String, path: String, input: InputStream) {
            events += "model $id $path=${input.readBytes().decodeToString()}"
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
