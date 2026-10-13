package com.vpr.screenlate.dictionary.api.imports

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.dictionary.api.registry.TranscriptionTags
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranscriptionTagBanksTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun archive(vararg files: Pair<String, String>): File = folder.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, text) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `reads the tags of transcriptions and skips other rows`() {
        val tags = TranscriptionTagBanks.read(
            archive(
                "index.json" to """{"title":"IPA"}""",
                "term_meta_bank_1.json" to """[
                    ["water", "freq", {"value": 5, "displayValue": "5"}],
                    ["water", "ipa", {"reading": "water", "transcriptions": [
                        {"ipa": "/ˈwɔtɚ/", "tags": ["General-American", "us"]},
                        {"ipa": "[ˈwɔɾɚ]"},
                        {"ipa": "/ˈwɔːtə/", "tags": []},
                        {"ipa": "/odd/", "tags": [1, null, "rare"]},
                        "broken",
                        {"tags": ["no ipa"]}
                    ]}],
                    ["cat", "pitch", {"reading": "cat", "pitches": [{"position": 0}]}],
                    "broken row",
                    ["water", "ipa", {"reading": "water", "transcriptions": [{"ipa": "/ˈwɔtɚ/", "tags": ["us", "uk"]}]}]
                ]""",
                "term_bank_1.json" to """[["water","water","","",0,["H2O"],1,""]]""",
            ),
        )!!

        assertThat(tags.of("water", "/ˈwɔtɚ/")).containsExactly("General-American", "us", "uk").inOrder()
        assertThat(tags.of("water", "[ˈwɔɾɚ]")).isEmpty()
        assertThat(tags.of("water", "/ˈwɔːtə/")).isEmpty()
        assertThat(tags.of("water", "/odd/")).containsExactly("rare")
        assertThat(tags.of("cat", "/ˈwɔtɚ/")).isEmpty()
        assertThat(tags.size).isEqualTo(2)
    }

    @Test
    fun `an archive without tagged transcriptions has none`() {
        val archive = archive(
            "term_meta_bank_1.json" to """[["猫", "pitch", {"reading": "ねこ", "pitches": [{"position": 1}]}],
                ["water", "ipa", {"reading": "water", "transcriptions": [{"ipa": "/ˈwɔtɚ/"}]}]]""",
        )

        assertThat(TranscriptionTagBanks.read(archive)).isNull()
    }

    @Test
    fun `the tags survive a write and a read`() {
        val tags = TranscriptionTags.Builder().apply {
            add("water", "/ˈwɔtɚ/", listOf("us"))
            add("water", "/ˈwɔːtə/", listOf("gb", "au"))
            add("route", "/ɹuːt/", listOf("gb", "au"))
            add("route", "/ɹaʊt/", listOf("x".repeat(500)))
        }.build()
        val file = folder.newFile()

        tags.write(file)
        val read = TranscriptionTags.read(file)

        assertThat(read.of("water", "/ˈwɔtɚ/")).containsExactly("us")
        assertThat(read.of("water", "/ˈwɔːtə/")).containsExactly("gb", "au").inOrder()
        assertThat(read.of("route", "/ɹuːt/")).containsExactly("gb", "au").inOrder()
        assertThat(read.of("route", "/ɹaʊt/")).isEmpty()
        assertThat(read.of("route", "/ˈwɔtɚ/")).isEmpty()
        assertThat(read.size).isEqualTo(3)
    }

    @Test(expected = java.io.IOException::class)
    fun `another file is refused`() {
        TranscriptionTags.read(folder.newFile().apply { writeText("{}") })
    }
}
