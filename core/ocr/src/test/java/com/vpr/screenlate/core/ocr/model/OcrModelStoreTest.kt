package com.vpr.screenlate.core.ocr.model

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test

class OcrModelStoreTest {
    private val temp: File = Files.createTempDirectory("models").toFile()
    private val root = File(temp, "ocr-models")
    private val store = OcrModelStore(root)

    @After
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun file(name: String, text: String) = File(temp, name).apply { writeText(text) }

    private fun zip(name: String, entries: Map<String, String>) = File(temp, name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun spec(archive: File, version: String = "1", id: String = "det") =
        OcrModelSpec(id = id, version = version, engine = "fake", sha256 = OcrModelStore.sha256(archive))

    @Test
    fun `a single file is kept under the model name`() = runTest {
        val archive = file("det.bin", "weights")
        val model = store.install(spec(archive), archive)
        assertThat(model.file(OcrModelStore.MODEL_FILE).readText()).isEqualTo("weights")
        assertThat(store.isInstalled("det", "1")).isTrue()
        assertThat(archive.exists()).isTrue()
        // A new store reads what an earlier one installed.
        assertThat(OcrModelStore(root).models.value.map { it.id to it.engine }).containsExactly("det" to "fake")
    }

    @Test
    fun `a zip archive is unpacked`() = runTest {
        val archive = zip("rec.zip", mapOf("rec.onnx" to "net", "dict/labels.txt" to "a\nb"))
        val model = store.install(spec(archive, id = "rec"), archive)
        assertThat(model.file("rec.onnx").readText()).isEqualTo("net")
        assertThat(model.file("dict/labels.txt").readText()).isEqualTo("a\nb")
        assertThat(model.bytes).isGreaterThan(0L)
    }

    @Test
    fun `a file that does not match its checksum is not installed`() = runTest {
        val archive = file("det.bin", "weights")
        val wrong = spec(archive).copy(sha256 = "0".repeat(64))
        assertThrows(ModelChecksumException::class.java) { kotlinx.coroutines.runBlocking { store.install(wrong, archive) } }
        assertThat(store.models.value).isEmpty()
    }

    @Test
    fun `entries outside the model directory are refused`() = runTest {
        val archive = zip("evil.zip", mapOf("../evil.txt" to "x"))
        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { store.install(spec(archive), archive) } }
        assertThat(File(root, "evil.txt").exists()).isFalse()
        assertThat(store.models.value).isEmpty()
    }

    @Test
    fun `a new version replaces the old one`() = runTest {
        val first = zip("v1.zip", mapOf("old.bin" to "1"))
        store.install(spec(first), first)
        val second = zip("v2.zip", mapOf("new.bin" to "2"))
        val model = store.install(spec(second, version = "2"), second)
        assertThat(store.isInstalled("det", "1")).isFalse()
        assertThat(store.isInstalled("det", "2")).isTrue()
        assertThat(model.file("old.bin").exists()).isFalse()
        assertThat(root.list().orEmpty().toList()).containsExactly("det")
    }

    @Test
    fun `delete removes the named models only`() = runTest {
        val archive = file("a.bin", "a")
        store.install(spec(archive, id = "a"), archive)
        store.install(spec(archive, id = "b"), archive)
        store.delete(listOf("a", "missing", "../escape"))
        assertThat(store.models.value.map { it.id }).containsExactly("b")
    }
}
