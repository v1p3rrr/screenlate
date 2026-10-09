package com.vpr.screenlate.overlay.translate

import android.content.Context
import android.util.Log
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.redacted
import com.vpr.screenlate.core.translate.SentenceTranslator
import com.vpr.screenlate.core.translate.TranslationError
import com.vpr.screenlate.core.translate.TranslationResult
import com.vpr.screenlate.core.translate.TranslationSettingsRepository
import com.vpr.screenlate.overlay.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Sentence translation for the popup's 文A and for the notes' `{sentence-translation}`. */
@Singleton
class SentenceTranslation @Inject constructor(
    val translator: SentenceTranslator,
    val settings: TranslationSettingsRepository,
) {
    /**
     * The translation for a note: the one the popup shows, or a new one. Null when notes get none (the switch is off)
     * or no service translated. The caller limits the wait ([NOTE_WAIT_MS]).
     */
    suspend fun forNote(sentence: String, language: Language): String? = try {
        if (sentence.isBlank() || !settings.current().ankiField) {
            null
        } else {
            (translator.translate(sentence, language) as? TranslationResult.Success)?.text
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // The note goes in without it.
        Log.w(TAG, "Translation for a note failed", e.redacted())
        null
    }

    /** What the popup's block shows for [sentence]: `{text, service, sentence}` (the sentence for copying) or `{error}`. */
    suspend fun forPopup(context: Context, sentence: String?, language: Language): JsonObject = try {
        if (sentence.isNullOrBlank()) {
            errorAnswer(context.getString(R.string.translation_no_sentence))
        } else {
            when (val result = translator.translate(sentence, language)) {
                is TranslationResult.Success -> buildJsonObject {
                    put("text", result.text)
                    put("service", result.service.label)
                    put("sentence", sentence)
                }
                is TranslationResult.Failure -> errorAnswer(failureText(context, result))
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Translation for the popup failed", e.redacted())
        errorAnswer(context.getString(R.string.translation_failed_other))
    }

    private fun errorAnswer(text: String): JsonObject = buildJsonObject { put("error", text) }

    companion object {
        private const val TAG = "SentenceTranslation"

        /** How long a note, once ready (after the crop editor), waits for its translation before going in without one. */
        const val NOTE_WAIT_MS = 5_000L

        /** A line per service that failed, under a heading; or why no service was asked. */
        fun failureText(context: Context, failure: TranslationResult.Failure): String {
            if (failure.failures.isEmpty()) return context.getString(R.string.translation_none_enabled)
            val lines = failure.failures.map { context.getString(R.string.translation_service_error, it.service.label, errorText(context, it.error)) }
            return (listOf(context.getString(R.string.translation_failed)) + lines).joinToString("\n")
        }

        /** Why one service gave no translation. */
        fun errorText(context: Context, error: TranslationError): String = when (error.kind) {
            TranslationError.Kind.OFFLINE -> context.getString(R.string.translation_error_offline)
            TranslationError.Kind.TIMEOUT -> context.getString(R.string.translation_error_timeout)
            TranslationError.Kind.CERTIFICATE_DATE -> context.getString(R.string.translation_error_clock)
            TranslationError.Kind.SECURE_CONNECTION -> context.getString(R.string.translation_error_secure)
            TranslationError.Kind.NETWORK -> context.getString(R.string.translation_error_network)
            TranslationError.Kind.HTTP_STATUS -> context.getString(R.string.translation_error_http, error.code)
            TranslationError.Kind.LIMITED -> context.getString(R.string.translation_error_limited)
            TranslationError.Kind.CAPTCHA -> context.getString(R.string.translation_error_captcha)
            TranslationError.Kind.REJECTED -> context.getString(R.string.translation_error_rejected)
            TranslationError.Kind.UNSUPPORTED_LANGUAGE -> context.getString(R.string.translation_error_language)
            TranslationError.Kind.TOO_LONG -> context.getString(R.string.translation_error_too_long)
            TranslationError.Kind.BAD_ANSWER -> context.getString(R.string.translation_error_bad_answer)
            TranslationError.Kind.OTHER -> context.getString(R.string.translation_error_other)
        }
    }
}
