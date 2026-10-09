package com.vpr.screenlate.core.translate

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.locale.AppLanguage
import com.vpr.screenlate.core.common.redacted
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/** What a translation request gave. */
sealed interface TranslationResult {
    data class Success(val text: String, val service: TranslationService) : TranslationResult

    /** Every enabled service failed, each with its [failures] entry; empty when no service is turned on. */
    data class Failure(val failures: List<ServiceFailure>) : TranslationResult
}

data class ServiceFailure(val service: TranslationService, val error: TranslationError)

/** One service's answer in the settings test, after [timeMs]. */
data class ServiceTest(val service: TranslationService, val timeMs: Long, val text: String?, val error: TranslationError?)

/**
 * Translates sentences with the services in the user's order: the first enabled one translates, the next one takes
 * over when it fails or gives no answer within [serviceTimeoutMs].
 *
 * A request runs on its own: a caller that stops waiting (a note that waited long enough) leaves it running, callers
 * asking for the same text share it, and a translation is kept for [CACHE_MS], so a note gets the one the popup shows.
 */
@Singleton
class SentenceTranslator internal constructor(
    private val translators: List<Translator>,
    private val settings: TranslationSettingsRepository,
    private val interfaceLocale: () -> Locale,
    private val serviceTimeoutMs: Long = SERVICE_TIMEOUT_MS,
    private val testTimeoutMs: Long = TEST_TIMEOUT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
        settings: TranslationSettingsRepository,
    ) : this(
        listOf(BingTranslator(httpClient), GoogleTranslator(httpClient), EdgeTranslator(httpClient)),
        settings,
        { AppLanguage.locales(context)?.get(0) ?: Resources.getSystem().configuration.locales[0] },
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = mutableMapOf<Key, Deferred<TranslationResult>>()
    private val kept = LinkedHashMap<Key, Kept>()

    /** The language translations go into: the chosen one, else the interface language. */
    fun target(settings: TranslationSettings, from: Language): TranslationLanguage =
        settings.language?.let(TranslationLanguages::of) ?: TranslationLanguages.defaultFor(interfaceLocale(), from)

    /** The language the settings list shows for "the interface language". */
    fun interfaceTarget(from: Language): TranslationLanguage = TranslationLanguages.defaultFor(interfaceLocale(), from)

    suspend fun translate(text: String, from: Language): TranslationResult {
        val current = settings.current()
        val target = target(current, from)
        val key = Key(text, from, target.tag, current.enabledServices)
        val job = synchronized(this) {
            kept[key]?.takeIf { clock() - it.at < CACHE_MS }?.let { return it.result }
            running[key]?.takeIf { it.isActive } ?: scope.async {
                val result = cascade(text, from, target, key.services)
                synchronized(this@SentenceTranslator) {
                    running.remove(key)
                    if (result is TranslationResult.Success) keep(key, result)
                }
                result
            }.also { running[key] = it }
        }
        return job.await()
    }

    /** What [service] makes of [text], with the time it took; for the settings test, so nothing is kept. */
    suspend fun test(service: TranslationService, text: String, from: Language): ServiceTest {
        val target = target(settings.current(), from)
        val started = clock()
        val outcome = attempt(translatorOf(service), text, from, target, testTimeoutMs)
        val time = clock() - started
        outcome.exceptionOrNull()?.let { Log.i(TAG, "$service test failed after $time ms: ${TranslationError.of(it)}", it.redacted()) }
        return outcome.fold(
            onSuccess = { ServiceTest(service, time, it, null) },
            onFailure = { ServiceTest(service, time, null, TranslationError.of(it)) },
        )
    }

    private suspend fun cascade(
        text: String,
        from: Language,
        target: TranslationLanguage,
        services: List<TranslationService>,
    ): TranslationResult {
        val failures = mutableListOf<ServiceFailure>()
        for (service in services) {
            val started = clock()
            val outcome = attempt(translatorOf(service), text, from, target, serviceTimeoutMs)
            outcome.getOrNull()?.let {
                Log.d(TAG, "$service translated in ${clock() - started} ms")
                return TranslationResult.Success(it, service)
            }
            val error = outcome.exceptionOrNull()!!
            // The kind only: messages may carry the text.
            Log.i(TAG, "$service failed after ${clock() - started} ms: ${TranslationError.of(error)}", error.redacted())
            failures += ServiceFailure(service, TranslationError.of(error))
        }
        return TranslationResult.Failure(failures)
    }

    private suspend fun attempt(
        translator: Translator,
        text: String,
        from: Language,
        target: TranslationLanguage,
        timeoutMs: Long,
    ): Result<String> = try {
        val to = translator.code(target)
        val source = TranslationLanguages.of(from)?.let(translator::code)
        when {
            to == null || source == null -> Result.failure(TranslationException(TranslationError.Kind.UNSUPPORTED_LANGUAGE))
            text.length > translator.maxLength -> Result.failure(TranslationException(TranslationError.Kind.TOO_LONG))
            else -> {
                val translated = withTimeoutOrNull(timeoutMs) { translator.translate(text, source, to) }
                when {
                    translated == null -> Result.failure(TranslationException(TranslationError.Kind.TIMEOUT))
                    translated.isBlank() -> Result.failure(TranslationException(TranslationError.Kind.BAD_ANSWER))
                    else -> Result.success(translated.trim())
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun translatorOf(service: TranslationService): Translator = translators.first { it.service == service }

    private fun keep(key: Key, result: TranslationResult) {
        // Taken out first, so a translation made again after its entry expired goes to the end and is evicted last.
        kept.remove(key)
        kept[key] = Kept(result, clock())
        while (kept.size > CACHE_SIZE) kept.remove(kept.keys.first())
    }

    private data class Key(val text: String, val from: Language, val target: String, val services: List<TranslationService>)

    private class Kept(val result: TranslationResult, val at: Long)

    private companion object {
        const val TAG = "SentenceTranslator"
        const val SERVICE_TIMEOUT_MS = 3_000L
        const val TEST_TIMEOUT_MS = 15_000L
        const val CACHE_MS = 10 * 60_000L
        const val CACHE_SIZE = 16
    }
}
