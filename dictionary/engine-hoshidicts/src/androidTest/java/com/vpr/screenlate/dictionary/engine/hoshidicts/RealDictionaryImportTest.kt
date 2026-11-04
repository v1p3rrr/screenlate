package com.vpr.screenlate.dictionary.engine.hoshidicts

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.LookupOptions
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in acceptance of public full archives staged in this test application's own external files. */
class RealDictionaryImportTest {
    @Test
    fun importsFullFormsAndFrequencyArchives(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realArchives") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archives = File(context.getExternalFilesDir(null), "acceptance")
        val root = File(context.cacheDir, "real-archive-acceptance").apply { mkdirs() }
        val engine = HoshidictsEngine(context)
        try {
            data class Case(val code: String, val language: Language, val form: String, val lemma: String)
            for ((code, language, form, lemma) in listOf(
                Case("ta", Language.TAMIL, "மரங்களை", "மரம்"),
                Case("te", Language.TELUGU, "ఆలోచిస్తాడు", "ఆలోచించు"),
            )) {
                val started = SystemClock.elapsedRealtime()
                val forms = engine.import(File(archives, "wty-$code-en.zip"), File(root, "$code-forms"), formsOnly = true)
                val elapsed = SystemClock.elapsedRealtime() - started
                val terms = engine.import(File(archives, "wty-$code-ru.zip"), File(root, "$code-terms"))
                engine.load(DictionarySet(terms = listOf(terms.directory, forms.directory)))
                val found = engine.lookup(form, LookupOptions(language = language))
                    .first { it.term.expression == lemma }
                assertThat(found.trace.any { it.form }).isTrue()
                assertThat(found.term.glossaries.map { it.dictionary }).containsExactly(terms.metadata.title)
                val bytes = forms.directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                println("Full forms import $code: ${elapsed}ms, ${bytes}B")
            }
            val ranks = linkedMapOf("我哋" to 279, "佢哋" to 391, "你哋" to 1110, "喺度" to 870, "為咗" to 1172)
            val fixture = File(root, "frequency-terms.zip")
            ZipOutputStream(fixture.outputStream()).use { zip ->
                fun entry(name: String, value: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(value.toByteArray())
                    zip.closeEntry()
                }
                entry("index.json", """{"title":"Acceptance terms","revision":"1","format":3,"sourceLanguage":"yue"}""")
                entry("term_bank_1.json", ranks.keys.joinToString(",", "[", "]") { """["$it","","","",0,["fixture"],0,""]""" })
            }
            val terms = engine.import(fixture, File(root, "frequency-terms"))
            val frequency = engine.import(File(archives, "wordshk-frequency.zip"), File(root, "frequency"))
            assertThat(frequency.metadata.frequencyCount).isEqualTo(34_933)
            engine.load(DictionarySet(terms = listOf(terms.directory), frequencies = listOf(frequency.directory)))
            for ((word, rank) in ranks) {
                val found = engine.lookup(word, LookupOptions(language = Language.CANTONESE)).first { it.term.expression == word }
                assertThat(found.term.frequencies.single().values.single().value).isEqualTo(rank)
            }
        } finally {
            engine.load(DictionarySet())
            root.deleteRecursively()
        }
    }
}
