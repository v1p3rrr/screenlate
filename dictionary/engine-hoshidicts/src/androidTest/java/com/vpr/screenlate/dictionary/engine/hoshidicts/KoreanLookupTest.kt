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

/**
 * Korean on the real engine: Yomitan's Korean bundle deinflects on jamo and joins them back, Wiktionary's form-of
 * entries lead to their lemmas, and the Hanja dictionary the app carries gives character entries.
 */
@RunWith(AndroidJUnit4::class)
class KoreanLookupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private val engine = HoshidictsEngine(context)
    private val korean = LookupOptions(language = Language.KOREAN)

    @Before
    fun setUp() {
        root = File(context.cacheDir, "korean-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun verbAndAdjectiveFormsLeadToTheirDictionaryForms() = runTest {
        load(learnersDictionary())

        val past = engine.lookup("먹었습니다", korean).first()
        assertThat(past.term.expression).isEqualTo("먹다")
        assertThat(past.matched).isEqualTo("먹었습니다")
        assertThat(past.trace).isNotEmpty()

        val connective = engine.lookup("공부하고 있어요", korean).first()
        assertThat(connective.term.expression).isEqualTo("공부하다")
        assertThat(connective.matched).isEqualTo("공부하고")

        assertThat(engine.lookup("좋네요", korean).first().term.expression).isEqualTo("좋다")
    }

    @Test
    fun aNounIsFoundWithoutItsParticle() = runTest {
        load(learnersDictionary())

        val noun = engine.lookup("한국어를 배워요", korean).first()
        assertThat(noun.term.expression).isEqualTo("한국어")
        assertThat(noun.matched).isEqualTo("한국어")
        // A noun takes no verb endings: 사람다 is no form of 사람.
        assertThat(engine.lookup("사람이", korean).map { it.term.expression }).containsExactly("사람")
    }

    @Test
    fun hanjaSpellingsAndFormOfEntriesLeadToTheHangulWord() = runTest {
        load(learnersDictionary(), wiktionary())

        assertThat(engine.lookup("韓國語", korean).map { it.term.expression }).contains("韓國語")
        val form = engine.lookup("犬", korean).first()
        assertThat(form.term.expression).isEqualTo("견")
        assertThat(form.trace.map { it.name }).containsExactly("hanja form")
    }

    @Test
    fun theBundledHanjaDictionaryGivesKoreanReadings() = runTest {
        val archive = File(root, "hanja.zip")
        context.assets.open("catalog/unihan-hanja.zip").use { input -> archive.outputStream().use { input.copyTo(it) } }
        val imported = engine.import(archive, File(root, "hanja"))
        assertThat(imported.metadata.kanjiCount).isGreaterThan(8000L)
        engine.load(DictionarySet(kanji = listOf(imported.directory)))

        val country = engine.kanji("國").entries.single()
        assertThat(country.onyomi).isEqualTo("국")
        assertThat(country.definitions).contains("nation, country, nation-state")
        assertThat(country.stats["strokes"]).isEqualTo("11")
        // Readings on the education list come first.
        assertThat(engine.kanji("樂").entries.single().onyomi).isEqualTo("락 낙 악 요")
    }

    private suspend fun load(vararg archives: File) {
        val directories = archives.mapIndexed { i, archive -> engine.import(archive, File(root, "out$i")).directory }
        engine.load(DictionarySet(terms = directories))
    }

    /** Rows as KRDICT has them: parts of speech `v` and `adj`, nouns without one, hanja spellings as headwords. */
    private fun learnersDictionary(): File = zip(
        "krdict.zip",
        "index.json" to """{"title":"Test KRDICT","revision":"1","format":3,"targetLanguage":"ko"}""",
        "term_bank_1.json" to """
            [
              ["먹다","","Verb","v",0,["eat"],1,""],
              ["공부하다","","Verb","v",0,["study"],2,""],
              ["공부","","Noun","",0,["study"],3,""],
              ["좋다","","Adjective","adj",0,["good"],4,""],
              ["한국어","","Noun","",0,["Korean language"],5,""],
              ["韓國語","","Noun","",0,["Korean language"],6,""],
              ["사람","","Noun","",0,["person"],7,""],
              ["를","","Postpositional Particle","",0,["object particle"],8,""]
            ]
        """.trimIndent(),
    )

    /** A Wiktionary export: a lemma and a form-of entry for its hanja spelling. */
    private fun wiktionary(): File = zip(
        "wty.zip",
        "index.json" to """{"title":"Test wty-ko-en","revision":"1","format":3,"sourceLanguage":"ko","targetLanguage":"en"}""",
        "term_bank_1.json" to """
            [
              ["견","","n","",0,["dog"],1,""],
              ["犬","","non-lemma","",0,[["견",["hanja form"]]],0,""]
            ]
        """.trimIndent(),
    )

    private fun zip(name: String, vararg entries: Pair<String, String>): File {
        val file = File(root, name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((path, text) in entries) {
                out.putNextEntry(ZipEntry(path))
                out.write(text.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }
}
