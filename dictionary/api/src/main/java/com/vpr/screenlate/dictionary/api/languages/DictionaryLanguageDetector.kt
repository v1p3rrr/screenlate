package com.vpr.screenlate.dictionary.api.languages

import android.content.Context
import android.util.Log
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The languages of a dictionary as far as its content tells; null where it does not. */
data class DetectedLanguages(val source: String?, val target: String?)

/**
 * Tells a dictionary's languages from a [DictionarySample] when neither its index.json nor the catalog names them:
 * the headwords give the source language, the definitions the target language. Latin and Cyrillic text goes to
 * Android's on-device language detection.
 */
@Singleton
class DictionaryLanguageDetector @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun detect(sample: DictionarySample): DetectedLanguages = withContext(Dispatchers.Default) {
        DetectedLanguages(
            source = LanguageGuess.of(sample.headwords, ::identify),
            target = LanguageGuess.of(sample.definitions, ::identify),
        )
    }

    /** The language of [text] when the system's detector is confident about it. */
    private fun identify(text: String): String? = runCatching {
        val classifier = context.getSystemService(TextClassificationManager::class.java)?.textClassifier
            ?: return@runCatching null
        val result = classifier.detectLanguage(TextLanguage.Request.Builder(text).build())
        if (result.localeHypothesisCount == 0) return@runCatching null
        val locale = result.getLocale(0)
        locale.language.takeIf { it.isNotEmpty() && result.getConfidenceScore(locale) >= MIN_CONFIDENCE }
    }.onFailure { Log.w(TAG, "Language detection failed", it) }.getOrNull()

    private companion object {
        const val TAG = "DictionaryLanguages"
        const val MIN_CONFIDENCE = 0.5f
    }
}
