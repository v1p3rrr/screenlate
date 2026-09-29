package com.vpr.screenlate

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** Every translation has every string of the English resources, with the same format arguments. */
class TranslationsTest {
    /** Modules with string resources, relative to the app module, where the tests run. */
    private val modules = listOf(".", "../overlay", "../core/anki", "../dictionary/api").map { File(it, "src/main/res") }

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
        val locales = modules.flatMap { res -> res.listFiles().orEmpty().map { it.name } }
            .filter { it.startsWith("values-") && File(modules[0], "$it/strings.xml").exists() }
            .map { it.removePrefix("values-") }
            .distinct()
        assertWithMessage("translated locales").that(locales).containsAtLeastElementsIn(pluralCategories.keys)
        for (res in modules) {
            val english = read(File(res, "values/strings.xml"))
            for (locale in pluralCategories.keys) {
                val file = File(res, "values-$locale/strings.xml")
                if (english.strings.isEmpty() && english.plurals.isEmpty()) continue
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
    fun `apostrophes and quotes are escaped`() {
        // aapt drops an unescaped ' and everything a pair of " encloses loses its meaning; both are easy to miss.
        val raw = Regex("""(?<!\\)'""")
        for (res in modules) {
            res.listFiles().orEmpty().filter { it.name.startsWith("values") }.forEach { dir ->
                val file = File(dir, "strings.xml").takeIf { it.exists() } ?: return@forEach
                file.readLines().forEachIndexed { index, line ->
                    val text = line.substringAfter('>', "").substringBeforeLast('<', "")
                    assertWithMessage("$file:${index + 1} has an unescaped '").that(raw.containsMatchIn(text)).isFalse()
                    assertWithMessage("$file:${index + 1} has an unescaped \"").that(Regex("""(?<!\\)"""").containsMatchIn(text)).isFalse()
                }
            }
        }
    }
}
