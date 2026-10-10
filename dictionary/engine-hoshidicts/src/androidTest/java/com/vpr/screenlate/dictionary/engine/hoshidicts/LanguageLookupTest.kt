package com.vpr.screenlate.dictionary.engine.hoshidicts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.LookupOptions
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Lookups through Yomitan's language code in QuickJS, and form-of entries split out at import. */
@RunWith(AndroidJUnit4::class)
class LanguageLookupTest {
    private lateinit var root: File
    private val engine = HoshidictsEngine(InstrumentationRegistry.getInstrumentation().targetContext)
    private val english = LookupOptions(language = Language.ENGLISH)

    @Before
    fun setUp() {
        root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "language-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun deinflectsFollowsFormsAndFindsPhrases() = runTest {
        val imported = engine.import(englishDictionary(), File(root, "out"))
        assertThat(File(imported.directory, FORM_OF_TABLE).isFile).isTrue()
        assertThat(engine.isComplete(imported.directory)).isTrue()
        engine.load(DictionarySet(terms = listOf(imported.directory)))

        val running = engine.lookup("running fast", english).first()
        assertThat(running.term.expression).isEqualTo("run")
        assertThat(running.matched).isEqualTo("running")
        assertThat(running.trace.map { it.name }).containsExactly("ing")

        // "blorft" is only a form-of entry, which no rule of the language produces.
        val form = engine.lookup("blorft away", english).first()
        assertThat(form.term.expression).isEqualTo("blorf")
        assertThat(form.matched).isEqualTo("blorft")
        assertThat(form.trace.map { it.name }).containsExactly("past")
        assertThat(engine.lookup("blorft", english).map { it.term.expression }).doesNotContain("blorft")

        val phrase = engine.lookup("Give up the fight", english).map { it.term.expression }
        assertThat(phrase.first()).isEqualTo("give up")
        assertThat(phrase).contains("give")
    }

    @Test
    fun cutTableFailsTheCompletenessCheck() = runTest {
        val imported = engine.import(englishDictionary(), File(root, "out"))
        val table = File(imported.directory, FORM_OF_TABLE)
        table.writeBytes(table.readBytes().copyOf(table.length().toInt() - 3))

        assertThat(engine.isComplete(imported.directory)).isFalse()
    }

    @Test
    fun dictionaryWithoutLanguageKeepsItsFormOfRows() = runTest {
        // Japanese lookups use it too, and they read form-of rows from the term banks.
        val imported = engine.import(
            englishDictionary(index = """{"title":"No Language","revision":"1","format":3}"""),
            File(root, "out"),
        )

        assertThat(File(imported.directory, FORM_OF_TABLE).exists()).isFalse()
        assertThat(engine.isComplete(imported.directory)).isTrue()
    }

    private fun englishDictionary(
        index: String = """{"title":"Test English","revision":"1","format":3,"sourceLanguage":"en"}""",
    ): File = zip(
        "en.zip",
        "index.json" to index.toByteArray(),
        "term_bank_1.json" to """
            [
              ["run","","","v",0,["to move fast on foot"],1,""],
              ["give","","","v",0,["to hand over"],2,""],
              ["give up","","","v",0,["to surrender"],3,""],
              ["blorf","","","v",0,["to do a made-up thing"],4,""],
              ["blorft","","non-lemma","v",0,[["blorf",["past"]]],5,""]
            ]
        """.trimIndent().toByteArray(),
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
        /** The table's file name inside a dictionary directory (`HoshidictsEngine`). */
        const val FORM_OF_TABLE = "form_of.bin"
    }
}
