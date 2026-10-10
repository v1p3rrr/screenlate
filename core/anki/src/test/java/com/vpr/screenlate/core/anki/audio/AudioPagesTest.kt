package com.vpr.screenlate.core.anki.audio

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.language.AudioRegion
import kotlinx.serialization.json.Json
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
        assertThat(AudioPages.linguaLibreSearch("猫", "Q5287", "jpn"))
            .isEqualTo("""intitle:"猫" intitle:/LL-Q5287 \(jpn\)-.*-猫\.wav/""")
        assertThat(AudioPages.wiktionarySearch("a.b", "ja"))
            .isEqualTo("""intitle:"a.b" intitle:/[Jj]a(-[a-zA-Z]{2})?-a\.b[0-9]*\.(ogg|oga|opus|wav|mp3|flac)/""")
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
    fun `Wiktionary titles name their region before the term`() {
        assertThat(AudioPages.wiktionaryRegion("File:En-us-water.ogg", "water", "en")).isEqualTo("us")
        assertThat(AudioPages.wiktionaryRegion("File:En-UK-water2.oga", "water", "en")).isEqualTo("uk")
        assertThat(AudioPages.wiktionaryRegion("File:En-water.ogg", "water", "en")).isNull()
        // A term that starts like a region code is not taken for one.
        assertThat(AudioPages.wiktionaryRegion("File:En-up-to.ogg", "up-to", "en")).isNull()
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
