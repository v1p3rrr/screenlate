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
import kotlinx.coroutines.withTimeoutOrNull
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
     * The translation for a note: the one the popup shows, or a new one waited for up to [NOTE_WAIT_MS]. Null when
     * notes get none (the switch is off) or none came in time.
     */
    suspend fun forNote(sentence: String, language: Language): String? = try {
        if (sentence.isBlank() || !settings.current().ankiField) {
            null
        } else {
            val result = withTimeoutOrNull(NOTE_WAIT_MS) { translator.translate(sentence, language) }
            (result as? TranslationResult.Success)?.text
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // The note goes in without it.
        Log.w(TAG, "Translation for a note failed", e.redacted())
        null
    }

    /** What the popup's block shows for [sentence]: `{text, service, sentence}` (the sentence for copying) or `{error}`. */
    suspend fun forPopup(context: Context, sentence: String?, language: Language): JsonObject {
        if (sentence.isNullOrBlank()) return buildJsonObject { put("error", context.getString(R.string.translation_no_sentence)) }
        return when (val result = translator.translate(sentence, language)) {
            is TranslationResult.Success -> buildJsonObject {
                put("text", result.text)
                put("service", result.service.label)
                put("sentence", sentence)
            }
            is TranslationResult.Failure -> buildJsonObject { put("error", failureText(context, result)) }
        }
    }

    companion object {
        private const val TAG = "SentenceTranslation"

        /** How long ➕ waits for a translation before the note goes in without one. */
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
