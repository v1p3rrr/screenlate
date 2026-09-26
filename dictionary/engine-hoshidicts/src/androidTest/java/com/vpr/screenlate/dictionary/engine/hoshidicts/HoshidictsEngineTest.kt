package com.vpr.screenlate.dictionary.engine.hoshidicts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.LookupOptions
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class HoshidictsEngineTest {
    private lateinit var root: File
    private val engine = HoshidictsEngine()

    @Before
    fun setUp() {
        root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "engine-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun importsAndLooksUpDeinflectedTerms() = runTest {
        val terms = engine.import(termDictionary(), File(root, "out"))
        val frequencies = engine.import(frequencyDictionary(), File(root, "out"))
        assertThat(terms.metadata.title).isEqualTo("Test Terms")
        assertThat(terms.metadata.termCount).isEqualTo(3)
        assertThat(terms.metadata.mediaCount).isEqualTo(1)
        assertThat(frequencies.metadata.frequencyCount).isEqualTo(2)
        assertThat(frequencies.metadata.frequencyMode).isEqualTo("rank-based")

        engine.load(DictionarySet(terms = listOf(terms.directory), frequencies = listOf(frequencies.directory)))

        val results = engine.lookup("食べさせられなかった。", LookupOptions(frequencyDictionary = "Test Frequency"))
        val first = results.first()
        assertThat(first.term.expression).isEqualTo("食べる")
        assertThat(first.matched).isEqualTo("食べさせられなかった")
        assertThat(first.trace).isNotEmpty()
        assertThat(Json.parseToJsonElement(first.term.glossaries.single().content).jsonArray.first().jsonPrimitive.content)
            .isEqualTo("to eat")
        assertThat(first.term.frequencies.single().values.single().value).isEqualTo(100)

        // Characters outside the BMP must survive the JNI boundary.
        val scolded = engine.lookup("𠮟られた")
        assertThat(scolded.first().term.expression).isEqualTo("𠮟る")
        assertThat(scolded.first().matched).isEqualTo("𠮟られた")

        assertThat(engine.styles().single().css).contains(".gloss")
        assertThat(engine.media("Test Terms", "img/dot.png")).isEqualTo(PNG_BYTES)
        assertThat(engine.media("Test Terms", "img/missing.png")).isNull()
    }

    @Test(expected = DictionaryImportException::class)
    fun rejectsArchiveWithoutIndex() = runTest {
        val archive = zip("broken.zip", "term_bank_1.json" to "[]".toByteArray())
        engine.import(archive, File(root, "out"))
    }

    private fun termDictionary(): File = zip(
        "terms.zip",
        "index.json" to """{"title":"Test Terms","revision":"1","format":3,"sourceLanguage":"ja"}""".toByteArray(),
        "styles.css" to ".gloss { color: red; }".toByteArray(),
        "term_bank_1.json" to """
            [
              ["食べる","たべる","","v1",0,["to eat"],1,""],
              ["𠮟る","しかる","","v5",0,["to scold"],2,""],
              ["点","てん","","",0,[{"type":"image","path":"img/dot.png"}],3,""]
            ]
        """.trimIndent().toByteArray(),
        "img/dot.png" to PNG_BYTES,
    )

    private fun frequencyDictionary(): File = zip(
        "freq.zip",
        "index.json" to """{"title":"Test Frequency","revision":"1","format":3,"frequencyMode":"rank-based"}"""
            .toByteArray(),
        "term_meta_bank_1.json" to """[["食べる","freq",100],["点","freq",200]]""".toByteArray(),
    )

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val file = File(root, name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((path, bytes) in entries) {
                out.putNextEntry(ZipEntry(path))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    private companion object {
        val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    }
}
