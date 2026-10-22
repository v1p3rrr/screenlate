package com.vpr.screenlate.core.anki.audio

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.ArabicSupport
import com.vpr.screenlate.core.common.language.AudioRegion
import com.vpr.screenlate.core.common.language.FrenchSupport
import com.vpr.screenlate.core.common.language.LatinSupport
import com.vpr.screenlate.core.common.language.SpanishSupport
import java.util.Locale
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioPagesTest {

    private fun languagePodRow(audio: String, vocab: String?, kana: String) = """
        <div class="dc-box--white dc-result-row">
            <div class='dc-result-row__player-field'>
                <audio preload="none" controls><source src="$audio" type="audio/mp3"></audio>
                <audio preload="none" controls data-speed="0.5"><source src="$audio" type="audio/mp3"></audio>
            </div>
            <div class="dc-result-row__vocab-field" lang=ja>
                ${vocab?.let { "<span class='dc-vocab'>$it</span>" }.orEmpty()}
                <span class='dc-vocab_kana'>$kana</span><span class='dc-vocab_romanization'>x</span>
            </div>
        </div>
    """

    @Test
    fun `languagepod rows match word and kana`() {
        val html = "<div>header</div>" +
            languagePodRow("https://cdn.example/626.mp3", "食べる", "たべる") +
            languagePodRow("https://cdn.example/700.mp3", "食べ物", "たべもの") +
            languagePodRow("https://cdn.example/701.mp3", "食べる", "くべる")
        assertThat(AudioPages.languagePod(html, "食べる", "たべる"))
            .containsExactly(AudioPages.Found("https://cdn.example/626.mp3"))
    }

    @Test
    fun `languagepod kana words have no separate kana field`() {
        val html = languagePodRow("https://cdn.example/1.mp3", null, "する")
        assertThat(AudioPages.languagePod(html, "する", "")).hasSize(1)
        assertThat(AudioPages.languagePod(html, "刷る", "する")).isEmpty()
    }

    @Test
    fun `jisho audio of the matching word and reading`() {
        val html = """
            <audio id="audio_食べる:たべる" preload="none"><source src="//cdn.example/audio/abc.mp3" type="audio/mpeg"></source><source src="//cdn.example/audio_ogg/abc.ogg" type="audio/ogg"></source></audio>
            <audio id="audio_食べ物:たべもの" preload="none"><source src="//cdn.example/audio/def.mp3" type="audio/mpeg"></source></audio>
        """
        assertThat(AudioPages.jisho(html, "食べる", "たべる"))
            .containsExactly(AudioPages.Found("https://cdn.example/audio/abc.mp3"))
        assertThat(AudioPages.jisho(html, "食べる", "くべる")).isEmpty()
    }

    @Test
    fun `commons searches escape the term`() {
        assertThat(AudioPages.linguaLibreSearch("猫", listOf("Q5287")))
            .isEqualTo("""intitle:"猫" intitle:/LL-Q5287 \([a-z]{3}\)-.*-猫\.wav/""")
        assertThat(AudioPages.linguaLibreSearch("voda", listOf("Q6654", "Q9299")))
            .isEqualTo("""intitle:"voda" intitle:/LL-(Q6654|Q9299) \([a-z]{3}\)-.*-[vV]oda\.wav/""")
        assertThat(AudioPages.wiktionarySearch("a.b", "ja"))
            .isEqualTo("""intitle:"a.b" intitle:/[Jj]a(-[a-zA-Z]{2})?-[aA]\.b[0-9]*\.(ogg|oga|opus|wav|mp3|flac)/""")
        assertThat(AudioPages.wiktionarySearch("(a)", "en")).contains("""-\(a\)[0-9]*""")
    }

    @Test
    fun `commons searches take the term's first letter in either case, as titles may capitalize it`() {
        assertThat(AudioPages.wiktionarySearch("bwyta", "cy"))
            .isEqualTo("""intitle:"bwyta" intitle:/[Cc]y(-[a-zA-Z]{2})?-[bB]wyta[0-9]*\.(ogg|oga|opus|wav|mp3|flac)/""")
        assertThat(AudioPages.linguaLibreSearch("uisge", listOf("Q9314")))
            .isEqualTo("""intitle:"uisge" intitle:/LL-Q9314 \([a-z]{3}\)-.*-[uU]isge\.wav/""")
        // A capitalized term finds lowercase titles too; only the first letter varies.
        assertThat(AudioPages.wiktionarySearch("Ŵyn", "cy")).contains("-[Ŵŵ]yn[0-9]*")
        assertThat(AudioPages.wiktionarySearch("вода", "ru")).contains("-[вВ]ода[0-9]*")
        // A digraph letter has a title case of its own.
        assertThat(AudioPages.wiktionarySearch("ǆep", "hr")).contains("-[ǆǄǅ]ep[0-9]*")
        // A letter whose capital is two letters stays as it is.
        assertThat(AudioPages.wiktionarySearch("ßa", "de")).contains("-ßa[0-9]*")

        val files = listOf("File:Cy-Bwyta.ogg" to "https://upload.example/cy.ogg")
        assertThat(AudioPages.wiktionaryFiles(files, "bwyta", "cy")).isEqualTo(files)
        assertThat(AudioPages.linguaLibreSpeaker("File:LL-Q9314 (gla)-DaoineÈibhinn-Uisge.wav", "uisge"))
            .isEqualTo("DaoineÈibhinn")
        assertThat(AudioPages.linguaLibreSpeaker("File:LL-Q1860 (eng)-Back ache-Kitty-cat.wav", "kitty-cat"))
            .isEqualTo("Back ache")
    }

    @Test
    fun `a capitalized title with marks is found and ranked as the word`() {
        // No language has both yet (Arabic script has no case); a combining acute stands in for the marks.
        val marks = "\u0301"
        val any = "[$marks]*"
        assertThat(AudioPages.wiktionarySearch("bwyta", "cy", marks = marks))
            .contains("-[bB]${any}w${any}y${any}t${any}a$any[0-9]*")
        val reading = "b\u0301wyta"
        val files = listOf(
            "File:Cy-B\u0301wyta.ogg" to "https://upload.example/reading.ogg",
            "File:Cy-Bwyta.ogg" to "https://upload.example/word.ogg",
        )
        assertThat(AudioPages.wiktionary(files, "bwyta", reading, "cy", marks = marks).map { it.url to it.rank })
            .containsExactly("https://upload.example/word.ogg" to 0, "https://upload.example/reading.ogg" to 1)
            .inOrder()
        val recording = listOf("File:LL-Q9309 (cym)-Speaker-B\u0301wyta.wav" to "https://upload.example/ll.wav")
        assertThat(AudioPages.linguaLibre(recording, "bwyta", listOf("Q9309"), marks, reading))
            .containsExactly(AudioPages.Found("https://upload.example/ll.wav", "Speaker", 1))
    }

    @Test
    fun `commons searches leave the term of a script without case as it is`() {
        assertThat(AudioPages.wiktionarySearch("猫", "ja"))
            .isEqualTo("""intitle:"猫" intitle:/[Jj]a(-[a-zA-Z]{2})?-猫[0-9]*\.(ogg|oga|opus|wav|mp3|flac)/""")
        assertThat(AudioPages.linguaLibreSearch("ねこ", listOf("Q5287"))).endsWith("""-.*-ねこ\.wav/""")
        assertThat(AudioPages.wiktionarySearch("물", "ko")).contains("-물[0-9]*")
    }

    @Test
    fun `commons searches take the cases of the language's letters`() {
        val turkish = Locale.forLanguageTag("tr")
        assertThat(AudioPages.wiktionarySearch("iyi", "tr", locale = turkish)).contains("-[iİ]yi[0-9]*")
        assertThat(AudioPages.wiktionarySearch("ılık", "tr", locale = turkish)).contains("-[ıI]lık[0-9]*")
        assertThat(AudioPages.linguaLibreSearch("iyi", listOf("Q256"), locale = turkish)).contains("""-[iİ]yi\.wav""")
        assertThat(AudioPages.wiktionarySearch("iyi", "en")).contains("-[iI]yi[0-9]*")

        val files = listOf("File:Tr-İyi.ogg" to "https://upload.example/tr.ogg")
        assertThat(AudioPages.wiktionaryFiles(files, "iyi", "tr")).isEqualTo(files)
    }

    @Test
    fun `commons answers give titles and urls`() {
        val search = Json.parseToJsonElement(
            """{"query":{"search":[{"title":"File:LL-Q5287 (jpn)-葵心-猫.wav"},{"title":"File:Ja-猫.ogg"}]}}""",
        )
        val titles = AudioPages.commonsTitles(search)
        assertThat(titles).containsExactly("File:LL-Q5287 (jpn)-葵心-猫.wav", "File:Ja-猫.ogg").inOrder()
        val info = Json.parseToJsonElement(
            """{"query":{"pages":{
                "2":{"title":"File:Ja-猫.ogg","imageinfo":[{"url":"https://upload.example/b.ogg"}]},
                "1":{"title":"File:LL-Q5287 (jpn)-葵心-猫.wav","imageinfo":[{"url":"https://upload.example/a.wav"}]}}}}""",
        )
        assertThat(AudioPages.commonsUrls(info, titles).toList()).containsExactly(
            "File:LL-Q5287 (jpn)-葵心-猫.wav" to "https://upload.example/a.wav",
            "File:Ja-猫.ogg" to "https://upload.example/b.ogg",
        ).inOrder()
        assertThat(AudioPages.linguaLibreSpeaker("File:LL-Q5287 (jpn)-葵心-猫.wav", "猫")).isEqualTo("葵心")
    }

    @Test
    fun `a commons error answer is not an empty one`() {
        val busy = Json.parseToJsonElement(
            """{"error":{"code":"cirrussearch-regex-too-busy-error","info":"Too many regular expression searches currently running."}}""",
        )
        val error = assertThrows(CommonsErrorException::class.java) { AudioPages.commonsTitles(busy) }
        assertThat(error.code).isEqualTo("cirrussearch-regex-too-busy-error")
        assertThrows(CommonsErrorException::class.java) { AudioPages.commonsUrls(busy, listOf("File:Ja-猫.ogg")) }
        assertThat(AudioError.of(error)).isEqualTo(AudioError(AudioError.Kind.HTTP_STATUS, "cirrussearch-regex-too-busy-error"))
        assertThat(AudioPages.commonsTitles(Json.parseToJsonElement("""{"batchcomplete":""}"""))).isEmpty()
    }

    @Test
    fun `arabic searches let every letter carry vowel marks, Lingua Libre only in Arabic's own item`() {
        val marks = ArabicSupport.recordingMarks
        val any = "[$marks]*"
        assertThat(AudioPages.linguaLibreSearch("كتاب", ArabicSupport.linguaLibreItems, marks))
            .isEqualTo("""intitle:/LL-Q13955 \([a-z]{3}\)-.*-ك${any}ت${any}ا${any}ب$any\.wav/""")
        assertThat(AudioPages.wiktionarySearch("كتاب", "ar", marks = marks)).isEqualTo(
            """intitle:"كتاب" intitle:/[Aa]r(-[a-zA-Z]{2})?-ك${any}ت${any}ا${any}ب$any[0-9]*\.(ogg|oga|opus|wav|mp3|flac)/""",
        )
        // A term with marks is searched by its letters.
        assertThat(AudioPages.wiktionarySearch("كِتَاب", "ar", marks = marks))
            .isEqualTo(AudioPages.wiktionarySearch("كتاب", "ar", marks = marks))
    }

    @Test
    fun `arabic Lingua Libre recordings of the word come before those of its vowelled reading and other marks`() {
        val marks = ArabicSupport.recordingMarks
        val files = listOf(
            "File:LL-Q13955 (ara)-Al-jahbaḏ-كِتَاب.wav" to "https://upload.example/reading.wav",
            "File:LL-Q13955 (ara)-Speaker-كُتَّاب.wav" to "https://upload.example/other.wav",
            "File:LL-Q13955 (ara)-Zinou2go-كتاب.wav" to "https://upload.example/word.wav",
            // The target of a redirect from an Arabic title: a dialect's recording.
            "File:LL-Q55633582 (ajp)-Khalil.rantissi-كتاب.wav" to "https://upload.example/dialect.wav",
        )
        assertThat(AudioPages.linguaLibre(files, "كتاب", listOf("Q13955"), marks, "كِتَاب")).containsExactly(
            AudioPages.Found("https://upload.example/word.wav", "Zinou2go", 0),
            AudioPages.Found("https://upload.example/reading.wav", "Al-jahbaḏ", 1),
            AudioPages.Found("https://upload.example/other.wav", "Speaker", 2),
        ).inOrder()
    }

    @Test
    fun `arabic Wiktionary titles with other letters are dropped, those with marks ranked`() {
        val marks = ArabicSupport.recordingMarks
        val files = listOf(
            "File:Ar-كَلْب.ogg" to "https://upload.example/reading.ogg",
            "File:Ar-كَلَبَ.ogg" to "https://upload.example/verb.ogg",
            "File:Ar-كلب.ogg" to "https://upload.example/word.ogg",
            "File:Ar-كلبة.ogg" to "https://upload.example/longer.ogg",
        )
        assertThat(AudioPages.wiktionary(files, "كلب", "كَلْب", "ar", marks = marks)).containsExactly(
            AudioPages.Found("https://upload.example/word.ogg", "Ar-كلب.ogg", 0),
            AudioPages.Found("https://upload.example/reading.ogg", "Ar-كَلْب.ogg", 1),
            AudioPages.Found("https://upload.example/verb.ogg", "Ar-كَلَبَ.ogg", 2),
        ).inOrder()
        // A search for جرس matches the redirect Ar-جرس.ogg and gives its target, another word.
        assertThat(AudioPages.wiktionary(listOf("File:Ar-حرس.oga" to "u"), "جرس", "", "ar", marks = marks)).isEmpty()
        // A redirect from the plain title to the vowelled one (Ar-كتاب.ogg) leaves the vowelled file.
        assertThat(AudioPages.wiktionary(listOf("File:Ar-كِتَابٌ.oga" to "u"), "كتاب", "كِتَاب", "ar", marks = marks))
            .containsExactly(AudioPages.Found("u", "Ar-كِتَابٌ.oga", 2))
        // Without marks the exact titles are all there is.
        assertThat(AudioPages.wiktionary(files, "كلب", "كَلْب", "ar").map { it.url }).containsExactly("https://upload.example/word.ogg")
    }

    @Test
    fun `Lingua Libre titles with a hyphen left before the term go last, as they are usually compounds`() {
        val files = listOf(
            "File:LL-Q1860 (eng)-Back ache-kitty-cat.wav" to "https://upload.example/compound.wav",
            "File:LL-Q1860 (eng)-Jean-Pierre-cat.wav" to "https://upload.example/hyphen.wav",
            "File:LL-Q1860 (eng)-Back ache-cat.wav" to "https://upload.example/plain.wav",
        )
        assertThat(AudioPages.linguaLibre(files, "cat")).containsExactly(
            AudioPages.Found("https://upload.example/plain.wav", "Back ache"),
            AudioPages.Found("https://upload.example/compound.wav", "Back ache-kitty"),
            AudioPages.Found("https://upload.example/hyphen.wav", "Jean-Pierre"),
        ).inOrder()
    }

    @Test
    fun `Lingua Libre recordings of several languages come in the order of their items`() {
        val files = listOf(
            "File:LL-Q9299 (srp)-Speaker-voda.wav" to "https://upload.example/sr.wav",
            "File:LL-Q6654 (hrv)-Govornik-voda.wav" to "https://upload.example/hr.wav",
        )
        assertThat(AudioPages.linguaLibre(files, "voda", listOf("Q6654", "Q9299")).map { it.name })
            .containsExactly("Govornik", "Speaker").inOrder()
        assertThat(AudioPages.linguaLibre(files, "voda").map { it.name }).containsExactly("Speaker", "Govornik").inOrder()
    }

    @Test
    fun `Wiktionary files are only those whose whole title records the term in the language`() {
        val files = listOf(
            "File:Sr-вода.ogg" to "https://upload.example/a.ogg",
            "File:sr-вода2.flac" to "https://upload.example/b.flac",
            "File:LL-Q7737 (rus)-Speaker-вода.wav" to "https://upload.example/ru.wav",
            "File:Sr-водар.ogg" to "https://upload.example/other.ogg",
            "File:Hr-вода.ogg" to "https://upload.example/hr.ogg",
        )
        assertThat(AudioPages.wiktionaryFiles(files, "вода", "sr").map { it.second })
            .containsExactly("https://upload.example/a.ogg", "https://upload.example/b.flac").inOrder()
        assertThat(AudioPages.wiktionaryFiles(listOf("File:En-us-water.ogg" to "u"), "water", "en")).hasSize(1)
    }

    @Test
    fun `Wiktionary titles name their region before the term`() {
        assertThat(AudioPages.wiktionaryRegion("File:En-us-water.ogg", "water", "en")).isEqualTo("us")
        assertThat(AudioPages.wiktionaryRegion("File:En-UK-water2.oga", "water", "en")).isEqualTo("uk")
        assertThat(AudioPages.wiktionaryRegion("File:En-water.ogg", "water", "en")).isNull()
        // A term that starts like a region code is not taken for one.
        assertThat(AudioPages.wiktionaryRegion("File:En-up-to.ogg", "up-to", "en")).isNull()
    }

    @Test
    fun `Wiktionary titles may name a region with a longer code the language lists`() {
        val regions = SpanishSupport.audioRegions
        assertThat(AudioPages.wiktionaryRegion("File:Es-am-lat-agua.ogg", "agua", "es", regions)).isEqualTo("am-lat")
        assertThat(AudioPages.wiktionaryRegion("File:Es-mx-agua.ogg", "agua", "es", regions)).isEqualTo("mx")
        assertThat(AudioPages.wiktionaryRegion("File:Es-am-lat-agua.ogg", "agua", "es")).isNull()
        val search = """intitle:/[Ee]s(-(am-lat|[a-zA-Z]{2}))?-[aA]gua[0-9]*\.(ogg|oga|opus|wav|mp3|flac)/"""
        assertThat(AudioPages.wiktionarySearch("agua", "es", regions)).isEqualTo("intitle:\"agua\" $search")

        val files = listOf("File:Es-agua.ogg", "File:Es-es-agua.ogg", "File:Es-am-lat-agua.ogg", "File:Es-mx-agua.ogg")
            .map { it to it }
        assertThat(AudioPages.byRegion(files, "agua", "es", regions).map { it.first })
            .containsExactly(
                "File:Es-am-lat-agua.ogg", "File:Es-mx-agua.ogg", "File:Es-es-agua.ogg", "File:Es-agua.ogg",
            )
            .inOrder()
    }

    @Test
    fun `latin recordings of the classical pronunciation come first, then the ecclesiastical, then the others`() {
        val regions = LatinSupport.audioRegions
        assertThat(AudioPages.wiktionaryRegion("File:La-cls-aqua.ogg", "aqua", "la", regions)).isEqualTo("cls")
        assertThat(AudioPages.wiktionaryRegion("File:La-ecc-aqua.ogg", "aqua", "la", regions)).isEqualTo("ecc")
        assertThat(AudioPages.wiktionarySearch("aqua", "la", regions)).contains("(-(cls|ecc|[a-zA-Z]{2}))?-[aA]qua[0-9]*")
        val files = listOf("File:La-aqua.ogg", "File:La-ecc-aqua.ogg", "File:La-cls-aqua.ogg").map { it to it }
        assertThat(AudioPages.byRegion(files, "aqua", "la", regions).map { it.first })
            .containsExactly("File:La-cls-aqua.ogg", "File:La-ecc-aqua.ogg", "File:La-aqua.ogg")
            .inOrder()
    }

    @Test
    fun `Wiktionary titles with a longer tag the language does not list are not recordings of the word after it`() {
        val files = listOf(
            "File:Fr-grand-mère.ogg" to "https://upload.example/compound.ogg",
            "File:Fr-mère.ogg" to "https://upload.example/word.ogg",
        )
        assertThat(AudioPages.wiktionaryFiles(files, "mère", "fr", FrenchSupport.audioRegions).map { it.second })
            .containsExactly("https://upload.example/word.ogg")
        assertThat(AudioPages.wiktionarySearch("mère", "fr", FrenchSupport.audioRegions))
            .contains("""(-(fr-paris|[a-zA-Z]{2}))?-[mM]ère[0-9]*""")
        assertThat(AudioPages.wiktionaryFiles(listOf("File:Gd-Lewis-aon.ogg" to "u"), "aon", "gd")).isEmpty()
        // A dialect tag fits as part of the language code a language searches under.
        assertThat(AudioPages.wiktionarySearch("aon", "gd-Lewis")).contains("""/[Gg]d-Lewis(-[a-zA-Z]{2})?-[aA]on[0-9]*""")
        assertThat(AudioPages.wiktionaryFiles(listOf("File:Gd-Lewis-aon.ogg" to "u"), "aon", "gd-Lewis")).hasSize(1)
    }

    @Test
    fun `Wiktionary files follow the order of the regions, other regions where Other is`() {
        val files = listOf("au", "uk", "", "us", "gb").map { code ->
            val title = if (code.isEmpty()) "File:En-water.ogg" else "File:En-$code-water.ogg"
            title to code
        }
        val us = AudioRegion("us", setOf("us"))
        val uk = AudioRegion("uk", setOf("uk", "gb"))
        fun order(regions: List<AudioRegion>) = AudioPages.byRegion(files, "water", "en", regions).map { it.second }
        assertThat(order(listOf(us, uk, AudioRegion.OTHER))).containsExactly("us", "uk", "gb", "au", "").inOrder()
        assertThat(order(listOf(AudioRegion.OTHER, uk, us))).containsExactly("au", "", "uk", "gb", "us").inOrder()
        assertThat(order(emptyList())).containsExactly("au", "uk", "", "us", "gb").inOrder()
    }

    @Test
    fun `stored region ids order the defaults, and new regions keep their default place after them`() {
        val us = AudioRegion("us", setOf("us"))
        val uk = AudioRegion("uk", setOf("uk"))
        val defaults = listOf(us, uk, AudioRegion.OTHER)
        assertThat(AudioRegion.ordered(emptyList(), defaults)).isEqualTo(defaults)
        assertThat(AudioRegion.ordered(listOf("uk", "gone", "us"), defaults)).containsExactly(uk, us, AudioRegion.OTHER).inOrder()
    }
}
