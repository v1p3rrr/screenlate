package com.vpr.screenlate.dictionary.api.imports

import com.vpr.screenlate.core.common.language.LanguageSupport
import com.vpr.screenlate.dictionary.api.registry.ReadingIndex
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * The [ReadingIndex] of an archive's `term_bank_*.json` files: rows `[expression, reading, ...]`, each reading keyed
 * as the language shows it (`LanguageSupport.displayReading`, then `readingKey`).
 */
object ReadingIndexBanks {

    /**
     * The index, or null when no reading has a key. The banks are read on several threads, each with a file of its
     * own: reading them takes longer than the engine's import otherwise.
     */
    suspend fun read(archive: File, support: LanguageSupport): ReadingIndex.Builder? = withContext(Dispatchers.Default) {
        val banks = ZipFile(archive).use { zip ->
            zip.entries().asSequence().map { it.name }.filter { BANK.matches(it.substringAfterLast('/')) }.sorted().toList()
        }
        val threads = minOf(banks.size, Runtime.getRuntime().availableProcessors(), MAX_THREADS).coerceAtLeast(1)
        val parts = List(threads) { thread -> banks.filterIndexed { index, _ -> index % threads == thread } }
            .map { names ->
                async {
                    ZipFile(archive).use { zip ->
                        val builder = ReadingIndex.Builder()
                        for (name in names) {
                            ensureActive()
                            zip.getInputStream(zip.getEntry(name)).use { parse(it, support, builder) }
                        }
                        builder
                    }
                }
            }
            .awaitAll()
        val builder = parts.first()
        parts.drop(1).forEach(builder::addAll)
        builder.takeUnless { it.isEmpty }
    }

    internal fun parse(input: InputStream, support: LanguageSupport, into: ReadingIndex.Builder) {
        val scanner = RawJsonScanner(input)
        scanner.beginArray()
        while (scanner.hasNext()) {
            if (scanner.peek() != ARRAY_START) {
                scanner.skipValue()
                continue
            }
            scanner.beginArray()
            val expression = if (scanner.hasNext()) stringOrSkip(scanner) else null
            val reading = if (scanner.hasNext()) stringOrSkip(scanner) else null
            while (scanner.hasNext()) scanner.skipValue()
            scanner.endArray()
            if (expression == null) continue
            into.add(expression, reading?.takeIf { it.isNotEmpty() }?.let { support.readingKey(support.displayReading(it)) })
        }
        scanner.endArray()
    }

    private fun stringOrSkip(scanner: RawJsonScanner): String? =
        if (scanner.peek() == QUOTE) scanner.nextString() else null.also { scanner.skipValue() }

    private const val MAX_THREADS = 4
    private const val QUOTE = '"'.code.toByte()
    private const val ARRAY_START = '['.code.toByte()
    private val BANK = Regex("""term_bank_\d+\.json""")
}
