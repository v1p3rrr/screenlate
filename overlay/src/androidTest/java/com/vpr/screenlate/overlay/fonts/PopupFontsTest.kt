package com.vpr.screenlate.overlay.fonts

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Adding, replacing, deleting and restoring font files in the app's font folder (of the test app). */
@RunWith(AndroidJUnit4::class)
class PopupFontsTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.filesDir, PageFonts.DIRECTORY)
    private val sources = File(context.cacheDir, "font-sources")
    private lateinit var fonts: PopupFonts

    @Before
    fun setUp() {
        directory.deleteRecursively()
        sources.deleteRecursively()
        sources.mkdirs()
        fonts = PopupFonts(context, OkHttpClient())
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
        sources.deleteRecursively()
    }

    /** A file with [bytes] to pick, as a `file:` URI. */
    private fun source(name: String, bytes: ByteArray): Uri = Uri.fromFile(File(sources, name).apply { writeBytes(bytes) })

    private fun import(name: String, bytes: ByteArray) = runBlocking { fonts.import(source(name, bytes)) }

    private fun partials() = directory.listFiles().orEmpty().filter { it.name.endsWith(".part") }

    @Test
    fun ownFilesAreSeparateFontsNamedByTheirStyle() {
        val regular = import("a.ttf", font(family = "Test Serif", style = "Regular", weight = 400))
        val bold = import("b.ttf", font(family = "Test Serif", style = "Bold", weight = 700))

        assertThat(regular).isInstanceOf(FontImport.Added::class.java)
        assertThat(bold).isInstanceOf(FontImport.Added::class.java)
        val installed = fonts.installed.value
        assertThat(installed.map { it.family }).containsExactly("Test Serif", "Test Serif Bold").inOrder()
        assertThat(installed.map { it.files.single().weight }).containsExactly("400", "700").inOrder()
        assertThat(installed.all { it.catalogId == null }).isTrue()
        assertThat(partials()).isEmpty()
    }

    @Test
    fun aFileOfTheSameNameReplacesTheFontAndItsFile() {
        val first = import("a.ttf", font(family = "Test Serif", style = "Regular", weight = 400)) as FontImport.Added
        val second = import("a2.ttf", font(family = "Test Serif", style = "Regular", weight = 400)) as FontImport.Added

        assertThat(second.font.id).isEqualTo(first.font.id)
        assertThat(fonts.installed.value).containsExactly(second.font)
        assertThat(File(directory, first.font.files.single().name).exists()).isFalse()
        assertThat(File(directory, second.font.files.single().name).exists()).isTrue()
    }

    @Test
    fun webFontsAndOtherFilesAreNotAdded() {
        assertThat(import("a.woff", "wOFF".toByteArray() + ByteArray(40))).isEqualTo(FontImport.WebFont)
        assertThat(import("a.woff2", "wOF2".toByteArray() + ByteArray(40))).isEqualTo(FontImport.WebFont)
        assertThat(import("a.html", "<html>not a font</html>".toByteArray())).isEqualTo(FontImport.NotAFont)
        assertThat(fonts.installed.value).isEmpty()
        assertThat(partials()).isEmpty()
    }

    @Test
    fun anUnreadableFileFailsWithoutAThrow() {
        val missing = Uri.fromFile(File(sources, "missing.ttf"))
        assertThat(runBlocking { fonts.import(missing) }).isEqualTo(FontImport.Failed)
        assertThat(partials()).isEmpty()
    }

    @Test
    fun deletingRemovesTheFontAndItsFile() {
        val added = import("a.ttf", font(family = "Test Serif", style = "Regular", weight = 400)) as FontImport.Added
        runBlocking { fonts.delete(added.font.id) }
        assertThat(fonts.installed.value).isEmpty()
        assertThat(File(directory, added.font.files.single().name).exists()).isFalse()
    }

    @Test
    fun aRestoredListIsChecked() {
        val backup = File(context.cacheDir, "font-backup").apply {
            deleteRecursively()
            mkdirs()
        }
        listOf("good-0.ttf", "blank-0.ttf").forEach { File(backup, it).writeBytes(font("X", null, 400)) }
        val list = listOf(
            InstalledFont("good", "Good", listOf(FontFile("good-0.ttf", "400; } body { color: red"))),
            InstalledFont("blank", " ", listOf(FontFile("blank-0.ttf"))),
            InstalledFont("escape", "Escape", listOf(FontFile("../escape.ttf"))),
            InstalledFont("missing", "Missing", listOf(FontFile("missing-0.ttf"))),
        )
        File(backup, "fonts.json").writeText(Json.encodeToString(list))

        val count = runBlocking { fonts.restore(backup) }

        assertThat(count).isEqualTo(1)
        assertThat(fonts.installed.value).containsExactly(InstalledFont("good", "Good", listOf(FontFile("good-0.ttf", "400"))))
        backup.deleteRecursively()
    }

    private companion object {
        /** A TrueType font with a `name` table (family, and the style as the typographic subfamily) and an `OS/2` weight. */
        fun font(family: String, style: String?, weight: Int): ByteArray {
            val names = listOfNotNull(16 to family, style?.let { 17 to it })
            val strings = names.map { it.second.toByteArray(Charsets.UTF_16BE) }
            val name = ByteArrayOutputStream().also { out ->
                DataOutputStream(out).apply {
                    writeShort(0)
                    writeShort(names.size)
                    writeShort(6 + 12 * names.size)
                    var offset = 0
                    names.forEachIndexed { index, (id, _) ->
                        writeShort(3)
                        writeShort(1)
                        writeShort(0x409)
                        writeShort(id)
                        writeShort(strings[index].size)
                        writeShort(offset)
                        offset += strings[index].size
                    }
                    strings.forEach { write(it) }
                }
            }.toByteArray()
            val os2 = ByteArrayOutputStream().also { out ->
                DataOutputStream(out).apply {
                    writeShort(4)
                    writeShort(500)
                    writeShort(weight)
                    write(ByteArray(10))
                }
            }.toByteArray()
            val tables = listOf("OS/2" to os2, "name" to name)
            return ByteArrayOutputStream().also { out ->
                DataOutputStream(out).apply {
                    writeInt(0x00010000)
                    writeShort(tables.size)
                    writeShort(0)
                    writeShort(0)
                    writeShort(0)
                    var offset = 12 + 16 * tables.size
                    tables.forEach { (tag, data) ->
                        writeBytes(tag)
                        writeInt(0)
                        writeInt(offset)
                        writeInt(data.size)
                        offset += data.size
                    }
                    tables.forEach { write(it.second) }
                }
            }.toByteArray()
        }
    }
}
