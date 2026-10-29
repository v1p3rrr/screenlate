package com.vpr.screenlate.dictionary.engine.hoshidicts

import android.content.Context
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.HeadwordMarks
import com.vpr.screenlate.core.common.language.SearchResolution
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.dictionary.api.DictionaryEngine
import com.vpr.screenlate.dictionary.api.DictionaryImportException
import com.vpr.screenlate.dictionary.api.DictionaryMetadata
import com.vpr.screenlate.dictionary.api.registry.decodeIndexText
import com.vpr.screenlate.dictionary.api.DictionarySet
import com.vpr.screenlate.dictionary.api.FormOfTags
import com.vpr.screenlate.dictionary.api.FrequencyOrder
import com.vpr.screenlate.dictionary.api.ImportedDictionary
import com.vpr.screenlate.dictionary.api.LookupOptions
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.KanjiResult
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.dictionary.api.model.Transform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DictionaryEngine] backed by hoshidicts. One native session holds the loaded dictionaries. Japanese is looked up by
 * hoshidicts itself; other languages through Yomitan's language code (`assets/yomitan-language`, see
 * `src/main/cpp/language`). Inflection names and descriptions come in the interface language from [context]'s
 * resources.
 */
@Singleton
class HoshidictsEngine @Inject constructor(@param:ApplicationContext private val context: Context) : DictionaryEngine {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var handle = 0L

    override suspend fun import(archive: File, outputDir: File, formsOnly: Boolean): ImportedDictionary = withContext(Dispatchers.IO) {
        ArchiveTitles.check(archive)
        outputDir.mkdirs()
        val raw = try {
            HoshidictsNative.importDictionary(
                archive.absolutePath.encodeToByteArray(),
                outputDir.absolutePath.encodeToByteArray(),
                false,
                HEADWORD_MARKS,
                formsOnly,
            )
        } catch (e: RuntimeException) {
            throw DictionaryImportException(e.message ?: "Import failed", e)
        }
        val result = json.decodeFromString<NativeImportResult>(raw.decodeToString())
        if (!result.success) throw DictionaryImportException(result.error.ifEmpty { "Import failed" })
        ImportedDictionary(
            directory = File(outputDir, result.title),
            metadata = result.summary.toMetadata(result.title),
        )
    }

    override suspend fun load(dictionaries: DictionarySet) = session { handle ->
        HoshidictsNative.load(
            handle,
            dictionaries.terms.toPaths(),
            dictionaries.frequencies.toPaths(),
            dictionaries.pitches.toPaths(),
            dictionaries.kanji.toPaths(),
        )
    }

    override suspend fun lookup(text: String, options: LookupOptions): List<LookupResult> =
        if (options.language == NATIVE_LANGUAGE) lookupNative(text, options) else lookupThroughYomitan(text, options)

    private suspend fun lookupNative(text: String, options: LookupOptions): List<LookupResult> = session { handle ->
        val frequencyDictionary = options.frequencyDictionary
            ?.takeIf { options.frequencyOrder != FrequencyOrder.DISABLED }
        val raw = HoshidictsNative.lookup(
            handle = handle,
            text = text.encodeToByteArray(),
            maxResults = options.maxResults,
            scanLength = options.scanLength,
            frequencyDictionary = frequencyDictionary?.encodeToByteArray(),
            frequencyOrder = if (frequencyDictionary == null) ORDER_DISABLED else options.frequencyOrder.toNative(),
            primaryReading = options.primaryReading?.encodeToByteArray(),
        )
        json.decodeFromString<List<LookupResult>>(raw.decodeToString()).map { result ->
            if (result.trace.isEmpty()) {
                result
            } else {
                result.copy(trace = result.trace.map { JapaneseInflections.localize(context.resources, it) })
            }
        }
    }

    private suspend fun lookupThroughYomitan(text: String, options: LookupOptions): List<LookupResult> = session { handle ->
        val language = options.language
        // Languages that share dictionaries (Croatian and Serbian) share their bundle too.
        val code = language.dictionaryCode.encodeToByteArray()
        if (!HoshidictsNative.hasLanguage(handle, code)) {
            val script = context.assets.open("$SCRIPTS/${language.dictionaryCode}.js").use { it.readBytes() }
            HoshidictsNative.loadLanguage(handle, code, script)
        }
        val frequencyDictionary = options.frequencyDictionary
            ?.takeIf { options.frequencyOrder != FrequencyOrder.DISABLED }
        val resolution = when (options.searchResolution ?: language.support.searchResolution) {
            SearchResolution.WORD -> "word"
            SearchResolution.LETTER -> "letter"
        }
        val raw = HoshidictsNative.lookupLanguage(
            handle = handle,
            language = code,
            text = text.encodeToByteArray(),
            resolution = resolution.encodeToByteArray(),
            maxResults = options.maxResults,
            scanLength = options.scanLength,
            frequencyDictionary = frequencyDictionary?.encodeToByteArray(),
            frequencyDescending = options.frequencyOrder == FrequencyOrder.DESCENDING,
            primaryReading = options.primaryReading?.encodeToByteArray(),
            fromOcr = options.fromOcr,
        )
        val support = language.support
        json.decodeFromString<List<LookupResult>>(raw.decodeToString()).let { results ->
            when {
                support.inflectionRulesOnlyWithoutEntry ->
                    results.withoutRuleOnlyResults(YomitanInflections.names(language.dictionaryCode))
                support.ruleChainsOnlyWithoutRow ->
                    results.withoutRuleChainsBesideFreeOnes(YomitanInflections.names(language.dictionaryCode))
                else -> results
            }
        }.withFormTagsAsRules(support.formTagRules).let { results ->
            // After the chains of two mutations are gone: ddwr finds ddŵr's lemma, not twr through dwr.
            if (support.respelledOnlyWithoutEntry) results.withoutRespelledBesideEntries() else results
        }.map { result ->
            if (result.trace.isEmpty() && result.otherTraces.isEmpty()) {
                result
            } else {
                result.copy(
                    trace = result.trace.map { localize(language, it) },
                    otherTraces = result.otherTraces.map { chain -> chain.map { localize(language, it) } },
                )
            }
        }
    }

    /**
     * A rule of the language's deinflection, else a tag of a form-of entry, cleaned of leftover gloss; unknown names
     * stay as they are.
     */
    private fun localize(language: Language, transform: Transform): Transform {
        YomitanInflections.localize(context.resources, language.dictionaryCode, transform)?.let { return it }
        val tag = transform.copy(name = FormOfTags.clean(transform.name))
        return FormOfTags.label(context.resources, tag.name, language.support.formTagAliases, language.support.formTagNames)
            ?.let { tag.copy(label = it) } ?: tag
    }

    override suspend fun styles(): List<DictionaryStyle> = session { handle ->
        json.decodeFromString<List<DictionaryStyle>>(HoshidictsNative.styles(handle).decodeToString())
    }

    override suspend fun media(dictionary: String, path: String): ByteArray? = session { handle ->
        HoshidictsNative.media(handle, dictionary.encodeToByteArray(), path.encodeToByteArray())
    }

    override suspend fun kanji(character: String): KanjiResult = session { handle ->
        json.decodeFromString<KanjiResult>(HoshidictsNative.kanji(handle, character.encodeToByteArray()).decodeToString())
    }

    private suspend fun <T> session(block: (Long) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle == 0L) handle = HoshidictsNative.create()
            block(handle)
        }
    }

    /** A form-of table cut short (see `form_of_table.hpp`) makes the dictionary broken; one without a table is whole. */
    override fun isComplete(directory: File): Boolean = FormOfTableFile.isComplete(File(directory, FORM_OF_TABLE))

    private fun List<File>.toPaths(): Array<ByteArray> = map { it.absolutePath.encodeToByteArray() }.toTypedArray()

    private fun FrequencyOrder.toNative(): Int = when (this) {
        FrequencyOrder.ASCENDING -> ORDER_ASCENDING
        FrequencyOrder.DESCENDING -> ORDER_DESCENDING
        FrequencyOrder.DISABLED -> ORDER_DISABLED
    }

    internal companion object {
        /** hoshidicts' own lookup and deinflector are Japanese's. */
        val NATIVE_LANGUAGE = Language.JAPANESE

        /** Asset folder of the language bundles, built by `scripts/yomitan-language/build.mjs`. */
        const val SCRIPTS = "yomitan-language"

        /** Written next to a dictionary's files by the import (`jni_bridge.cpp`). */
        const val FORM_OF_TABLE = "form_of.bin"

        /**
         * Each language's [headwordMarks][com.vpr.screenlate.core.common.language.LanguageSupport.headwordMarks] and
         * [foldsMarks][com.vpr.screenlate.core.common.language.LanguageSupport.foldsMarks], by the source language
         * dictionaries name ([Language.dictionaryCode], and the [other codes][Language.otherCodes] they may name).
         */
        val HEADWORD_MARKS: ByteArray = buildJsonObject {
            for (language in Language.entries.distinctBy { it.dictionaryCode }) {
                val support = language.support
                val marks = support.headwordMarks
                    ?: if (support.foldsMarks) HeadwordMarks(marks = "", bases = "") else continue
                val entry = buildJsonObject {
                    put("marks", marks.marks)
                    put("bases", marks.bases)
                    put("every_headword", marks.everyHeadword)
                    put("replacements", buildJsonObject { marks.replacements.forEach { (from, to) -> put(from, to) } })
                    put(
                        "further_replacements",
                        buildJsonObject { marks.furtherReplacements.forEach { (from, to) -> put(from, to) } },
                    )
                    put("lemmas_as_written", marks.lemmasAsWritten)
                    put("forms_in_script", marks.formsInScript)
                    put("mark_bases", buildJsonObject { marks.markBases.forEach { (mark, bases) -> put(mark, bases) } })
                    put("letter_marks", marks.letterMarks)
                    put("letter_bases", marks.letterBases)
                    put("comparative_prefixes", buildJsonArray { marks.comparativePrefixes.forEach { add(it) } })
                    put("dropped_forms", buildJsonArray { marks.droppedForms.forEach { add(it) } })
                    put("fold_marks", support.foldsMarks)
                }
                for (code in listOf(language.dictionaryCode) + Language.otherCodes(language)) put(code, entry)
            }
        }.toString().encodeToByteArray()

        // Values of hoshidicts' LookupFrequencyOrder.
        const val ORDER_ASCENDING = 1
        const val ORDER_DESCENDING = 2
        const val ORDER_DISABLED = 3
    }
}

@Serializable
private data class NativeImportResult(
    val success: Boolean,
    val title: String = "",
    val error: String = "",
    val summary: NativeSummary = NativeSummary(),
)

@Serializable
private data class NativeSummary(
    val revision: String = "",
    val isUpdatable: Boolean? = null,
    val indexUrl: String? = null,
    val downloadUrl: String? = null,
    val author: String? = null,
    val url: String? = null,
    val description: String? = null,
    val attribution: String? = null,
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val frequencyMode: String? = null,
    val counts: NativeCounts = NativeCounts(),
) {
    fun toMetadata(title: String) = DictionaryMetadata(
        title = title,
        revision = decodeIndexText(revision) ?: revision,
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        frequencyMode = frequencyMode,
        isUpdatable = isUpdatable == true,
        // The engine returns index strings as raw JSON; the title stays raw, as lookups name dictionaries by it.
        indexUrl = decodeIndexText(indexUrl),
        downloadUrl = decodeIndexText(downloadUrl),
        author = decodeIndexText(author),
        url = decodeIndexText(url),
        description = decodeIndexText(description),
        attribution = decodeIndexText(attribution),
        termCount = counts.terms.total,
        frequencyCount = counts.termMeta["freq"] ?: 0,
        pitchCount = (counts.termMeta["pitch"] ?: 0) + (counts.termMeta["ipa"] ?: 0),
        kanjiCount = counts.kanji.total,
        mediaCount = counts.media.total,
    )
}

@Serializable
private data class NativeCounts(
    val terms: NativeCount = NativeCount(),
    val termMeta: Map<String, Long> = emptyMap(),
    val kanji: NativeCount = NativeCount(),
    val media: NativeCount = NativeCount(),
)

@Serializable
private data class NativeCount(val total: Long = 0)
