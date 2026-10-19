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
    fun phrasalVerbsWithoutPartOfSpeechMatchTheirFormsWithAdverbParticles() = runTest {
        val imported = engine.import(phrasalDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))

        val past = engine.lookup("picked up the phone", english).first()
        assertThat(past.term.expression).isEqualTo("pick up")
        assertThat(past.matched).isEqualTo("picked up")
        assertThat(past.trace.map { it.name }).containsExactly("past")
        assertThat(engine.lookup("picking up", english).first().term.expression).isEqualTo("pick up")
        assertThat(engine.lookup("picks up", english).first().term.expression).isEqualTo("pick up")

        // A preposition after the verb is mostly its object, not a particle: "close to" means "near".
        val preposition = engine.lookup("closed to traffic", english)
        assertThat(preposition.first().term.expression).isEqualTo("close")
        assertThat(preposition.map { it.term.expression }).doesNotContain("close to")
        assertThat(engine.lookup("looked for it", english).map { it.term.expression }).doesNotContain("look for")

        // The interposed-object rule drops any words in between; it matches typed phrasal verbs only, as in Yomitan.
        val long = english.copy(scanLength = 32)
        assertThat(engine.lookup("picked the book up", long).map { it.term.expression }).doesNotContain("pick up")
        assertThat(engine.lookup("time is running out", long).map { it.term.expression }).doesNotContain("time out")
        val typed = engine.lookup("turned the light off", long).first()
        assertThat(typed.term.expression).isEqualTo("turn off")
        assertThat(typed.trace.map { it.name }).contains("interposed object")
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

    @Test
    fun stressMarksOfFormsAndTheirLemmasAreDroppedAtImport() = runTest {
        val imported = engine.import(russianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val russian = LookupOptions(language = Language.RUSSIAN)

        // The form names its lemma stressed; the dictionary's headword is not.
        assertThat(engine.lookup("почти", russian).map { it.term.expression }).contains("почтить")
        // A stressed form is found in plain text and as written.
        assertThat(engine.lookup("пальца", russian).first().term.expression).isEqualTo("палец")
        assertThat(engine.lookup("па́льца", russian).first().term.expression).isEqualTo("палец")
        // The diaeresis of ё is a letter's, not a stress mark.
        assertThat(engine.lookup("ёжика", russian).first().term.expression).isEqualTo("ёжик")
    }

    @Test
    fun formStepsDoNotLeadBackToTheText() = runTest {
        val imported = engine.import(
            zip(
                "names.zip",
                "index.json" to """{"title":"Test Names","revision":"1","format":3,"sourceLanguage":"en"}""".toByteArray(),
                "term_bank_1.json" to """
                    [
                      ["Jansen","","","n",0,["a surname"],1,""],
                      ["Jansen","","non-lemma","n",0,[["Janssen",["alternative form"]]],2,""],
                      ["Janssen","","non-lemma","n",0,[["Jansen",["alternative"]]],3,""]
                    ]
                """.trimIndent().toByteArray(),
            ),
            File(root, "out"),
        )
        engine.load(DictionarySet(terms = listOf(imported.directory)))

        // Jansen → Janssen → Jansen is no form of the word.
        val direct = engine.lookup("Jansen", english).first()
        assertThat(direct.term.expression).isEqualTo("Jansen")
        assertThat(direct.trace).isEmpty()
        assertThat(direct.otherTraces).isEmpty()
        val form = engine.lookup("Janssen", english).first()
        assertThat(form.term.expression).isEqualTo("Jansen")
        assertThat(form.trace.map { it.name }).containsExactly("alternative")
    }

    @Test
    fun serboCroatianWithoutDiacriticsFindsTheLettersInEitherEntry() = runTest {
        val imported = engine.import(serboCroatianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val latin = LookupOptions(language = Language.SERBO_CROATIAN_LATIN)
        val cyrillic = LookupOptions(language = Language.SERBO_CROATIAN_CYRILLIC)

        assertThat(engine.lookup("kuca", latin).map { it.term.expression }).contains("kuća")
        assertThat(engine.lookup("sesir", latin).first().term.expression).isEqualTo("šešir")
        assertThat(engine.lookup("djak", latin).first().term.expression).isEqualTo("đak")
        // A form and its lemma lose their accent marks at import; plain text finds the form in both entries.
        for (options in listOf(cyrillic, latin)) {
            val form = engine.lookup("куће", options).first()
            assertThat(form.term.expression).isEqualTo("кућа")
            assertThat(form.trace.map { it.name }).containsExactly("genitive singular")
        }
        // Text written with the marks of readings, in either script.
        assertThat(engine.lookup("ку̏ћа", cyrillic).first().term.expression).isEqualTo("кућа")
        assertThat(engine.lookup("kȕća", latin).first().term.expression).isEqualTo("kuća")
        // More variants than Yomitan keeps: it warns about the cut through a console, which QuickJS lacks.
        assertThat(engine.lookup("cecescecescecesc", latin)).isEmpty()
    }

    private fun serboCroatianDictionary(): File = zip(
        "sh.zip",
        "index.json" to """{"title":"Test Serbo-Croatian","revision":"1","format":3,"sourceLanguage":"sh"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["kuća","","","n",0,["house"],1,""],
              ["kuca","","","n",0,["a made-up word"],2,""],
              ["šešir","","","n",0,["hat"],3,""],
              ["đak","","","n",0,["pupil"],4,""],
              ["кућа","","","n",0,["house"],5,""],
              ["ку̏ће","","non-lemma","n",0,[["ку̏ћа",["genitive singular"]]],6,""]
            ]
        """.trimIndent().toByteArray(),
    )

    private fun russianDictionary(): File = zip(
        "ru.zip",
        "index.json" to """{"title":"Test Russian","revision":"1","format":3,"sourceLanguage":"ru"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["почтить","","","v",0,["to honor"],1,""],
              ["почти","","","adv",0,["almost"],2,""],
              ["почти","","non-lemma","v",0,[["почти́ть",["imperative"]]],3,""],
              ["палец","","","n",0,["finger"],4,""],
              ["па́льца","","non-lemma","n",0,[["палец",["genitive"]]],5,""],
              ["ёжик","","","n",0,["hedgehog"],6,""],
              ["ёжика","","non-lemma","n",0,[["ё́жик",["genitive"]]],7,""]
            ]
        """.trimIndent().toByteArray(),
    )

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

    /** Phrases without a part of speech, as Wiktionary exports often have them, and one typed phrasal verb. */
    private fun phrasalDictionary(): File = zip(
        "phrasal.zip",
        "index.json" to """{"title":"Test Phrases","revision":"1","format":3,"sourceLanguage":"en"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["pick","","","v",0,["to choose"],1,""],
              ["pick up","","","",0,["to lift"],2,""],
              ["close","","","v",0,["to shut"],3,""],
              ["close to","","","",0,["near"],4,""],
              ["look","","","v",0,["to see"],5,""],
              ["look for","","","",0,["to search"],6,""],
              ["time","","","n",0,["duration"],7,""],
              ["time out","","","",0,["a break"],8,""],
              ["turn","","","v",0,["to rotate"],9,""],
              ["turn off","","","v",0,["to switch off"],10,""]
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
