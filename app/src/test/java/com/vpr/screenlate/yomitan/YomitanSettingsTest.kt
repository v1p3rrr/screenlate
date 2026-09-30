package com.vpr.screenlate.yomitan

import com.vpr.screenlate.dictionary.api.registry.dictionaryKey
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.AudioSourceType
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.core.anki.settings.OverwriteMode
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

class YomitanSettingsTest {

    private val export = """
        {
          "version": 1,
          "options": {
            "version": 77,
            "profileCurrent": 1,
            "profiles": [
              {"name": "Desktop", "options": {"general": {"maxResults": 16}}},
              {
                "name": "Mining",
                "options": {
                  "general": {"maxResults": 8, "sortFrequencyDictionary": "JPDB", "fontSize": 14, "fontFamily": "\"Meiryo\"", "customPopupCss": ".gloss-content { font-size: 16px }"},
                  "scanning": {"length": 12},
                  "translation": {"textReplacements": {"searchOriginal": false, "groups": [
                    [{"pattern": "(.)々", "ignoreCase": false, "replacement": "${'$'}1${'$'}1"}, {"pattern": "ッ", "replacement": "っ"}]
                  ]}},
                  "dictionaries": [
                    {"name": "Jitendex.org [2026-01-04]", "enabled": true},
                    {"name": "Wikipedia", "enabled": false}
                  ],
                  "audio": {"enabled": true, "volume": 25, "autoPlay": true, "sources": [
                    {"type": "custom-json", "url": "http://127.0.0.1:5050/?term={term}&reading={reading}"},
                    {"type": "jpod101", "url": ""},
                    {"type": "language-pod-101", "url": ""},
                    {"type": "something-new", "url": ""}
                  ]},
                  "anki": {
                    "tags": ["yomitan", "mined"],
                    "checkForDuplicates": true,
                    "duplicateScope": "deck-root",
                    "duplicateScopeCheckAllModels": false,
                    "duplicateBehavior": "overwrite",
                    "cardFormats": [
                      {"name": "Expression", "type": "term", "deck": "Mining", "model": "Senren", "fields": {
                        "word": {"value": "{expression}", "overwriteMode": "coalesce"},
                        "sentence": {"value": "{sentence}", "overwriteMode": "append"}
                      }},
                      {"name": "Kanji", "type": "kanji", "deck": "Kanji", "model": "Kanji", "fields": {}},
                      {"name": "Reading", "type": "term", "deck": "Mining", "model": "Lapis", "fields": {
                        "Expression": {"value": "{reading}", "overwriteMode": "coalesce-new"}
                      }}
                    ]
                  }
                }
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `reads profiles and the current one`() {
        val settings = YomitanSettings.parse(export)
        assertThat(settings.profiles.map { it.name }).containsExactly("Desktop", "Mining").inOrder()
        assertThat(settings.currentProfile).isEqualTo(1)
    }

    @Test
    fun `reads lookup and dictionary settings`() {
        val profile = YomitanSettings.parse(export).profiles[1]
        assertThat(profile.scanLength).isEqualTo(12)
        assertThat(profile.maxResults).isEqualTo(8)
        assertThat(profile.sortFrequencyDictionary).isEqualTo("JPDB")
        assertThat(profile.replacementRules).isEqualTo(2)
        assertThat(profile.fontSize).isEqualTo(14)
        assertThat(profile.fontFamily).isEqualTo("\"Meiryo\"")
        assertThat(profile.customPopupCss).isEqualTo(".gloss-content { font-size: 16px }")
        assertThat(profile.dictionaries).containsExactly(
            YomitanSettings.Dictionary("Jitendex.org [2026-01-04]", true),
            YomitanSettings.Dictionary("Wikipedia", false),
        ).inOrder()
    }

    @Test
    fun `reads audio sources and keeps unknown ones aside`() {
        val audio = YomitanSettings.parse(export).profiles[1].audio!!
        assertThat(audio.sources).containsExactly(
            AudioSource(AudioSourceType.CUSTOM_JSON, "http://127.0.0.1:5050/?term={term}&reading={reading}"),
            AudioSource(AudioSourceType.JAPANESE_POD_101),
            AudioSource(AudioSourceType.LANGUAGE_POD_101),
        ).inOrder()
        assertThat(audio.unknown).containsExactly("something-new")
        assertThat(audio.volume).isEqualTo(25)
        assertThat(audio.autoPlay).isTrue()
    }

    @Test
    fun `the old name of the dictionary search source is read as that source`() {
        val old = """{"options": {"profiles": [{"options": {"audio": {"sources": [{"type": "jpod101-alternate"}]}}}]}}"""
        assertThat(YomitanSettings.parse(old).profiles.single().audio!!.sources)
            .containsExactly(AudioSource(AudioSourceType.LANGUAGE_POD_101))
    }

    @Test
    fun `reads term card formats with overwrite modes`() {
        val anki = YomitanSettings.parse(export).profiles[1].anki!!
        assertThat(anki.main!!.model).isEqualTo("Senren")
        assertThat(anki.main!!.deck).isEqualTo("Mining")
        assertThat(anki.main!!.fields).containsExactly("word", "{expression}", "sentence", "{sentence}")
        assertThat(anki.main!!.overwriteModes).containsExactly("word", OverwriteMode.COALESCE, "sentence", OverwriteMode.APPEND)
        assertThat(anki.others.map { it.model }).containsExactly("Lapis")
        assertThat(anki.others.single().overwriteModes).containsExactly("Expression", OverwriteMode.COALESCE_NEW)
        assertThat(anki.tags).containsExactly("yomitan", "mined").inOrder()
        assertThat(anki.duplicateScope).isEqualTo(DuplicateScope.DECK_ROOT)
        assertThat(anki.duplicateBehavior).isEqualTo(DuplicateBehavior.OVERWRITE)
    }

    @Test
    fun `reads the terms format of older versions`() {
        val old = """
            {"options": {"profileCurrent": 0, "profiles": [{"name": "Default", "options": {"anki": {
              "terms": {"deck": "Deck", "model": "Basic", "fields": {"Front": "{expression}", "Back": "{glossary}"}}
            }}}]}}
        """.trimIndent()
        val main = YomitanSettings.parse(old).profiles.single().anki!!.main!!
        assertThat(main.model).isEqualTo("Basic")
        assertThat(main.fields).containsExactly("Front", "{expression}", "Back", "{glossary}")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `other files are refused`() {
        YomitanSettings.parse("""{"data": {"databases": []}}""")
    }

    @Test
    fun `dictionary titles match across revisions`() {
        assertThat(dictionaryKey("Jitendex.org [2026-01-04]")).isEqualTo(dictionaryKey("Jitendex.org [2026-08-11]"))
        assertThat(dictionaryKey("JMdict (Russian)")).isEqualTo("jmdict (russian)")
        assertThat(dictionaryKey("Колобок 400k")).isEqualTo("колобок 400k")
    }

    /** The owner's own export, when it is present in testdata/ (not in git). */
    @Test
    fun `reads a real export`() {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "testdata/dictionaries/yomitan-settings-2026-07-04-01-24-42.json") }
            .firstOrNull { it.exists() }
        assumeTrue(file != null)
        val settings = YomitanSettings.parse(file!!.readText())
        val profile = settings.profiles[settings.currentProfile]
        assertThat(profile.anki?.main?.model).isNotEmpty()
        assertThat(profile.dictionaries).isNotEmpty()
    }
}
