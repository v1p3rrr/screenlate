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
    fun croatianAndSerbianWithoutDiacriticsFindTheLettersInEitherScript() = runTest {
        val imported = engine.import(serboCroatianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val latin = LookupOptions(language = Language.CROATIAN)
        val cyrillic = LookupOptions(language = Language.SERBIAN)

        assertThat(engine.lookup("kuca", latin).map { it.term.expression }).contains("kuća")
        // The headword itself, with or without its marks, shows no tags of a form with its letters (kuća, genitive
        // plural of kuća); a form with other letters keeps them, and so does the same text as a form of another word.
        for (text in listOf("kuća", "kuca")) {
            val house = engine.lookup(text, latin).first { it.term.expression == "kuća" }
            assertThat(house.trace).isEmpty()
            assertThat(house.otherTraces).isEmpty()
        }
        val houses = engine.lookup("kućama", latin).first()
        assertThat(houses.term.expression).isEqualTo("kuća")
        assertThat(houses.trace.map { it.name }).containsExactly("dative plural")
        val am = engine.lookup("sam", latin)
        assertThat(am.first { it.term.expression == "sam" }.trace).isEmpty()
        assertThat(am.first { it.term.expression == "biti" }.trace.map { it.name })
            .containsExactly("first-person singular present")
        assertThat(engine.lookup("sesir", latin).first().term.expression).isEqualTo("šešir")
        assertThat(engine.lookup("djak", latin).first().term.expression).isEqualTo("đak")
        // A form and its lemma lose their accent marks at import; plain text finds the form in both languages.
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

    @Test
    fun ukrainianDeinflectsDropsStressAndMatchesEveryApostrophe() = runTest {
        val imported = engine.import(ukrainianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val ukrainian = LookupOptions(language = Language.UKRAINIAN)

        assertThat(engine.lookup("книги", ukrainian).map { it.term.expression }).contains("книга")
        assertThat(engine.lookup("зробив", ukrainian).map { it.term.expression }).contains("зробити")
        for (text in listOf("п'ять", "п’ять", "пʼять")) {
            assertThat(engine.lookup(text, ukrainian).first().term.expression).isEqualTo("п'ять")
        }
        assertThat(engine.lookup("сім'я", ukrainian).first().term.expression).isEqualTo("сімʼя")
        // A form with a secondary stress mark is found in plain text, beside Yomitan's rule for the same ending.
        val form = engine.lookup("автостради", ukrainian).first()
        assertThat(form.term.expression).isEqualTo("автострада")
        assertThat((form.otherTraces + listOf(form.trace)).map { trace -> trace.map { it.name } }).contains(listOf("genitive"))
        assertThat(engine.lookup("чита́ю", ukrainian).first().term.expression).isEqualTo("читати")
        // Yomitan takes книга for the plural of книго, the vocative of книга: no chain leads back to the text.
        val lemma = engine.lookup("книга", ukrainian).first()
        assertThat(lemma.term.expression).isEqualTo("книга")
        assertThat(lemma.trace).isEmpty()
        assertThat(lemma.otherTraces).isEmpty()
        val vocative = engine.lookup("книго", ukrainian).first()
        assertThat((vocative.otherTraces + listOf(vocative.trace)).map { trace -> trace.map { it.name } })
            .contains(listOf("vocative singular"))
    }

    @Test
    fun belarusianDropsStressAndMatchesEveryApostrophe() = runTest {
        val imported = engine.import(belarusianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val belarusian = LookupOptions(language = Language.BELARUSIAN)

        assertThat(engine.lookup("сям’я", belarusian).first().term.expression).isEqualTo("сям'я")
        val form = engine.lookup("вады", belarusian).first()
        assertThat(form.term.expression).isEqualTo("вада")
        assertThat(form.trace.map { it.name }).containsExactly("genitive")
        assertThat(engine.lookup("ва́ды", belarusian).first().term.expression).isEqualTo("вада")
    }

    @Test
    fun greekWithoutTonosFindsHeadwordsWithItAndWithTonosOnlyItself() = runTest {
        val imported = engine.import(greekDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val greek = LookupOptions(language = Language.GREEK)

        for (text in listOf("σκυλος", "ΣΚΥΛΟΣ", "σκύλος")) {
            val dog = engine.lookup(text, greek).first()
            assertThat(dog.term.expression).isEqualTo("σκύλος")
            assertThat(dog.trace).isEmpty()
            assertThat(dog.otherTraces).isEmpty()
        }
        assertThat(engine.lookup("ποτε", greek).map { it.term.expression }).containsAtLeast("ποτέ", "πότε")
        assertThat(engine.lookup("πότε", greek).map { it.term.expression }).doesNotContain("ποτέ")
        // A form keeps its accented lemma.
        val genitive = engine.lookup("ΣΚΥΛΟΥ", greek).first()
        assertThat(genitive.term.expression).isEqualTo("σκύλος")
        assertThat(genitive.trace.map { it.name }).containsExactly("genitive", "singular")
        assertThat(engine.lookup("καλη ορεξη", greek).first().term.expression).isEqualTo("καλή όρεξη")
        // Yomitan's ξανα- rule leads to the form, which leads to its lemma.
        assertThat(engine.lookup("ξαναρώτησε", greek).map { it.term.expression }).contains("ρωτάω")
        assertThat(engine.lookup("πρόσωπό", greek).first().term.expression).isEqualTo("πρόσωπο")
    }

    @Test
    fun greekDictionaryWithoutFormsGetsATableOfHeadwordsWithoutTonos() = runTest {
        val imported = engine.import(
            zip(
                "el-main.zip",
                "index.json" to """{"title":"Test Greek Words","revision":"1","format":3,"sourceLanguage":"el"}""".toByteArray(),
                "term_bank_1.json" to """[["νερό","","","n",0,["water"],1,""]]""".toByteArray(),
            ),
            File(root, "out"),
        )
        assertThat(File(imported.directory, FORM_OF_TABLE).isFile).isTrue()
        assertThat(engine.isComplete(imported.directory)).isTrue()
        engine.load(DictionarySet(terms = listOf(imported.directory)))

        assertThat(engine.lookup("ΝΕΡΟ", LookupOptions(language = Language.GREEK)).first().term.expression).isEqualTo("νερό")
    }

    @Test
    fun hindiWithoutNuktaOrWithAnusvaraFindsHeadwordsWithThemAndWithNuktaOnlyItself() = runTest {
        val imported = engine.import(hindiDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val hindi = LookupOptions(language = Language.HINDI)
        suspend fun first(text: String) = engine.lookup(text, hindi).first().term.expression

        // Forms are found with and without nukta, and with anusvara for candrabindu, each way round.
        for (text in listOf("पढ़ता", "पढता")) assertThat(first(text)).isEqualTo("पढ़ना")
        for (text in listOf("लड़कियाँ", "लड़कियां", "लडकियाँ", "लडकियां")) assertThat(first(text)).isEqualTo("लड़की")
        // Headwords with definitions as well, as forms of themselves with no steps.
        val am = engine.lookup("हूं", hindi).first()
        assertThat(am.term.expression).isEqualTo("हूँ")
        assertThat(am.trace).isEmpty()
        assertThat(first("मां")).isEqualTo("माँ")
        // Candrabindu written for anusvara.
        assertThat(first("हैँ")).isEqualTo("हैं")
        // A word without nukta finds both spellings, one with nukta only itself.
        assertThat(engine.lookup("खाना", hindi).map { it.term.expression }).containsAtLeast("खाना", "ख़ाना")
        assertThat(engine.lookup("ख़ाना", hindi).map { it.term.expression }).doesNotContain("खाना")
        // A letter with nukta as one character (U+0959) reads as the headword's two.
        assertThat(engine.lookup("\u0959ाना", hindi).map { it.term.expression }).contains("ख़ाना")
    }

    @Test
    fun bengaliEndingsFindEntriesWithoutPartOfSpeechAndBrokenFormRowsAreCleanedAtImport() = runTest {
        val imported = engine.import(bengaliDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val bengali = LookupOptions(language = Language.BENGALI)
        suspend fun first(text: String) = engine.lookup(text, bengali).first().term.expression

        // An ending finds a headword without a part of speech, in text composed as headwords are too (ো as ে + া).
        val book = engine.lookup("বইটির দাম", bengali).first()
        assertThat(book.term.expression).isEqualTo("বই")
        assertThat(book.matched).isEqualTo("বইটির")
        assertThat(book.trace.map { it.name }).containsExactly("-টির")
        assertThat(first("বইগুল\u09C7\u09BE")).isEqualTo("বই")
        // Forms written with a romanization after them, with two spellings or with the ending apart.
        assertThat(first("করেন")).isEqualTo("করা")
        assertThat(first("করিস")).isEqualTo("করা")
        assertThat(first("করো")).isEqualTo("করা")
        // A row of an unfilled template stands for no word.
        assertThat(engine.lookup("{{{3}}}ে", bengali)).isEmpty()
    }

    @Test
    fun georgianRulesFindEntriesWithoutPartOfSpeechAndMtavruliHeadingsFindTheOrdinaryLetters() = runTest {
        val imported = engine.import(georgianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val georgian = LookupOptions(language = Language.GEORGIAN)

        // The dative ending cut and the nominative -ი restored, on an entry without a part of speech.
        val book = engine.lookup("წიგნს ვკითხულობ", georgian).first()
        assertThat(book.term.expression).isEqualTo("წიგნი")
        assertThat(book.matched).isEqualTo("წიგნს")
        assertThat(book.trace.map { it.name }).containsExactly("nounAdjStemCompletion", "nounAdjSuffixStripping")
        // Two letters left are too few: მას is not cut down to the entry მა.
        assertThat(engine.lookup("მას", georgian)).isEmpty()
        assertThat(engine.lookup("ᲡᲐᲥᲐᲠᲗᲕᲔᲚᲝ", georgian).first().term.expression).isEqualTo("საქართველო")
    }

    @Test
    fun armenianMarksDropTheLigatureIsTriedAndClassicalSpellingsFindTheirWord() = runTest {
        val imported = engine.import(armenianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val armenian = LookupOptions(language = Language.ARMENIAN)
        suspend fun first(text: String) = engine.lookup(text, armenian).first()

        // A form written with the emphasis mark, found with and without it.
        val imperative = first("կարդա")
        assertThat(imperative.term.expression).isEqualTo("կարդալ")
        assertThat(imperative.trace.map { it.name }).containsExactly("imperative singular")
        assertThat(first("կարդա՛").term.expression).isEqualTo("կարդալ")
        // The question mark on the stressed vowel.
        val why = first("ինչո՞ւ ես")
        assertThat(why.term.expression).isEqualTo("ինչու")
        assertThat(why.matched).isEqualTo("ինչո՞ւ")
        assertThat(first("ԵՐԵՎԱՆ").term.expression).isEqualTo("Երևան")
        // "orthography spelling գիւղ" is keyed as գիւղ.
        val village = first("գիւղ")
        assertThat(village.term.expression).isEqualTo("գյուղ")
        assertThat(village.trace.map { it.name }).containsExactly("traditional")
    }

    @Test
    fun thaiTextInAnotherCharacterOrderAndMaiYamokWithoutSpacesFindTheirWords() = runTest {
        val imported = engine.import(thaiDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val thai = LookupOptions(language = Language.THAI)
        suspend fun first(text: String) = engine.lookup(text, thai).first()

        // Letter by letter, the longest word first: Thai has no spaces between words.
        assertThat(first("ห้องสมุดเปิดเช้า").term.expression).isEqualTo("ห้องสมุด")
        // Sara am given as nikhahit + sara aa, the tone mark between them.
        val water = first("น\u0E4D\u0E49\u0E32เย็น")
        assertThat(water.term.expression).isEqualTo("น้ำ")
        assertThat(water.matched).isEqualTo("น\u0E4D\u0E49\u0E32")
        // Mai yamok right after its word, where the headword has a space before it.
        val children = first("เด็กๆ ชอบเล่น")
        assertThat(children.term.expression).isEqualTo("เด็ก ๆ")
        assertThat(children.matched).isEqualTo("เด็กๆ")
        // The abbreviation with paiyannoi leads to the full name.
        assertThat(engine.lookup("กรุงเทพฯ", thai).map { it.term.expression }).contains("กรุงเทพมหานคร")
    }

    @Test
    fun khmerSubscriptTaAndDaFindEachOtherAndZeroWidthSpacesAreDropped() = runTest {
        val imported = engine.import(khmerDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val khmer = LookupOptions(language = Language.KHMER)
        suspend fun first(text: String) = engine.lookup(text, khmer).first()

        assertThat(first("សាលារៀនធំ").term.expression).isEqualTo("សាលារៀន")
        // សួស្ដី is written with subscript DA, ស្ត្រី with TA; each is found as typed the other way.
        assertThat(first("សួស្តី").term.expression).isEqualTo("សួស្ដី")
        assertThat(first("ស្ដ្រី").term.expression).isEqualTo("ស្ត្រី")
        assertThat(first("ស្ត្រី").term.expression).isEqualTo("ស្ត្រី")
        // A zero-width space between words; one that ends the match stays out of it.
        val school = first("សាលា\u200Bរៀន")
        assertThat(school.term.expression).isEqualTo("សាលារៀន")
        assertThat(school.matched).isEqualTo("សាលា\u200Bរៀន")
        val me = first("ខ្ញុំ\u200Bទៅ")
        assertThat(me.term.expression).isEqualTo("ខ្ញុំ")
        assertThat(me.matched).isEqualTo("ខ្ញុំ")
    }

    @Test
    fun arabicWithoutHamzaFindsHeadwordsWithItAndFormsAreCleanedAtImport() = runTest {
        val imported = engine.import(arabicDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val arabic = LookupOptions(language = Language.ARABIC)
        suspend fun first(text: String) = engine.lookup(text, arabic).first()
        suspend fun all(text: String) = engine.lookup(text, arabic).map { it.term.expression }

        // Text without hamza, with ي for a final ى and ه for a final ة, finds the headwords written by the norm.
        assertThat(first("اكبر").term.expression).isEqualTo("أكبر")
        assertThat(first("اسلام").term.expression).isEqualTo("إسلام")
        assertThat(first("علي").term.expression).isEqualTo("على")
        assertThat(first("لغه").term.expression).isEqualTo("لغة")
        // A bare alif finds either hamza; a word with hamza finds only itself.
        assertThat(all("امام")).containsAtLeast("أمام", "إمام")
        assertThat(all("أمام")).doesNotContain("إمام")
        // Vowel marks and tatweel in the text.
        assertThat(first("كِتَابٌ").term.expression).isEqualTo("كتاب")
        assertThat(first("كتـاب").term.expression).isEqualTo("كتاب")
        // The conjunction and the article cut off an adjective, which gives no part of speech.
        val big = first("والكبير جدا")
        assertThat(big.term.expression).isEqualTo("كبير")
        assertThat(big.matched).isEqualTo("والكبير")
        // A form naming a vowelled lemma finds the headword, its tags without the template name.
        val schools = first("مدارس")
        assertThat(schools.term.expression).isEqualTo("مدرسة")
        assertThat(schools.trace.map { it.name }).containsExactly("nominative plural broken-form construct diptote")
        assertThat(all("كتب")).containsAtLeast("كتب", "كتاب")
        // Romanized forms are dropped.
        assertThat(engine.lookup("kutub", arabic)).isEmpty()
    }

    @Test
    fun persianWithoutTheNonJoinerOrWithArabicLettersFindsItsWordsThroughOwnEndingsAndJoinedTries() = runTest {
        val imported = engine.import(persianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val persian = LookupOptions(language = Language.PERSIAN)
        suspend fun first(text: String) = engine.lookup(text, persian).first()

        // The non-joiner is dropped in the text and in headwords, so either spelling finds the word.
        assertThat(first("آنها").term.expression).isEqualTo("آن\u200Cها")
        assertThat(first("آن\u200Cها").term.expression).isEqualTo("آن\u200Cها")
        // A headword with the non-joiner and no form row of its spelling without it.
        assertThat(first("زیستشناسی").term.expression).isEqualTo("زیست\u200Cشناسی")
        // An ending after the non-joiner, read with the Arabic kaf; the entries give no part of speech.
        val books = first("کتاب\u200Cها")
        assertThat(books.term.expression).isEqualTo("کتاب")
        assertThat(books.matched).isEqualTo("کتاب\u200Cها")
        val theirBooks = first("كتابهایشان")
        assertThat(theirBooks.term.expression).isEqualTo("کتاب")
        assertThat(theirBooks.trace.map { it.name }).containsExactly("-ها", "-یشان")
        // The popup shows the rules' names and descriptions in the app's language, not the affixes.
        assertThat(theirBooks.trace.map { it.label }).containsNoneOf("", "-ها", "-یشان")
        assertThat(theirBooks.trace.map { it.description }).doesNotContain("")
        assertThat(first("درختان").term.expression).isEqualTo("درخت")
        assertThat(first("ستارگان").term.expression).isEqualTo("ستاره")
        assertThat(first("خانۀ").term.expression).isEqualTo("خانه")
        // A verb form through its form row, with the non-joiner, without it and with the prefix written apart.
        val want = first("میخواهم")
        assertThat(want.term.expression).isEqualTo("خواستن")
        assertThat(want.trace.map { it.name }).containsExactly("first-person singular present indicative")
        assertThat(first("می\u200Cخواهم").term.expression).isEqualTo("خواستن")
        val apart = first("می خواهم بروم")
        assertThat(apart.term.expression).isEqualTo("خواستن")
        assertThat(apart.matched).isEqualTo("می خواهم")
        val biggest = first("بزرگ ترین")
        assertThat(biggest.term.expression).isEqualTo("بزرگ")
        assertThat(biggest.matched).isEqualTo("بزرگ ترین")
        // One ending comes before two: خانه‌ای (a house) is خانه before خان through -ی and -ها.
        assertThat(first("خانه\u200Cای").term.expression).isEqualTo("خانه")
        // Cyrillic Tajik forms are not keyed.
        assertThat(engine.lookup("ҳалол", persian)).isEmpty()
        assertThat(first("حلال").term.expression).isEqualTo("حلال")
        // The spoken plural possessives: کتابمون (our book), خودشون (themselves).
        val ourBook = first("کتابمون")
        assertThat(ourBook.term.expression).isEqualTo("کتاب")
        assertThat(ourBook.trace.map { it.name }).containsExactly("-مون")
        assertThat(first("خودشون").term.expression).isEqualTo("خود")
        // A headword in -تون is itself, not زی through the ending.
        val olive = first("زیتون")
        assertThat(olive.term.expression).isEqualTo("زیتون")
        assertThat(olive.trace).isEmpty()
        // The ordinal in -مین after a number is its ordinal in -م; an Arabic plural in -ین is not cut.
        val second = first("دومین")
        assertThat(second.term.expression).isEqualTo("دوم")
        assertThat(second.trace.map { it.name }).containsExactly("-مین")
        assertThat(engine.lookup("مسلمین", persian).map { it.term.expression }).doesNotContain("مسلم")
        // The spoken copula is left out: خوبه does not find خوب.
        assertThat(engine.lookup("خوبه", persian).map { it.term.expression }).doesNotContain("خوب")
    }

    /**
     * Rows as Wiktionary's Persian exports write them: no part of speech, Latin readings, headwords with the non-joiner
     * (one with its spelling without it as a form row), a verb form, a Cyrillic Tajik form, a headword in -تون beside
     * the word it would leave, a number with its ordinal.
     */
    private fun persianDictionary(): File = zip(
        "fa.zip",
        "index.json" to """{"title":"Test Persian","revision":"1","format":3,"sourceLanguage":"fa"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["کتاب","kitāb","n","",0,["book"],1,""],
              ["آن${ZWNJ}ها","ân-hâ","pron","",0,["they"],2,""],
              ["آنها","","non-lemma","",0,[["آن${ZWNJ}ها",["alternative"]]],3,""],
              ["خواستن","xwāstan","v","",0,["to want"],4,""],
              ["میخواهم","","non-lemma","",0,[["خواستن",["first-person singular present indicative"]]],5,""],
              ["بزرگ","buzurg","adj","",0,["big"],6,""],
              ["درخت","daraxt","n","",0,["tree"],7,""],
              ["ستاره","sitāra","n","",0,["star"],8,""],
              ["خانه","xāna","n","",0,["house"],9,""],
              ["خان","xān","n","",0,["khan"],10,""],
              ["حلال","halâl","adj","",0,["halal"],11,""],
              ["ҳалол","","non-lemma","",0,[["حلال",["Cyrillic Tajik"]]],12,""],
              ["زیست${ZWNJ}شناسی","zīst-šināsī","n","",0,["biology"],13,""],
              ["خود","xwud","pron","",0,["self"],14,""],
              ["زیتون","zaytūn","n","",0,["olive"],15,""],
              ["زی","zī","n","",0,["manner"],16,""],
              ["دو","dū","num","",0,["two"],17,""],
              ["دوم","duvvum","adj","",0,["second"],18,""],
              ["مسلم","muslim","n","",0,["Muslim"],19,""],
              ["خوب","xūb","adj","",0,["good"],20,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun kazakhFindsItsWordsThroughEndingsAndWrittenWithTheRussianTwinsOfItsLetters() = runTest {
        val imported = engine.import(kazakhDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val kazakh = LookupOptions(language = Language.KAZAKH)
        suspend fun first(text: String) = engine.lookup(text, kazakh).first()
        suspend fun all(text: String) = engine.lookup(text, kazakh).map { it.term.expression }

        // Three endings on an entry whose part of speech is a tag of its glossary, as Wiktionary's exports give it.
        val books = first("кітаптарымызда")
        assertThat(books.term.expression).isEqualTo("кітап")
        assertThat(books.matched).isEqualTo("кітаптарымызда")
        assertThat(books.trace.map { it.name }).containsExactly("plural", "possessive 1pl", "locative")
        // The popup shows the rules' names and descriptions in the app's language.
        assertThat(books.trace.map { it.label }).doesNotContain("")
        assertThat(books.trace.map { it.description }).doesNotContain("")
        val went = first("бармадым")
        assertThat(went.term.expression).isEqualTo("бару")
        assertThat(went.trace.map { it.name }).containsExactly("negative", "past", "1sg")
        // A verb ending takes only the glossaries tagged as a verb: оқу is "to read" and "education".
        val reads = engine.lookup("оқиды", kazakh).filter { it.term.expression == "оқу" }
            .flatMap { result -> result.term.glossaries.map { it.content } }.joinToString()
        assertThat(reads).contains("to read")
        assertThat(reads).doesNotContain("education")
        assertThat(all("жазды")).containsAtLeast("жаз", "жазу")
        // Text in the Russian twins finds the Kazakh letters, its own spelling first; a Kazakh letter is exact.
        assertThat(all("он").first()).isEqualTo("он")
        assertThat(all("он")).containsExactly("он", "оң", "өң")
        assertThat(all("оң")).containsExactly("оң")
        val country = first("Казакстанның")
        assertThat(country.term.expression).isEqualTo("Қазақстан")
        assertThat(country.matched).isEqualTo("Казакстанның")
        assertThat(country.trace.map { it.name }).containsExactly("genitive")
        // Latin i and h typed for і and һ.
        assertThat(first("кiтап").term.expression).isEqualTo("кітап")
        assertThat(first("шаhар").term.expression).isEqualTo("шаһар")
        // A suffix entry from its hyphen.
        assertThat(first("-ға дейін").term.expression).isEqualTo("-ға")
    }

    /**
     * Rows as Wiktionary's Kazakh exports write them: the part of speech in the glossary's tags, no rules, no
     * readings; one headword as a noun and a verb, three words that differ only in Kazakh letters, a suffix entry.
     */
    private fun kazakhDictionary(): File = zip(
        "kk.zip",
        "index.json" to """{"title":"Test Kazakh","revision":"1","format":3,"sourceLanguage":"kk"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["кітап","","n","",0,["book"],1,""],
              ["оқу","","n","",0,["education"],2,""],
              ["оқу","","v","",0,["to read"],3,""],
              ["жаз","","n","",0,["summer"],4,""],
              ["жазу","","v","",0,["to write"],5,""],
              ["он","","num","",0,["ten"],6,""],
              ["оң","","adj","",0,["right"],7,""],
              ["өң","","n","",0,["complexion"],8,""],
              ["Қазақстан","","name","",0,["Kazakhstan"],9,""],
              ["-ға","","suffix","",0,["to (dative)"],10,""],
              ["шаһар","","n","",0,["city"],11,""],
              ["бару","","v","",0,["to go"],12,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun kyrgyzFindsItsWordsThroughEndingsInFourWayHarmonyAndWrittenWithTheRussianTwinsOfItsLetters() = runTest {
        val imported = engine.import(kyrgyzDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val kyrgyz = LookupOptions(language = Language.KYRGYZ)
        suspend fun first(text: String) = engine.lookup(text, kyrgyz).first()
        suspend fun all(text: String) = engine.lookup(text, kyrgyz).map { it.term.expression }

        val books = first("китептерибизде")
        assertThat(books.term.expression).isEqualTo("китеп")
        assertThat(books.trace.map { it.name }).containsExactly("plural", "possessive 1pl", "locative")
        assertThat(books.trace.map { it.label }).doesNotContain("")
        assertThat(books.trace.map { it.description }).doesNotContain("")
        assertThat(first("үйлөрдө").term.expression).isEqualTo("үй")
        // The question particle written onto the verb; a verb stem lengthens into the dictionary form.
        val going = first("барасыңбы")
        assertThat(going.term.expression).isEqualTo("баруу")
        assertThat(going.trace.map { it.name }).containsExactly("present", "2sg", "question")
        val reads = engine.lookup("окуйт", kyrgyz).filter { it.term.expression == "окуу" }
            .flatMap { result -> result.term.glossaries.map { it.content } }.joinToString()
        assertThat(reads).contains("to read")
        assertThat(reads).doesNotContain("education")
        // Text in the Russian twins finds the Kyrgyz letters, its own spelling first; a Kyrgyz letter is exact.
        assertThat(all("кол")).containsExactly("кол", "көл").inOrder()
        assertThat(all("көл")).containsExactly("көл")
        assertThat(first("Куз").term.expression).isEqualTo("күз")
    }

    /** Rows as Wiktionary's Kyrgyz exports write them: one headword as a noun and a verb, two words that differ in ө. */
    private fun kyrgyzDictionary(): File = zip(
        "ky.zip",
        "index.json" to """{"title":"Test Kyrgyz","revision":"1","format":3,"sourceLanguage":"ky"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["китеп","","n","",0,["book"],1,""],
              ["үй","","n","",0,["house"],2,""],
              ["окуу","","n","",0,["education"],3,""],
              ["окуу","","v","",0,["to read"],4,""],
              ["баруу","","v","",0,["to go"],5,""],
              ["кол","","n","",0,["hand"],6,""],
              ["көл","","n","",0,["lake"],7,""],
              ["күз","","n","",0,["autumn"],8,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun swedishFindsFormRowsFromCapitalsAndKeepsTheMarksOfItsLetters() = runTest {
        val imported = engine.import(swedishDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val swedish = LookupOptions(language = Language.SWEDISH)
        suspend fun all(text: String) = engine.lookup(text, swedish).map { it.term.expression }

        val house = engine.lookup("Huset är stort", swedish).first()
        assertThat(house.term.expression).isEqualTo("hus")
        assertThat(house.matched).isEqualTo("Huset")
        assertThat(all("läser")).contains("läsa")
        // å and ä are letters of their own: a word without the ring or the dots is another word.
        assertThat(all("år")).containsExactly("år")
        assertThat(all("ar")).containsExactly("ar")
        assertThat(all("lasa")).isEmpty()
    }

    /** Rows as Wiktionary's Swedish exports write them: form rows naming their lemma, two words that differ in å. */
    private fun swedishDictionary(): File = zip(
        "sv.zip",
        "index.json" to """{"title":"Test Swedish","revision":"1","format":3,"sourceLanguage":"sv"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["hus","","","n",0,["house"],1,""],
              ["huset","","non-lemma","n",0,[["hus",["definite","singular"]]],2,""],
              ["läsa","","","v",0,["to read"],3,""],
              ["läser","","non-lemma","v",0,[["läsa",["present"]]],4,""],
              ["år","","","n",0,["year"],5,""],
              ["ar","","","n",0,["are (unit of area)"],6,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun norwegianFindsGenitivesOfNounsNamesAndFormsByTheirTags() = runTest {
        val imported = engine.import(norwegianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val norwegian = LookupOptions(language = Language.NORWEGIAN)
        suspend fun first(text: String) = engine.lookup(text, norwegian).first()
        suspend fun all(text: String) = engine.lookup(text, norwegian).map { it.term.expression }

        // dagens: the genitive of dagen, a form of dag.
        val day = first("dagens")
        assertThat(day.term.expression).isEqualTo("dag")
        assertThat(day.matched).isEqualTo("dagens")
        assertThat(day.trace.first().name).isEqualTo("-s")
        assertThat(day.trace.first().label).isNotEmpty()
        assertThat(first("Norges").term.expression).isEqualTo("Norge")
        // A pronoun is no noun: hos does not reach ho ("she").
        assertThat(all("hos")).containsExactly("hos")
        // A word's own entry comes first; the genitive reaches the noun's senses only (men "harm", not "but").
        assertThat(all("mens").first()).isEqualTo("mens")
        val harm = engine.lookup("mens", norwegian).filter { it.term.expression == "men" }
            .flatMap { result -> result.term.glossaries.map { it.content } }.joinToString()
        assertThat(harm).contains("harm")
        assertThat(harm).doesNotContain("but")
        // å and ø are letters of their own.
        assertThat(all("år")).containsExactly("år")
        assertThat(all("ar")).isEmpty()
    }

    /**
     * Rows as Wiktionary's Bokmål exports write them: the part of speech among the definition tags, no rules, forms
     * naming their lemma; one headword as a noun and a conjunction.
     */
    private fun norwegianDictionary(): File = zip(
        "nb.zip",
        "index.json" to """{"title":"Test Norwegian","revision":"1","format":3,"sourceLanguage":"nb"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["dag","","n masc","",0,["day"],1,""],
              ["dagen","","non-lemma","",0,[["dag",["singular","definite"]]],2,""],
              ["Norge","","name","",0,["Norway"],3,""],
              ["hos","","prep","",0,["at, with"],4,""],
              ["ho","","pron","",0,["she"],5,""],
              ["mens","","conj","",0,["while"],6,""],
              ["men","","n neut","",0,["harm"],7,""],
              ["men","","conj","",0,["but"],8,""],
              ["år","","n neut","",0,["year"],9,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun danishFindsFormRowsAndGenitivesFromCapitalsAndKeepsTheMarksOfItsLetters() = runTest {
        val imported = engine.import(danishDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val danish = LookupOptions(language = Language.DANISH)
        suspend fun all(text: String) = engine.lookup(text, danish).map { it.term.expression }

        val house = engine.lookup("Huset er stort", danish).first()
        assertThat(house.term.expression).isEqualTo("hus")
        assertThat(house.matched).isEqualTo("Huset")
        // Wiktionary lists Danish genitives as form rows.
        assertThat(all("Dagens")).contains("dag")
        assertThat(all("læser")).contains("læse")
        // æ, ø and å are letters of their own: a word without them is another word.
        assertThat(all("år")).containsExactly("år")
        assertThat(all("ar")).containsExactly("ar")
        assertThat(all("lase")).isEmpty()
    }

    /** Rows as Wiktionary's Danish exports write them: form rows naming their lemma, two words that differ in å. */
    private fun danishDictionary(): File = zip(
        "da.zip",
        "index.json" to """{"title":"Test Danish","revision":"1","format":3,"sourceLanguage":"da"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["hus","","n neut","",0,["house"],1,""],
              ["huset","","non-lemma","",0,[["hus",["nominative singular definite"]]],2,""],
              ["dag","","n","",0,["day"],3,""],
              ["dagens","","non-lemma","",0,[["dag",["genitive singular definite"]]],4,""],
              ["læse","","v","",0,["to read"],5,""],
              ["læser","","non-lemma","",0,[["læse",["present active"]]],6,""],
              ["år","","n neut","",0,["year"],7,""],
              ["ar","","n","",0,["are (unit of area)"],8,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun romanianFindsWordsWithAndWithoutMarksAndArticleEndingsOnlyWithoutAnEntry() = runTest {
        val imported = engine.import(romanianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val romanian = LookupOptions(language = Language.ROMANIAN)
        suspend fun first(text: String) = engine.lookup(text, romanian).first()
        suspend fun all(text: String) = engine.lookup(text, romanian).map { it.term.expression }

        // consiliul: no form row, the article ending reaches the noun.
        val council = first("Consiliul")
        assertThat(council.term.expression).isEqualTo("consiliu")
        assertThat(council.trace.first().name).isEqualTo("-ul")
        assertThat(council.trace.first().label).isNotEmpty()
        assertThat(all("procesul").first()).isEqualTo("proces")
        // Forms are stored without marks, lemmas keyed without them as well; cedilla letters read as comma ones.
        assertThat(all("numărul")).contains("număr")
        assertThat(all("dupa")).containsExactly("după")
        assertThat(all("şi")).containsExactly("și")
        assertThat(all("si")).containsExactly("și")
        assertThat(all("ŢARĂ")).containsExactly("țară")
        assertThat(all("tara")).containsExactly("țară")
        // A word with an entry of its own takes no ending's results; one without takes them.
        assertThat(all("casă")).containsExactly("casă")
        assertThat(all("casa")).containsExactly("casă")
        val found = engine.lookup("aflată", romanian).first { it.term.expression == "afla" }
        assertThat(found.trace.map { it.name }).contains("agreement")
    }

    /**
     * Rows as Wiktionary's Romanian exports write them: forms stored without marks, the spelling in the reading; a
     * participle as a form of its verb; a word that the agreement ending would reach from casă.
     */
    private fun romanianDictionary(): File = zip(
        "ro.zip",
        "index.json" to """{"title":"Test Romanian","revision":"1","format":3,"sourceLanguage":"ro"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["consiliu","","n neut","",0,["council"],1,""],
              ["proces","","n neut","",0,["trial"],2,""],
              ["procesul","","non-lemma","",0,[["proces",["nominative accusative singular definite"]]],3,""],
              ["număr","","n neut","",0,["number"],4,""],
              ["numarul","numărul","non-lemma","",0,[["număr",["nominative accusative singular definite"]]],5,""],
              ["după","","prep acc","",0,["after"],6,""],
              ["și","","conj","",0,["and"],7,""],
              ["țară","","n fem","",0,["country"],8,""],
              ["casă","","n fem","",0,["house"],9,""],
              ["cas","","adj","",0,["a word the agreement ending reaches from casă"],10,""],
              ["afla","","v","",0,["to find out"],11,""],
              ["aflat","","non-lemma","",0,[["afla",["past participle"]]],12,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun latinFindsLemmasNamedWithMacronsUAsVAndEncliticsOnlyWithoutAnEntry() = runTest {
        val imported = engine.import(latinDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val latin = LookupOptions(language = Language.LATIN)
        suspend fun first(text: String) = engine.lookup(text, latin).first()
        suspend fun all(text: String) = engine.lookup(text, latin).map { it.term.expression }

        // Forms name their lemmas with vowel lengths (amō), which the headwords lack; text with them finds them too.
        assertThat(all("amat")).contains("amo")
        assertThat(all("Romae")).contains("Roma")
        assertThat(all("Rōmae")).contains("Roma")
        // A u before a vowel is read as v, as scholarly editions write the consonant.
        assertThat(first("uita").term.expression).isEqualTo("vita")
        // An enclitic comes off where the text has no entry of its own: atque is not at + que.
        val more = first("magisque")
        assertThat(more.term.expression).isEqualTo("magis")
        assertThat(more.trace.map { it.name }).containsExactly("-que")
        assertThat(more.trace.first().description).isNotEmpty()
        assertThat(all("videsque")).contains("video")
        assertThat(all("atque")).containsExactly("atque")
    }

    /** Rows as Wiktionary's Latin exports write them: headwords without vowel lengths, lemma names of forms with them. */
    private fun latinDictionary(): File = zip(
        "la.zip",
        "index.json" to """{"title":"Test Latin","revision":"1","format":3,"sourceLanguage":"la"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["amo","amō","v","v",0,["to love"],1,""],
              ["amat","","non-lemma","v",0,[["amō",["third-person singular present active indicative"]]],2,""],
              ["magis","","adv not-comp","adv",0,["more"],3,""],
              ["at","","conj","",0,["but"],4,""],
              ["atque","","conj","",0,["and also"],5,""],
              ["vita","vīta","n decl-1 fem","n",0,["life"],6,""],
              ["Roma","Rōma","name decl-1 fem sg","",0,["Rome"],7,""],
              ["Romae","Rōmae","non-lemma","",0,[["Rōma",["genitive/dative/locative singular"]]],8,""],
              ["video","videō","v","v",0,["to see"],9,""],
              ["vides","vidēs","non-lemma","v",0,[["videō",["second-person singular present active indicative"]]],10,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun albanianEndingsFindOnlyWordsWithoutAnEntryTheFirstEndingThatReachesOne() = runTest {
        val imported = engine.import(albanianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val albanian = LookupOptions(language = Language.ALBANIAN)
        suspend fun all(text: String) = engine.lookup(text, albanian).map { it.term.expression }

        val important = engine.lookup("rëndësishme", albanian).single()
        assertThat(important.term.expression).isEqualTo("rëndësishëm")
        assertThat(important.trace.map { it.name }).containsExactly("-me")
        assertThat(important.trace.first().description).isNotEmpty()
        assertThat(all("Rëndësishme")).containsExactly("rëndësishëm")
        assertThat(all("moderne")).containsExactly("modern")
        assertThat(all("drejtësisë")).containsExactly("drejtësi")
        assertThat(all("botua")).containsExactly("botoj")
        assertThat(all("aleatët")).containsExactly("aleat")
        // The longest ending first: -eve gives tipar, and -ve's tipare, an entry too, is not tried.
        assertThat(all("tipareve")).containsExactly("tipar")
        // A word with an entry of its own takes no ending.
        assertThat(all("kryesore")).containsExactly("kryesore")
        // A noun ending in a case ending is not reached through its form-of entry: partneritet is not partnerit + -et.
        assertThat(all("partneritet")).isEmpty()
        assertThat(all("partnerit")).containsExactly("partner")
    }

    /** Rows as Wiktionary's Albanian exports write them: the part of speech in the rules, forms as form-of rows. */
    private fun albanianDictionary(): File = zip(
        "sq.zip",
        "index.json" to """{"title":"Test Albanian","revision":"1","format":3,"sourceLanguage":"sq"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["rëndësishëm","","adj","adj",0,["important"],1,""],
              ["modern","","adj","adj",0,["modern"],2,""],
              ["drejtësi","","n","n",0,["justice"],3,""],
              ["botoj","","v","v",0,["to publish"],4,""],
              ["aleat","","n","n",0,["ally"],5,""],
              ["tipar","","n","n",0,["feature"],6,""],
              ["tipare","","n","n",0,["features"],7,""],
              ["kryesor","","adj","adj",0,["main"],8,""],
              ["kryesore","","adj","adj",0,["main, feminine"],9,""],
              ["partner","","n","n",0,["partner"],10,""],
              ["partnerit","","non-lemma","n",0,[["partner",["definite genitive singular"]]],11,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun swahiliPrefixesComeOffFromTheOutsideInAndReachVerbsOnlyWhereTheTextHasNoEntry() = runTest {
        val imported = engine.import(swahiliDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val swahili = LookupOptions(language = Language.SWAHILI)
        suspend fun first(text: String) = engine.lookup(text, swahili).first()
        suspend fun all(text: String) = engine.lookup(text, swahili).map { it.term.expression }

        val read = first("alisoma")
        assertThat(read.term.expression).isEqualTo("soma")
        assertThat(read.matched).isEqualTo("alisoma")
        assertThat(read.trace.map { it.name }).containsExactly("past", "he/she").inOrder()
        // The popup shows the rules' names and descriptions in the app's language.
        assertThat(read.trace.map { it.label }).doesNotContain("")
        assertThat(read.trace.map { it.description }).doesNotContain("")
        assertThat(first("aliyemwona").trace.map { it.name })
            .containsExactly("object", "relative", "past", "he/she").inOrder()
        // Prefixes reach only the glossaries tagged as a verb: unga is "to join" and "flour".
        val joined = engine.lookup("aliunga", swahili).flatMap { result -> result.term.glossaries.map { it.content } }
            .joinToString()
        assertThat(joined).contains("to join")
        assertThat(joined).doesNotContain("flour")
        // Verbs written with a hyphen or with ku- are found too; the reading with fewer prefixes comes first.
        assertThat(all("aliandika")).containsExactly("-andika")
        assertThat(all("alikula").first()).isEqualTo("kula")
        // A one-syllable stem needs its ku- after a tense: Angela is no a-nge-la.
        assertThat(all("Angela")).isEmpty()
        // A word with an entry of its own takes no prefix: kupa is not ku- + pa.
        assertThat(all("kupa")).containsExactly("kupa")
        assertThat(all("ng’ombe")).containsExactly("ng'ombe")
    }

    /**
     * Rows as Wiktionary's Swahili exports write them: the part of speech in the glossary's tags, no rules; one
     * headword as a noun and a verb, a verb with a hyphen, one-syllable verbs with and without ku-.
     */
    private fun swahiliDictionary(): File = zip(
        "sw.zip",
        "index.json" to """{"title":"Test Swahili","revision":"1","format":3,"sourceLanguage":"sw"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["soma","","v","",0,["to read"],1,""],
              ["ona","","v","",0,["to see"],2,""],
              ["unga","","n","",0,["flour"],3,""],
              ["unga","","v","",0,["to join"],4,""],
              ["-andika","","v","",0,["to write"],5,""],
              ["kula","","v","",0,["to eat"],6,""],
              ["la","","v","",0,["to eat"],7,""],
              ["kupa","","v","",0,["to give"],8,""],
              ["pa","","v","",0,["to give"],9,""],
              ["ng'ombe","","n","",0,["cow"],10,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun welshMutationsAreUndoneUnderTheirNamesCommonWordsStayAndUnmarkedWordsFindMarkedOnesWithoutAnEntry() = runTest {
        val imported = engine.import(welshDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val welsh = LookupOptions(language = Language.WELSH)
        suspend fun first(text: String, options: LookupOptions = welsh) = engine.lookup(text, options).first()
        suspend fun all(text: String, options: LookupOptions = welsh) = engine.lookup(text, options).map { it.term.expression }

        // The rule and the dictionary's own row give one chain, under the rule's name, which the popup shows translated.
        val cat = first("nghath")
        assertThat(cat.term.expression).isEqualTo("cath")
        assertThat(cat.matched).isEqualTo("nghath")
        assertThat(cat.trace.map { it.name }).containsExactly("nasal mutation")
        assertThat(cat.otherTraces).isEmpty()
        assertThat(cat.trace.map { it.label }).doesNotContain("")
        assertThat(cat.trace.map { it.description }).doesNotContain("")
        // Words the dictionary does not list as mutated are found by the rule, capitals kept.
        assertThat(first("Nghymru").term.expression).isEqualTo("Cymru")
        assertThat(first("hysgol").trace.map { it.name }).containsExactly("h-prothesis")
        // A row whose lemma is a single letter (fara as the soft form of b) is left out, and so is a second mutation
        // (bara as the soft form of para).
        assertThat(all("fara")).containsExactly("bara")
        assertThat(first("fara").trace.map { it.name }).containsExactly("soft mutation")
        // The rows' own tag names the same rule as the rule: the aspirate row of the and the rule's reading are one.
        assertThat(first("the").trace.map { it.name }).containsExactly("aspirate mutation")
        // mae is "is", not bae "bay".
        assertThat(all("mae")).containsExactly("bod")
        assertThat(all("Mae")).containsExactly("bod")
        // ty finds tŷ, as people type it; twr has an entry of its own and finds only that.
        assertThat(all("ty")).containsExactly("tŷ")
        // ddwr would reach twr only through two mutations (dwr, the soft form of twr), so it finds dŵr; dwr itself finds
        // twr, its lemma, and not dŵr.
        assertThat(all("ddwr")).containsExactly("dŵr")
        assertThat(all("dwr")).containsExactly("twr")
        assertThat(all("twr")).containsExactly("twr")
        // tý, as recognition reads tŷ, finds it only in text read from a screenshot.
        assertThat(all("tý")).isEmpty()
        assertThat(all("tý", welsh.copy(fromOcr = true))).containsExactly("tŷ")
        assertThat(all("thý", welsh.copy(fromOcr = true))).containsExactly("tŷ")
        assertThat(first("mae’r").term.expression).isEqualTo("mae'r")
    }

    /**
     * Rows as Wiktionary's Welsh exports write them: no rules, mutated forms as form-of rows under the edition's tags
     * (`nasal mutation`, `soft`, `aspirate mutation`), one of them with a single-letter lemma; a word written with
     * and without the circumflex.
     */
    private fun welshDictionary(): File = zip(
        "cy.zip",
        "index.json" to """{"title":"Test Welsh","revision":"1","format":3,"sourceLanguage":"cy"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["cath","","n fem","",0,["cat"],1,""],
              ["nghath","","non-lemma","",0,[["cath",["nasal mutation"]]],2,""],
              ["Cymru","","name","",0,["Wales"],3,""],
              ["bara","","n masc","",0,["bread"],4,""],
              ["fara","","non-lemma","",0,[["bara",["soft mutation"]]],5,""],
              ["fara","","non-lemma","",0,[["b",["soft"]]],6,""],
              ["b","","char","",0,["the letter b"],7,""],
              ["ysgol","","n fem","",0,["school"],8,""],
              ["te","","n masc","",0,["tea"],9,""],
              ["the","","non-lemma","",0,[["te",["aspirate mutation"]]],10,""],
              ["bod","","verb","",0,["to be"],11,""],
              ["mae","","non-lemma","",0,[["bod",["third-person singular present"]]],12,""],
              ["bae","","n masc","",0,["bay"],13,""],
              ["tŷ","","n masc","",0,["house"],14,""],
              ["dŵr","","n masc","",0,["water"],15,""],
              ["ddŵr","","non-lemma","",0,[["dŵr",["soft mutation"]]],16,""],
              ["tŵr","","n masc","",0,["tower"],17,""],
              ["twr","","n masc","",0,["heap"],18,""],
              ["mae'r","","phrase","",0,["is the"],19,""],
              ["para","","n masc","",0,["continuance"],20,""],
              ["bara","","non-lemma","",0,[["para",["soft"]]],21,""],
              ["dwr","","non-lemma","",0,[["twr",["soft"]]],22,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun irishMutationsAreUndoneOnceUnderTheirNamesCommonWordsStayAndWordsAreLookedUpAsWritten() = runTest {
        val imported = engine.import(irishDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val irish = LookupOptions(language = Language.IRISH)
        suspend fun first(text: String) = engine.lookup(text, irish).first()
        suspend fun all(text: String) = engine.lookup(text, irish).map { it.term.expression }

        // The rule and the dictionary's own row give one chain, under the rule's name, which the popup shows translated.
        val woman = first("bhean")
        assertThat(woman.term.expression).isEqualTo("bean")
        assertThat(woman.trace.map { it.name }).containsExactly("lenition")
        assertThat(woman.otherTraces).isEmpty()
        assertThat(woman.trace.map { it.label }).doesNotContain("")
        assertThat(woman.trace.map { it.description }).doesNotContain("")
        assertThat(first("nGaillimh").trace.map { it.name }).containsExactly("eclipsis")
        // Words the dictionary does not list as mutated are found by the rules, the capital after the prefix kept.
        assertThat(first("tSín").term.expression).isEqualTo("Sín")
        assertThat(first("tSín").trace.map { it.name }).containsExactly("lenition")
        assertThat(first("t-uisce").trace.map { it.name }).containsExactly("t-prothesis")
        assertThat(first("hÉireann").trace.map { it.name }).contains("h-prothesis")
        assertThat(all("hÉireann")).containsExactly("Éire")
        assertThat(all("Bhí")).containsExactly("bí")
        // One mutation per word, and the French edition's rows from a radical to its mutated forms are left out.
        assertThat(all("mbmbean")).isEmpty()
        assertThat(all("abróimid")).containsExactly("abair")
        assertThat(first("abróimid").trace.map { it.name }).doesNotContain("radical")
        // The rules leave chun "towards" and thar "over" as written (these rows give no cun or tar).
        assertThat(all("chun")).containsExactly("chun")
        assertThat(all("thar")).containsExactly("thar")
        // Gaelic type and the other apostrophe.
        assertThat(all("ḃean")).containsExactly("bean")
        assertThat(first("d’fhág").term.expression).isEqualTo("fág")
        // Irish words are looked up with the fada as written.
        assertThat(all("fag")).isEmpty()
    }

    /**
     * Rows as Wiktionary's Irish exports write them: no rules, mutated forms as form-of rows under the English
     * edition's tags (`lenited form`, `eclipsis`) and a French `radical` row from a radical to its eclipsed form.
     */
    private fun irishDictionary(): File = zip(
        "ga.zip",
        "index.json" to """{"title":"Test Irish","revision":"1","format":3,"sourceLanguage":"ga"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["bean","","n","",0,["woman"],1,""],
              ["bhean","","non-lemma","",0,[["bean",["lenited form"]]],2,""],
              ["Gaillimh","","name","",0,["Galway"],3,""],
              ["nGaillimh","","non-lemma","",0,[["Gaillimh",["eclipsis"]]],4,""],
              ["Sín","","name","",0,["China"],5,""],
              ["uisce","","n","",0,["water"],6,""],
              ["Éire","","name","",0,["Ireland"],7,""],
              ["Éireann","","non-lemma","",0,[["Éire",["genitive singular"]]],8,""],
              ["bí","","v","",0,["to be"],9,""],
              ["abair","","v","",0,["to say"],10,""],
              ["abróimid","","non-lemma","",0,[["abair",["future first-person plural"]]],11,""],
              ["abróimid","","non-lemma","",0,[["n-abróimid",["radical"]]],12,""],
              ["n-abróimid","","non-lemma","",0,[["abróimid",["eclipsis"]]],13,""],
              ["chun","","prep","",0,["towards"],14,""],
              ["cun","","n","",0,["stoutness"],15,""],
              ["thar","","prep","",0,["over"],16,""],
              ["tar","","v","",0,["to come"],17,""],
              ["fág","","v","",0,["to leave"],18,""],
              ["d'fhág","","non-lemma","",0,[["fág",["past indicative analytic independent"]]],19,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun gaelicMutationsAreUndoneOnceUnderTheirNamesAcuteVowelsReadAsGraveAndUnmarkedWordsFindMarkedOnes() = runTest {
        val imported = engine.import(gaelicDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val gaelic = LookupOptions(language = Language.SCOTTISH_GAELIC)
        suspend fun first(text: String) = engine.lookup(text, gaelic).first()
        suspend fun all(text: String) = engine.lookup(text, gaelic).map { it.term.expression }

        // The rule and the dictionary's own row give one chain, under the rule's name, which the popup shows translated.
        val scotland = first("h-Alba")
        assertThat(scotland.term.expression).isEqualTo("Alba")
        assertThat(scotland.trace.map { it.name }).containsExactly("h-prothesis")
        assertThat(scotland.otherTraces).isEmpty()
        assertThat(scotland.trace.map { it.label }).doesNotContain("")
        assertThat(scotland.trace.map { it.description }).doesNotContain("")
        assertThat(first("bhean").trace.map { it.name }).containsExactly("lenition")
        // Words the dictionary does not list as mutated are found by the rules, capitals kept.
        assertThat(first("t-sràid").term.expression).isEqualTo("sràid")
        assertThat(first("t-sràid").trace.map { it.name }).containsExactly("lenition")
        assertThat(first("t-uisge").trace.map { it.name }).containsExactly("t-prothesis")
        assertThat(first("n-eilean").trace.map { it.name }).containsExactly("eclipsis")
        assertThat(first("Bhreatainn").term.expression).isEqualTo("Breatainn")
        // One mutation per word, and the rows from a radical to its mutated form are left out.
        assertThat(all("t-shràid")).isEmpty()
        assertThat(all("cill")).containsExactly("cill")
        // The rules leave chan "not" and bhathar "was" as written (these rows give no can or bathar).
        assertThat(all("chan")).containsExactly("chan")
        assertThat(all("bhathar")).containsExactly("bhathar")
        // The older spelling's acute is read as the grave, through a mutation too.
        assertThat(all("mór")).containsExactly("mòr")
        assertThat(all("mhór")).containsExactly("mòr")
        assertThat(all("Ór")).containsExactly("òr")
        // bata finds bàta, as people type it; cas has an entry of its own and finds only that.
        assertThat(all("bata")).containsExactly("bàta")
        assertThat(all("cas")).containsExactly("cas")
        assertThat(all("càs")).containsExactly("càs")
        assertThat(first("dh’fhàg").term.expression).isEqualTo("fàg")
    }

    /**
     * Rows as Wiktionary's Gaelic exports write them: no rules, mutated forms as form-of rows under the English
     * edition's tags (`lenition mutation`, `mutation prothesis-h`) and a `mutation-radical` row from a radical to its
     * lenited form; two words told apart by the grave accent alone.
     */
    private fun gaelicDictionary(): File = zip(
        "gd.zip",
        "index.json" to """{"title":"Test Gaelic","revision":"1","format":3,"sourceLanguage":"gd"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["bean","","n","",0,["woman"],1,""],
              ["bhean","","non-lemma","",0,[["bean",["lenition mutation"]]],2,""],
              ["Alba","","name","",0,["Scotland"],3,""],
              ["h-Alba","","non-lemma","",0,[["Alba",["mutation prothesis-h"]]],4,""],
              ["sràid","","n","",0,["street"],5,""],
              ["uisge","","n","",0,["water"],6,""],
              ["eilean","","n","",0,["island"],7,""],
              ["Breatainn","","name","",0,["Britain"],8,""],
              ["cill","","n","",0,["church"],9,""],
              ["cill","","non-lemma","",0,[["chill",["mutation mutation-radical"]]],10,""],
              ["chill","","non-lemma","",0,[["cill",["lenition mutation"]]],11,""],
              ["chan","","particle","",0,["not"],12,""],
              ["can","","v","",0,["to say"],13,""],
              ["bhathar","","v","",0,["was (impersonal)"],14,""],
              ["bathar","","n","",0,["goods"],15,""],
              ["mòr","","adj","",0,["big"],16,""],
              ["òr","","n","",0,["gold"],17,""],
              ["bàta","","n","",0,["boat"],18,""],
              ["cas","","n","",0,["foot"],19,""],
              ["càs","","n","",0,["difficulty"],20,""],
              ["fàg","","v","",0,["to leave"],21,""],
              ["dh'fhàg","","non-lemma","",0,[["fàg",["past"]]],22,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun finnishCliticsAndPossessiveSuffixesComeOffIntoTheListedFormsOnlyWhereTheTextHasNoEntry() = runTest {
        val imported = engine.import(finnishDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val finnish = LookupOptions(language = Language.FINNISH)
        suspend fun first(text: String) = engine.lookup(text, finnish).first()
        suspend fun all(text: String) = engine.lookup(text, finnish).map { it.term.expression }.distinct()

        // A clitic and a possessive suffix come off, then the dictionary's row gives the case.
        val house = first("talossanikin")
        assertThat(house.term.expression).isEqualTo("talo")
        assertThat(house.matched).isEqualTo("talossanikin")
        assertThat(house.trace.map { it.name }).containsAtLeast("possessive", "clitic").inOrder()
        assertThat(house.trace.map { it.label }).doesNotContain("")
        // The form row's step shows the dictionary's tags, with no description.
        assertThat(house.trace.filter { it.name in setOf("possessive", "clitic") }.map { it.description })
            .doesNotContain("")
        assertThat(all("talossakaan")).containsExactly("talo")
        assertThat(all("talokin")).containsExactly("talo")
        assertThat(all("talossaan")).containsExactly("talo")
        assertThat(all("onkohan")).containsExactly("olla")
        // Words and forms of their own take no suffix off: kuusi is no kuu with -si, taloon no talo with -on.
        assertThat(all("kuusi")).containsExactly("kuusi")
        assertThat(first("taloon").trace.map { it.name }).doesNotContain("possessive")
        assertThat(all("vesi")).containsExactly("vesi")
        // An ending after a colon: no dictionary has EU:n, so the word before the colon is found.
        val union = first("EU:n")
        assertThat(union.term.expression).isEqualTo("EU")
        assertThat(union.matched).isEqualTo("EU")
        assertThat(all("-vuotias")).containsExactly("-vuotias")
    }

    @Test
    fun finnishFormsImportedAloneLeadToTheEntriesOfAnotherDictionary() = runTest {
        val forms = engine.import(finnishDictionary(), File(root, "forms"), formsOnly = true)
        val russian = engine.import(finnishRussianDictionary(), File(root, "ru"))
        assertThat(forms.directory.name).endsWith(" (forms)")
        engine.load(DictionarySet(terms = listOf(russian.directory, forms.directory)))
        val finnish = LookupOptions(language = Language.FINNISH)
        val results = engine.lookup("talossanikin", finnish)
        assertThat(results.map { it.term.expression }.distinct()).containsExactly("talo")
        // The forms bring no definitions of their own.
        val glossaries = results.flatMap { result -> result.term.glossaries.map { it.content } }.joinToString()
        assertThat(glossaries).contains("дом")
        assertThat(glossaries).doesNotContain("house")
    }

    /**
     * Rows as Wiktionary's Finnish exports write them: no rules, the part of speech in the tags, case and verb forms as
     * form-of rows; a word that is also another word with a possessive suffix (kuusi, kuu), a suffix entry.
     */
    private fun finnishDictionary(): File = zip(
        "fi.zip",
        "index.json" to """{"title":"Test Finnish","revision":"1","format":3,"sourceLanguage":"fi"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["talo","","noun","",0,["house"],1,""],
              ["talossa","","non-lemma","",0,[["talo",["inessive singular"]]],2,""],
              ["taloon","","non-lemma","",0,[["talo",["illative singular"]]],3,""],
              ["kuusi","","num","",0,["six"],4,""],
              ["kuu","","noun","",0,["moon"],5,""],
              ["vesi","","noun","",0,["water"],6,""],
              ["olla","","verb","",0,["to be"],7,""],
              ["onko","","non-lemma","",0,[["olla",["third-person singular present interrogative"]]],8,""],
              ["EU","","name","",0,["European Union"],9,""],
              ["-vuotias","","suffix","",0,["-year-old"],10,""]
            ]
        """.trimIndent().toByteArray(),
    )

    /** A dictionary of a gloss language without forms, as Wiktionary's Russian edition writes Finnish. */
    private fun finnishRussianDictionary(): File = zip(
        "fi-ru.zip",
        "index.json" to """{"title":"Test Finnish Russian","revision":"1","format":3,"sourceLanguage":"fi"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["talo","","noun","",0,["дом"],1,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun hungarianVerbPrefixesComeOffIntoTheBareVerbsFormsOnlyWhereTheTextHasNoEntry() = runTest {
        val imported = engine.import(hungarianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val hungarian = LookupOptions(language = Language.HUNGARIAN)
        suspend fun all(text: String, options: LookupOptions = hungarian) =
            engine.lookup(text, options).map { it.term.expression }.distinct()

        // The tables list the bare verb's forms only: megtartottam is tartottam, a form of tart, with meg-.
        val kept = engine.lookup("megtartottam", hungarian).first()
        assertThat(kept.term.expression).isEqualTo("tart")
        assertThat(kept.matched).isEqualTo("megtartottam")
        assertThat(kept.trace.map { it.name }).contains("prefix")
        assertThat(kept.trace.map { it.label }).doesNotContain("")
        assertThat(all("olvasta")).containsExactly("olvas")
        // A word with its own entry takes no prefix off: megtart is no tart, bevétel no vétel (vesz).
        assertThat(all("megtart")).containsExactly("megtart")
        assertThat(all("bevétel")).containsExactly("bevétel")
        // The prefix comes off verbs only: kigén is no ki- with gén.
        assertThat(engine.lookup("kigén", hungarian)).isEmpty()
        assertThat(all("-ban")).containsExactly("-ban")

        // ő and ű that recognition read as ö and ü (ű also as ú) are tried for words from OCR only, the word as read first.
        val ocr = hungarian.copy(fromOcr = true)
        assertThat(engine.lookup("füzfa", hungarian)).isEmpty()
        assertThat(all("füzfa", ocr)).containsExactly("fűzfa")
        // The built-in recognizer also reads ű as ú.
        assertThat(all("fúzfa", ocr)).containsExactly("fűzfa")
        assertThat(engine.lookup("fúzfa", hungarian)).isEmpty()
        assertThat(all("öt")).containsExactly("öt")
        assertThat(all("öt", ocr)).containsExactly("öt", "őt").inOrder()
    }

    /**
     * Rows as Wiktionary's Hungarian export writes them: no rules, the part of speech in the tags, the bare verb's
     * forms as form-of rows; prefixed verbs and nouns of their own.
     */
    private fun hungarianDictionary(): File = zip(
        "hu.zip",
        "index.json" to """{"title":"Test Hungarian","revision":"1","format":3,"sourceLanguage":"hu"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["tart","","v","",0,["to hold"],1,""],
              ["tartottam","","non-lemma","",0,[["tart",["first-person singular definite/indefinite past indicative"]]],2,""],
              ["megtart","","v vt","",0,["to keep"],3,""],
              ["olvas","","v vt","",0,["to read"],4,""],
              ["olvasta","","non-lemma","",0,[["olvas",["third-person singular definite past indicative"]]],5,""],
              ["vesz","","v","",0,["to take"],6,""],
              ["vétel","","n","",0,["purchase"],7,""],
              ["vétel","","non-lemma","",0,[["vesz",["verbal noun"]]],8,""],
              ["bevétel","","n","",0,["revenue"],9,""],
              ["gén","","n","",0,["gene"],10,""],
              ["fűzfa","","n","",0,["willow"],11,""],
              ["öt","","num","",0,["five"],12,""],
              ["őt","","pron","",0,["him, her"],13,""],
              ["-ban","","suf","",0,["in"],14,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun estonianNegativeFormsOfOlemaFindTheirOwnEntryThenTheVerb() = runTest {
        val imported = engine.import(estonianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val estonian = LookupOptions(language = Language.ESTONIAN)
        suspend fun all(text: String, options: LookupOptions = estonian) =
            engine.lookup(text, options).map { it.term.expression }.distinct()

        // pole has an entry of its own, which comes first; olema follows through the negative rule.
        assertThat(all("pole")).containsExactly("pole", "olema").inOrder()
        val verb = engine.lookup("pole", estonian).first { it.term.expression == "olema" }
        assertThat(verb.trace.map { it.name }).containsExactly("negative")
        assertThat(verb.trace.map { it.label }).doesNotContain("")
        assertThat(all("Polnud")).containsExactly("polnud", "olema").inOrder()
        assertThat(all("poleks")).contains("olema")
        // The rule reaches verbs only, and whole words only.
        assertThat(all("on")).containsExactly("olema")
        assertThat(engine.lookup("polegi", estonian)).isEmpty()

        // õ that recognition read as ö is tried for words from OCR only, the word as read first.
        val ocr = estonian.copy(fromOcr = true)
        assertThat(engine.lookup("möte", estonian)).isEmpty()
        assertThat(all("möte", ocr)).containsExactly("mõte")
        assertThat(all("öö", ocr)).containsExactly("öö")
        assertThat(all("-ga")).containsExactly("-ga")
    }

    /**
     * Rows as Wiktionary's Estonian export writes them: no rules, the part of speech in the tags; pole and polnud
     * entries of their own, poleks a spelling of "ei oleks", olema's table as form-of rows.
     */
    private fun estonianDictionary(): File = zip(
        "et.zip",
        "index.json" to """{"title":"Test Estonian","revision":"1","format":3,"sourceLanguage":"et"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["olema","","v","",0,["to be"],1,""],
              ["on","","non-lemma","",0,[["olema",["third-person singular present indicative"]]],2,""],
              ["pole","","v abbv contr","",0,["is not"],3,""],
              ["polnud","","v contr","",0,["was not"],4,""],
              ["poleks","","non-lemma","",0,[["ei oleks",["alt-of"]]],5,""],
              ["ei oleks","","v","",0,["would not be"],6,""],
              ["mõte","","n","",0,["thought"],7,""],
              ["öö","","n","",0,["night"],8,""],
              ["-ga","","suf","",0,["with"],9,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun lithuanianFindsStressedFormsAndWordsWrittenWithoutTheMarksOfTheirLettersTheExactSpellingFirst() = runTest {
        val imported = engine.import(lithuanianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val lithuanian = LookupOptions(language = Language.LITHUANIAN)
        suspend fun first(text: String) = engine.lookup(text, lithuanian).first()
        suspend fun all(text: String) = engine.lookup(text, lithuanian).map { it.term.expression }

        // Forms are stored with their stress (žmogaũs), text has none; the dot over an accented i goes with it.
        assertThat(first("žmogaus").term.expression).isEqualTo("žmogus")
        assertThat(first("žmogaũs").term.expression).isEqualTo("žmogus")
        assertThat(all("bitės")).contains("bitė")
        // Lemma names of forms lose their stress (vanduõ), the headword has none.
        assertThat(all("vandens")).contains("vanduo")
        // Text without the marks of ą č ė š ž finds the words with them; the exact spelling comes first.
        assertThat(all("zmogaus")).contains("žmogus")
        assertThat(all("ZMOGUS")).contains("žmogus")
        assertThat(all("bites")).contains("bitė")
        assertThat(all("as")).containsExactly("aš")
        assertThat(all("karta")).containsExactly("karta", "kartą").inOrder()
        assertThat(all("kartą")).containsExactly("kartą")
        // The e with a dot is a letter of its own, not a stress mark.
        assertThat(all("bitė").first()).isEqualTo("bitė")
    }

    /**
     * Rows as Wiktionary's Lithuanian exports write them: forms with stress marks and the dot over an accented i, a lemma
     * name with stress, two words told apart by the ogonek alone.
     */
    private fun lithuanianDictionary(): File = zip(
        "lt.zip",
        "index.json" to """{"title":"Test Lithuanian","revision":"1","format":3,"sourceLanguage":"lt"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["žmogus","","n","",0,["human"],1,""],
              ["žmogaũs","","non-lemma","",0,[["žmogus",["genitive singular"]]],2,""],
              ["vanduo","","n masc","",0,["water"],3,""],
              ["vandens","","non-lemma","",0,[["vanduõ",["genitive singular"]]],4,""],
              ["bitė","","n fem","",0,["bee"],5,""],
              ["bi${"\u0307\u0300"}tės","","non-lemma","",0,[["bitė",["genitive singular"]]],6,""],
              ["karta","","n fem","",0,["generation"],7,""],
              ["kartą","","adv","",0,["once"],8,""],
              ["aš","","pron sg","",0,["I"],9,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun slovakFindsWordsWrittenWithoutTheirMarksTheExactSpellingFirst() = runTest {
        val imported = engine.import(slovakDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val slovak = LookupOptions(language = Language.SLOVAK)
        suspend fun all(text: String) = engine.lookup(text, slovak).map { it.term.expression }

        // Text without the acute, caron, circumflex or diaeresis finds the words with them.
        assertThat(all("robit")).contains("robiť")
        assertThat(all("ROBIT")).contains("robiť")
        assertThat(all("kon")).contains("kôň")
        assertThat(all("maso")).contains("mäso")
        assertThat(all("dakujem")).contains("ďakujem")
        assertThat(all("stlp")).containsExactly("stĺp")
        // Two words told apart by a mark alone: the exact spelling first, and a marked word finds only itself.
        assertThat(all("byt")).containsExactly("byt", "byť").inOrder()
        assertThat(all("byť")).containsExactly("byť")
        assertThat(all("ma")).containsExactly("ma", "má").inOrder()
        // The form's lemma is reached from the form typed without marks.
        assertThat(all("robil")).contains("robiť")
        // A lemma of the row's form-of item, reached through the same key, comes after the word itself as it does
        // from the word as written.
        assertThat(all("robit")).containsExactly("robiť", "urobiť").inOrder()
        assertThat(all("robiť")).containsExactly("robiť", "urobiť").inOrder()
    }

    /**
     * Rows as Wiktionary's Slovak exports write them: words told apart by a mark alone, a form of a marked lemma, a word
     * with definitions that is also a form of another (robiť, the imperfective of urobiť).
     */
    private fun slovakDictionary(): File = zip(
        "sk.zip",
        "index.json" to """{"title":"Test Slovak","revision":"1","format":3,"sourceLanguage":"sk"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["robiť","","v","",0,["to do",["urobiť",["imperfective"]]],1,""],
              ["urobiť","","v","",0,["to do, to make"],11,""],
              ["robil","","non-lemma","",0,[["robiť",["past masculine singular"]]],2,""],
              ["kôň","","n masc","",0,["horse"],3,""],
              ["mäso","","n neut","",0,["meat"],4,""],
              ["ďakujem","","intj","",0,["thank you"],5,""],
              ["stĺp","","n masc","",0,["pillar"],6,""],
              ["byt","","n masc","",0,["flat"],7,""],
              ["byť","","v","",0,["to be"],8,""],
              ["ma","","pron","",0,["me"],9,""],
              ["má","","v","",0,["has"],10,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun slovenianFindsTonemicFormsAndWordsWrittenWithoutTheCaronTheExactSpellingFirst() = runTest {
        val imported = engine.import(slovenianDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val slovenian = LookupOptions(language = Language.SLOVENIAN)
        suspend fun first(text: String) = engine.lookup(text, slovenian).first()
        suspend fun all(text: String) = engine.lookup(text, slovenian).map { it.term.expression }

        // Forms are stored in the tonemic notation (vôde, dẹ́lał, pə̏s), text has none of it: the marks drop, ł and ə
        // are read as l and e.
        assertThat(first("vode").term.expression).isEqualTo("voda")
        assertThat(first("vôde").term.expression).isEqualTo("voda")
        assertThat(all("delal")).contains("delati")
        assertThat(all("delajoč")).contains("delati")
        assertThat(all("pes")).containsExactly("pes")
        // Lemma names of forms lose their marks (bíti), the headword has none.
        assertThat(all("je")).contains("biti")
        // Text without the caron finds the words with it, after the other marks are gone and ł is read as l.
        assertThat(all("zena")).contains("žena")
        assertThat(all("ZENA")).contains("žena")
        assertThat(all("delajoc")).contains("delati")
        assertThat(all("zrl")).contains("žreti")
        assertThat(all("žrl")).contains("žreti")
        // Two words told apart by the caron alone: the exact spelling first, and a word with it finds only itself.
        assertThat(all("se")).containsExactly("se", "še").inOrder()
        assertThat(all("še")).containsExactly("še")
    }

    /**
     * Rows as Wiktionary's Slovenian exports write them: forms in the tonemic notation with ł and ə, a lemma name with
     * an acute, a form with the caron and ł, two words told apart by the caron alone.
     */
    private fun slovenianDictionary(): File = zip(
        "sl.zip",
        "index.json" to """{"title":"Test Slovenian","revision":"1","format":3,"sourceLanguage":"sl"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["voda","","n fem","",0,["water"],1,""],
              ["vôde","","non-lemma","",0,[["voda",["genitive singular"]]],2,""],
              ["delati","","v impf","",0,["to work"],3,""],
              ["d${"\u1EB9\u0301"}lał","","non-lemma","",0,[["delati",["singular masculine l-participle"]]],4,""],
              ["delaj${"\u1ECD\u0304"}č","","non-lemma","",0,[["delati",["present participle"]]],5,""],
              ["pes","","n animate masc","",0,["dog"],6,""],
              ["p${"\u0259\u030F"}s","","non-lemma","",0,[["pes",["vocative singular"]]],7,""],
              ["biti","","v","",0,["to be"],8,""],
              ["je","","non-lemma","",0,[["bíti",["third-person singular present"]]],9,""],
              ["žena","","n fem","",0,["woman"],10,""],
              ["žreti","","v impf","",0,["to devour"],11,""],
              ["žȓł","","non-lemma","",0,[["žreti",["singular masculine l-participle"]]],12,""],
              ["se","","pron","",0,["oneself"],13,""],
              ["še","","adv","",0,["still"],14,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun esperantoFindsWordsWrittenWithoutHatsOrInTheXSystemTheExactSpellingFirst() = runTest {
        val imported = engine.import(esperantoDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val esperanto = LookupOptions(language = Language.ESPERANTO)
        suspend fun all(text: String) = engine.lookup(text, esperanto).map { it.term.expression }

        // The x-system (cx for ĉ) and text without hats find the words with them.
        assertThat(all("cxu")).containsExactly("ĉu")
        assertThat(all("CXu")).containsExactly("ĉu")
        assertThat(all("cu")).containsExactly("ĉu")
        assertThat(all("CU")).containsExactly("ĉu")
        // Wiktionary's x-system rows lead to the same word once.
        assertThat(all("gxi")).containsExactly("ĝi")
        assertThat(all("gi")).containsExactly("ĝi")
        // Yomitan's endings come off text in any of the three spellings.
        assertThat(all("manĝaĵojn")).contains("manĝaĵo")
        assertThat(all("mangxajxojn")).contains("manĝaĵo")
        assertThat(all("mangajojn")).contains("manĝaĵo")
        // Two words told apart by a hat alone: the exact spelling first, and a word with it finds only itself.
        assertThat(all("si")).containsExactly("si", "ŝi").inOrder()
        assertThat(all("ŝi")).containsExactly("ŝi")
    }

    /**
     * Rows as Wiktionary's Esperanto exports write them: words with hats, an x-system row, a plural accusative form,
     * two words told apart by a hat alone.
     */
    private fun esperantoDictionary(): File = zip(
        "eo.zip",
        "index.json" to """{"title":"Test Esperanto","revision":"1","format":3,"sourceLanguage":"eo"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["ĉu","","ptcl","",0,["whether"],1,""],
              ["ĝi","","pron","",0,["it"],2,""],
              ["gxi","","non-lemma","",0,[["ĝi",["X-system"]]],3,""],
              ["manĝaĵo","","n","n",0,["food"],4,""],
              ["manĝaĵojn","","non-lemma","n",0,[["manĝaĵo",["accusative plural"]]],5,""],
              ["si","","pron","",0,["oneself"],6,""],
              ["ŝi","","pron","",0,["she"],7,""]
            ]
        """.trimIndent().toByteArray(),
    )

    @Test
    fun hebrewFindsItsWordsPointedOrNotAndAfterPrefixesInTheOrderOfTheGrammar() = runTest {
        val imported = engine.import(hebrewDictionary(), File(root, "out"))
        engine.load(DictionarySet(terms = listOf(imported.directory)))
        val hebrew = LookupOptions(language = Language.HEBREW)
        suspend fun first(text: String) = engine.lookup(text, hebrew).first()

        assertThat(first("כלב").term.expression).isEqualTo("כלב")
        // The headword itself shows no tags of a pointed form with its letters (שְׁלוֹם, the construct form of
        // שָׁלוֹם); text with the form's points is that form and keeps them.
        for (text in listOf("כלב", "שלום")) {
            assertThat(first(text).trace).isEmpty()
            assertThat(first(text).otherTraces).isEmpty()
        }
        val construct = first("שְׁלוֹם")
        assertThat(construct.term.expression).isEqualTo("שלום")
        assertThat(construct.trace.map { it.name }).containsExactly("singular construct")
        // Pointed text through the pointed form row and without its points.
        assertThat(first("כֶּלֶב").term.expression).isEqualTo("כלב")
        assertThat(first("הַכֶּלֶב").term.expression).isEqualTo("כלב")
        // Two prefixes, then a form whose lemma is named pointed; the entries give no part of speech.
        assertThat(first("והכלבים").term.expression).isEqualTo("כלב")
        val house = first("והבית")
        assertThat(house.term.expression).isEqualTo("בית")
        assertThat(house.matched).isEqualTo("והבית")
        assertThat(house.trace.map { it.name }).containsExactly("ה-", "ו-")
        // The popup shows the rules' names and descriptions in the app's language, not the prefixes.
        assertThat(house.trace.map { it.label }).containsNoneOf("", "ה-", "ו-")
        assertThat(house.trace.map { it.description }).doesNotContain("")
        assertThat(first("כשהגעתי").term.expression).isEqualTo("הגיע")
        // Three letters at most: ושמהבית would need ו ש מ and the article.
        assertThat(engine.lookup("ושמהבית", hebrew).map { it.term.expression }).doesNotContain("בית")
        // A letter and one more stay: לא is not א.
        assertThat(engine.lookup("לא", hebrew).map { it.term.expression }).containsExactly("לא")
        // ASCII quotes stand for the gershayim and the geresh.
        assertThat(first("צה\"ל").term.expression).isEqualTo("צה״ל")
        assertThat(first("ג'ירפה").term.expression).isEqualTo("ג׳ירפה")
        assertThat(first("בית־ספר").term.expression).isEqualTo("בית־ספר")
        // Forms in no Hebrew letter are not keyed.
        assertThat(engine.lookup("𐤊𐤋𐤁", hebrew)).isEmpty()
    }

    /**
     * Rows as Wiktionary's Hebrew exports write them: unpointed headwords without a part of speech or a reading,
     * pointed forms, a form naming its lemma pointed, a construct form with its maqaf, a construct form with the
     * letters of its headword (שְׁלוֹם of שלום), a Phoenician spelling.
     */
    private fun hebrewDictionary(): File = zip(
        "he.zip",
        "index.json" to """{"title":"Test Hebrew","revision":"1","format":3,"sourceLanguage":"he"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["כלב","","n masc","",0,["dog"],1,""],
              ["בית","","n masc","",0,["house"],2,""],
              ["בית־ספר","","n masc","",0,["school"],3,""],
              ["צה״ל","","name","",0,["IDF"],4,""],
              ["ג׳ירפה","","n fem","",0,["giraffe"],5,""],
              ["לא","","particle","",0,["not"],6,""],
              ["הגיע","","v","",0,["to arrive"],7,""],
              ["כֶּלֶב","","non-lemma","",0,[["כלב",["singular indefinite masculine"]]],8,""],
              ["כלבים","","non-lemma","",0,[["כֶּלֶב",["plural indefinite form"]]],9,""],
              ["בֵּית־","","non-lemma","",0,[["בית",["singular construct"]]],10,""],
              ["הגעתי","","non-lemma","",0,[["הגיע",["first-person singular past"]]],11,""],
              ["𐤊𐤋𐤁","","non-lemma","",0,[["כלב",["Phoenician"]]],12,""],
              ["שלום","","n masc","",0,["peace","hello"],13,""],
              ["שְׁלוֹם","","non-lemma","",0,[["שָׁלוֹם",["singular construct"]]],14,""]
            ]
        """.trimIndent().toByteArray(),
    )

    /**
     * Rows as Wiktionary's Arabic exports write them: unvowelled headwords with the vowelled spelling as the reading,
     * adjectives without a part of speech, forms naming vowelled lemmas, template names among tags, romanized forms.
     */
    private fun arabicDictionary(): File = zip(
        "ar.zip",
        "index.json" to """{"title":"Test Arabic","revision":"1","format":3,"sourceLanguage":"ar"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["كتاب","كِتَاب","n masc","n",0,["book"],1,""],
              ["كتب","كَتَبَ","v vt","v",0,["to write"],2,""],
              ["كبير","كَبِير","adj","",0,["big"],3,""],
              ["أكبر","أَكْبَر","adj","",0,["bigger"],4,""],
              ["إسلام","إِسْلَام","name","",0,["Islam"],5,""],
              ["على","عَلَى","prep","",0,["on"],6,""],
              ["لغة","لُغَة","n fem","n",0,["language"],7,""],
              ["أمام","أَمَامَ","prep","",0,["in front of"],8,""],
              ["إمام","إِمَام","n masc","n",0,["imam"],9,""],
              ["مدرسة","مَدْرَسَة","n fem","n",0,["school"],10,""],
              ["كتب","كُتُب","non-lemma","n",0,[["كِتَاب",["plural"]]],11,""],
              ["مدارس","مَدَارِسُ","non-lemma","n",0,[["مَدْرَسَة",["nominative plural broken-form construct ar-infl-a diptote"]]],12,""],
              ["kutub","","non-lemma","n",0,[["كتاب",["plural"]]],13,""]
            ]
        """.trimIndent().toByteArray(),
    )

    /** Rows as Wiktionary's Khmer exports write them, subscript DA in some words and TA in others. */
    private fun khmerDictionary(): File = zip(
        "km.zip",
        "index.json" to """{"title":"Test Khmer","revision":"1","format":3,"sourceLanguage":"km"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["ខ្ញុំ","","pron","",0,["I"],1,""],
              ["សាលា","","n","",0,["hall"],2,""],
              ["សាលារៀន","","n","",0,["school"],3,""],
              ["សួស្ដី","","intj","",0,["hello"],4,""],
              ["ស្ត្រី","","n","",0,["woman"],5,""]
            ]
        """.trimIndent().toByteArray(),
    )

    /** Rows as Wiktionary's Thai exports write them: a classifier row for each noun it counts. */
    private fun thaiDictionary(): File = zip(
        "th.zip",
        "index.json" to """{"title":"Test Thai","revision":"1","format":3,"sourceLanguage":"th"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["น้ำ","","n","",0,["water"],1,""],
              ["เด็ก","","n","",0,["child"],2,""],
              ["เด็ก ๆ","","n","",0,["children"],3,""],
              ["ห้อง","","n","",0,["room"],4,""],
              ["ห้องสมุด","","n","",0,["library"],5,""],
              ["ห้อง","","non-lemma","",0,[["ห้องสมุด",["classifier"]]],6,""],
              ["กรุงเทพมหานคร","","name","",0,["Bangkok"],7,""],
              ["กรุงเทพฯ","","non-lemma","",0,[["กรุงเทพมหานคร",["alternative abbreviation"]]],8,""]
            ]
        """.trimIndent().toByteArray(),
    )

    /** Rows as Wiktionary's Armenian exports write them, the classical spelling after its label. */
    private fun armenianDictionary(): File = zip(
        "hy.zip",
        "index.json" to """{"title":"Test Armenian","revision":"1","format":3,"sourceLanguage":"hy"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["կարդալ","","v","",0,["to read"],1,""],
              ["կարդա՛","","non-lemma","",0,[["կարդալ",["imperative singular"]]],2,""],
              ["ինչու","","adv","",0,["why"],3,""],
              ["Երևան","","name","",0,["Yerevan"],4,""],
              ["գյուղ","","n","",0,["village"],5,""],
              ["orthography spelling գիւղ","","non-lemma","",0,[["գյուղ",["traditional"]]],6,""]
            ]
        """.trimIndent().toByteArray(),
    )

    /** Rows as Wiktionary's Georgian exports write them: no parts of speech. */
    private fun georgianDictionary(): File = zip(
        "ka.zip",
        "index.json" to """{"title":"Test Georgian","revision":"1","format":3,"sourceLanguage":"ka"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["წიგნი","","n","",0,["book"],1,""],
              ["მა","","n col","",0,["a made-up collective noun"],2,""],
              ["საქართველო","","name","",0,["Georgia"],3,""]
            ]
        """.trimIndent().toByteArray(),
    )

    private fun ukrainianDictionary(): File = zip(
        "uk.zip",
        "index.json" to """{"title":"Test Ukrainian","revision":"1","format":3,"sourceLanguage":"uk"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["книга","","","n",0,["book"],1,""],
              ["зробити","","","v",0,["to do"],2,""],
              ["п'ять","","","num",0,["five"],3,""],
              ["сімʼя","","","n",0,["family"],4,""],
              ["автострада","","","n",0,["highway"],5,""],
              ["а̀втостра́ди","","non-lemma","n",0,[["автострада",["genitive"]]],6,""],
              ["читати","","","v",0,["to read"],7,""],
              ["чита́ю","","non-lemma","v",0,[["чита́ти",["first-person singular present"]]],8,""],
              ["кни́го","","non-lemma","n",0,[["кни́га",["vocative singular"]]],9,""]
            ]
        """.trimIndent().toByteArray(),
    )

    private fun greekDictionary(): File = zip(
        "el.zip",
        "index.json" to """{"title":"Test Greek","revision":"1","format":3,"sourceLanguage":"el"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["σκύλος","","","n",0,["dog"],1,""],
              ["σκύλου","","non-lemma","n",0,[["σκύλος",["genitive","singular"]]],2,""],
              ["ποτέ","","","adv",0,["never"],3,""],
              ["πότε","","","adv",0,["when"],4,""],
              ["καλή όρεξη","","","phrase",0,["enjoy your meal"],5,""],
              ["ρωτάω","","","v",0,["to ask"],6,""],
              ["ρώτησε","","non-lemma","v",0,[["ρωτάω",["third-person singular past"]]],7,""],
              ["πρόσωπο","","","n",0,["face"],8,""]
            ]
        """.trimIndent().toByteArray(),
    )

    private fun hindiDictionary(): File = zip(
        "hi.zip",
        "index.json" to """{"title":"Test Hindi","revision":"1","format":3,"sourceLanguage":"hi"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["पढ़ना","","","v",0,["to read"],1,""],
              ["पढ़ता","","non-lemma","",0,[["पढ़ना",["masculine","singular","habitual"]]],2,""],
              ["लड़की","","","n",0,["girl"],3,""],
              ["लड़कियाँ","","non-lemma","",0,[["लड़की",["plural","direct"]]],4,""],
              ["हूँ","","","v",0,["am"],5,""],
              ["माँ","","","n",0,["mother"],6,""],
              ["हैं","","","v",0,["are"],7,""],
              ["खाना","","","n",0,["food"],8,""],
              ["ख़ाना","","","n",0,["compartment"],9,""]
            ]
        """.trimIndent().toByteArray(),
    )

    /** Rows as Wiktionary's Bengali exports write them: no parts of speech, some forms with leftovers. */
    private fun bengaliDictionary(): File = zip(
        "bn.zip",
        "index.json" to """{"title":"Test Bengali","revision":"1","format":3,"sourceLanguage":"bn"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["বই","","","",0,["book"],1,""],
              ["করা","","","",0,["to do"],2,""],
              ["করেন (koren (formal))","","non-lemma","",0,[["করা",["third-person present polite"]]],3,""],
              ["করিস  / koris","","non-lemma","",0,[["করা",["second-person present very familiar"]]],4,""],
              ["কর ো","","non-lemma","",0,[["করা",["imperative familiar"]]],5,""],
              ["{{{3}}}ে ({{{4}}}e)","","non-lemma","",0,[["করা",["third-person present familiar"]]],6,""]
            ]
        """.trimIndent().toByteArray(),
    )

    private fun belarusianDictionary(): File = zip(
        "be.zip",
        "index.json" to """{"title":"Test Belarusian","revision":"1","format":3,"sourceLanguage":"be"}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["сям'я","","","n",0,["family"],1,""],
              ["вада","","","n",0,["water"],2,""],
              ["вады́","","non-lemma","n",0,[["вада",["genitive"]]],3,""]
            ]
        """.trimIndent().toByteArray(),
    )

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
              ["ку̏ће","","non-lemma","n",0,[["ку̏ћа",["genitive singular"]]],6,""],
              ["kuća","","non-lemma","n",0,[["kuća",["genitive plural"]]],7,""],
              ["kućama","","non-lemma","n",0,[["kuća",["dative plural"]]],8,""],
              ["sam","","","adj",0,["alone"],9,""],
              ["sam","","non-lemma","v",0,[["biti",["first-person singular present"]]],10,""],
              ["biti","","","v",0,["to be"],11,""]
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

        /**
         * The zero-width non-joiner, written into raw JSON as the character itself, as Wiktionary's exports hold it:
         * hoshidicts keeps a headword's JSON escapes (`\u200C`) as written.
         */
        const val ZWNJ = "\u200C"
    }
}
