package com.vpr.screenlate

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** Every translation has every string of the English resources, with the same format arguments. */
class TranslationsTest {
    /**
     * English resource files with strings in every module (one or two levels below the root, like `app` and
     * `core/anki`). The tests run in the app module, so the root is its parent.
     */
    private val englishFiles: List<File> = File("..").listFiles().orEmpty()
        .flatMap { listOf(it) + it.listFiles().orEmpty() }
        .map { File(it, "src/main/res/values") }
        .flatMap { it.listFiles { file -> file.extension == "xml" }.orEmpty().toList() }
        .filter { read(it).let { strings -> strings.strings.isNotEmpty() || strings.plurals.isNotEmpty() } }

    /** Plural categories each language needs (CLDR); other categories are left out. */
    private val pluralCategories = mapOf(
        "ru" to setOf("one", "few", "many", "other"),
        "pl" to setOf("one", "few", "many", "other"),
        "de" to setOf("one", "other"),
        "tr" to setOf("one", "other"),
        "es" to setOf("one", "many", "other"),
        "fr" to setOf("one", "many", "other"),
        "it" to setOf("one", "many", "other"),
        "pt" to setOf("one", "many", "other"),
        "ja" to setOf("other"),
        "ko" to setOf("other"),
        "vi" to setOf("other"),
        "b+zh+Hans" to setOf("other"),
        "b+zh+Hant" to setOf("other"),
    )

    private val format = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfx%]""")

    private class Strings(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun read(file: File): Strings {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        val nodes = document.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            if (element.getAttribute("translatable") == "false") continue
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> strings[name] = element.textContent
                "plurals" -> {
                    val items = element.getElementsByTagName("item")
                    plurals[name] = (0 until items.length).map { items.item(it) as Element }
                        .associate { it.getAttribute("quantity") to it.textContent }
                }
            }
        }
        return Strings(strings, plurals)
    }

    private fun arguments(text: String): List<String> = format.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun `translations match the english strings`() {
        val paths = englishFiles.map { it.relativeTo(File("..")).invariantSeparatorsPath.replace("/src/main/res/values/", ":") }
        assertWithMessage("string files").that(paths).containsAtLeast(
            "app:strings.xml", "overlay:strings.xml", "core/anki:strings.xml", "dictionary/api:strings.xml",
            "dictionary/engine-hoshidicts:inflections_ja.xml",
        )
        val locales = File("src/main/res").listFiles().orEmpty()
            .filter { it.name.startsWith("values-") && File(it, "strings.xml").exists() }
            .map { it.name.removePrefix("values-") }
        assertWithMessage("translated locales").that(locales).containsAtLeastElementsIn(pluralCategories.keys)
        for (englishFile in englishFiles) {
            val english = read(englishFile)
            for (locale in pluralCategories.keys) {
                val file = File(englishFile.parentFile.parentFile, "values-$locale/${englishFile.name}")
                assertWithMessage("$file exists").that(file.exists()).isTrue()
                val translated = read(file)
                assertWithMessage("$file strings").that(translated.strings.keys).containsExactlyElementsIn(english.strings.keys)
                assertWithMessage("$file plurals").that(translated.plurals.keys).containsExactlyElementsIn(english.plurals.keys)
                english.strings.forEach { (name, text) ->
                    val value = translated.strings.getValue(name)
                    assertWithMessage("$locale $name arguments").that(arguments(value)).isEqualTo(arguments(text))
                    assertWithMessage("$locale $name is empty").that(value.isNotBlank()).isTrue()
                }
                english.plurals.forEach { (name, items) ->
                    val translatedItems = translated.plurals.getValue(name)
                    assertWithMessage("$locale $name quantities").that(translatedItems.keys)
                        .containsExactlyElementsIn(pluralCategories.getValue(locale))
                    val expected = arguments(items.getValue("other"))
                    translatedItems.forEach { (quantity, value) ->
                        assertWithMessage("$locale $name $quantity arguments").that(arguments(value)).isEqualTo(expected)
                    }
                }
            }
        }
    }

    @Test
    fun `apostrophes, quotes and line breaks are escaped`() {
        // aapt drops an unescaped ' and everything a pair of " encloses loses its meaning; a line break in the
        // source becomes a space, so paragraphs need an escaped one. All are easy to miss.
        val raw = Regex("""(?<!\\)'""")
        val quote = Regex("""(?<!\\)"""")
        // Whole elements, so a string broken over several lines is caught.
        val element = Regex("""<(string|item)\b[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
        for (englishFile in englishFiles) {
            englishFile.parentFile.parentFile.listFiles().orEmpty().filter { it.name.startsWith("values") }.forEach { dir ->
                val file = File(dir, englishFile.name).takeIf { it.exists() } ?: return@forEach
                val content = file.readText()
                element.findAll(content).forEach { match ->
                    val text = match.groupValues[2]
                    val line = content.substring(0, match.range.first).count { it == '\n' } + 1
                    assertWithMessage("$file:$line has an unescaped '").that(raw.containsMatchIn(text)).isFalse()
                    assertWithMessage("$file:$line has an unescaped \"").that(quote.containsMatchIn(text)).isFalse()
                    assertWithMessage("$file:$line has a line break; write \\n").that('\n' in text).isFalse()
                }
            }
        }
    }
}
