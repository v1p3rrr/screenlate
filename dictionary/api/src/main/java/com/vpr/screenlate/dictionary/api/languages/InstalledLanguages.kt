package com.vpr.screenlate.dictionary.api.languages

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.dictionary.api.DictionaryEngine
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Fills in the languages of dictionaries installed before imports detected them, once. Their archives are gone, so
 * the sample comes from lookups: common Japanese words, grammar patterns and kanji looked up in all enabled
 * dictionaries, with each dictionary's definitions kept apart. A dictionary that answers Japanese lookups has Japanese
 * headwords; its definitions tell the target language. Disabled dictionaries and ones no sample word reaches keep
 * what they have.
 */
@Singleton
class InstalledLanguages @Inject constructor(
    private val repository: DictionaryRepository,
    private val engine: DictionaryEngine,
    private val detector: DictionaryLanguageDetector,
    private val preferences: DataStore<Preferences>,
) {
    suspend fun fillOnce() {
        if (preferences.data.first()[FILLED] == true) return
        val missing = repository.getAll().filter { it.sourceLanguage == null || (it.kind.hasTarget && it.targetLanguage == null) }
        if (missing.isNotEmpty()) {
            val samples = sample(missing.map { it.title }.toSet())
            for (dictionary in missing) {
                val text = samples[dictionary.title] ?: continue
                val target = if (dictionary.kind.hasTarget) {
                    detector.detect(DictionarySample(headwords = "", definitions = text.toString())).target
                } else {
                    null
                }
                repository.fillLanguages(dictionary.id, source = SOURCE, target = target)
                Log.i(TAG, "Filled in the languages of a dictionary: ${dictionary.kind}, target ${target ?: "unknown"}")
            }
        }
        preferences.edit { it[FILLED] = true }
    }

    /** Definition text by dictionary title for the dictionaries in [titles] that the sample words reach. */
    private suspend fun sample(titles: Set<String>): Map<String, StringBuilder> = repository.withLookup(Language.JAPANESE) { prepared ->
        val texts = mutableMapOf<String, StringBuilder>()
        fun add(dictionary: String, text: () -> String) {
            if (dictionary !in titles) return
            val builder = texts.getOrPut(dictionary) { StringBuilder() }
            if (builder.length < MAX_CHARS) builder.append(text()).append(' ')
        }
        for (word in WORDS) {
            val results = runCatching {
                engine.lookup(word, prepared.options.copy(scanLength = word.length, maxResults = MAX_RESULTS))
            }.getOrElse { emptyList() }
            for (result in results) {
                val term = result.term
                term.glossaries.forEach { glossary ->
                    add(glossary.dictionary) {
                        val content = runCatching { Json.parseToJsonElement(glossary.content) }.getOrNull()
                        StringBuilder().also { out -> content?.let { DictionarySample.glossaryText(it, out) } }.toString()
                    }
                }
                term.frequencies.forEach { add(it.dictionary) { "" } }
                term.pitches.forEach { add(it.dictionary) { "" } }
            }
        }
        for (character in KANJI) {
            runCatching { engine.kanji(character) }.getOrNull()?.entries?.forEach { entry ->
                add(entry.dictionary) { entry.definitions.joinToString(" ") }
            }
        }
        texts
    }

    private companion object {
        const val TAG = "DictionaryLanguages"
        val FILLED = booleanPreferencesKey("installed_languages_filled")

        /** Every sampled dictionary answered Japanese lookups. */
        const val SOURCE = "ja"
        const val MAX_RESULTS = 8
        const val MAX_CHARS = 6000

        /** Everyday words, grammar patterns and names, so general, grammar and name dictionaries all answer. */
        val WORDS = listOf(
            "日本", "人", "年", "時間", "今日", "明日", "私", "友達", "学校", "先生", "本", "水", "山", "川", "家", "車",
            "仕事", "言葉", "気持ち", "問題", "世界", "天気", "電車", "病気", "結婚",
            "する", "行く", "来る", "見る", "食べる", "飲む", "言う", "思う", "分かる", "書く", "読む", "話す", "聞く",
            "待つ", "作る", "使う", "考える", "始める", "ある", "いる", "なる", "できる",
            "大きい", "小さい", "新しい", "早い", "高い", "美しい", "好き", "静か", "大切",
            "とても", "少し", "もう", "まだ", "やはり",
            "ながら", "ように", "ばかり", "について", "によって", "はず", "わけ", "ため", "ことができる", "かもしれない",
            "田中", "山田", "東京", "大阪",
        )
        val KANJI = listOf("日", "人", "大", "水", "山", "学", "見", "行")
    }
}
