package com.vpr.screenlate.dictionary.api.registry

import com.google.common.truth.Truth.assertThat
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranscriptionTagsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun written(builder: TranscriptionTags.Builder): TranscriptionTags {
        val file = File(folder.root, "transcription_tags.bin")
        builder.write(file)
        return TranscriptionTags.read(file)
    }

    private fun file(write: DataOutputStream.() -> Unit): File =
        folder.newFile().apply { DataOutputStream(outputStream()).use(write) }

    /** A version 2 entry: the top 39 bits of the hash above the tag set index. */
    private fun packed(term: String, ipa: String, set: Int): Long =
        ((TranscriptionTags.key(term, ipa) ushr 25) shl 24) or set.toLong()

    @Test
    fun `a transcription listed twice gets the tags of both, the first listing's first`() {
        val builder = TranscriptionTags.Builder().apply {
            add("cat", "/kæt/", listOf("uk"))
            add("dog", "/dɒɡ/", listOf("us"))
            add("cat", "/kæt/", listOf("uk"))
            add("dog", "/dɒɡ/", listOf("uk"))
            add("dog", "/dɒɡ/", listOf("au", "us"))
            add("cow", "/kaʊ/", listOf("us", "us", ""))
        }

        for (tags in listOf(builder.build(), written(builder))) {
            assertThat(tags.of("dog", "/dɒɡ/")).containsExactly("us", "uk", "au").inOrder()
            assertThat(tags.of("cat", "/kæt/")).containsExactly("uk")
            assertThat(tags.of("cow", "/kaʊ/")).containsExactly("us")
            assertThat(tags.size).isEqualTo(3)
        }
    }

    @Test
    fun `entries in several chunks are merged and found`() {
        val builder = TranscriptionTags.Builder(chunkSize = 4).apply {
            for (i in 0 until 50) add("t$i", "/$i/", listOf("r${i % 7}"))
            add("late", "/l/", listOf("b"))
            for (i in 0 until 50 step 10) add("t$i", "/$i/", listOf("late", "r${i % 7}"))
            add("late", "/l/", listOf("r0"))
        }

        for (tags in listOf(builder.build(), written(builder))) {
            for (i in 0 until 50) {
                val expected = if (i % 10 == 0) listOf("r${i % 7}", "late") else listOf("r${i % 7}")
                assertThat(tags.of("t$i", "/$i/")).containsExactlyElementsIn(expected).inOrder()
            }
            assertThat(tags.of("late", "/l/")).containsExactly("b", "r0").inOrder()
            assertThat(tags.of("t5", "/6/")).isEmpty()
            assertThat(tags.of("absent", "/5/")).isEmpty()
            assertThat(tags.size).isEqualTo(51)
        }
    }

    @Test
    fun `a file of the first version is read`() {
        val entries = listOf(
            TranscriptionTags.key("water", "/ˈwɔtɚ/") to 0,
            TranscriptionTags.key("route", "/ɹuːt/") to 1,
        ).sortedBy { it.first }
        val file = file {
            writeInt(0x53545431)
            writeInt(2)
            writeInt(1)
            writeUTF("us")
            writeInt(2)
            writeUTF("gb")
            writeUTF("au")
            writeInt(entries.size)
            entries.forEach { writeLong(it.first) }
            entries.forEach { writeInt(it.second) }
        }

        val tags = TranscriptionTags.read(file)

        assertThat(tags.of("water", "/ˈwɔtɚ/")).containsExactly("us")
        assertThat(tags.of("route", "/ɹuːt/")).containsExactly("gb", "au").inOrder()
        assertThat(tags.of("route", "/ˈwɔtɚ/")).isEmpty()
        assertThat(tags.size).isEqualTo(2)
    }

    @Test
    fun `a tag set index out of range gives no tags`() {
        val entries = listOf(packed("water", "/ˈwɔtɚ/", 0), packed("route", "/ɹuːt/", 5)).sorted()
        val file = file {
            writeInt(0x53545432)
            writeInt(entries.size)
            entries.forEach(::writeLong)
            writeInt(1)
            writeInt(1)
            writeUTF("us")
        }

        val tags = TranscriptionTags.read(file)

        assertThat(tags.of("water", "/ˈwɔtɚ/")).containsExactly("us")
        assertThat(tags.of("route", "/ɹuːt/")).isEmpty()
    }

    @Test
    fun `a file shorter than its entries is refused`() {
        val file = file {
            writeInt(0x53545432)
            writeInt(3)
            writeLong(packed("water", "/ˈwɔtɚ/", 0))
        }

        assertThrows(IOException::class.java) { TranscriptionTags.read(file) }
    }
}
