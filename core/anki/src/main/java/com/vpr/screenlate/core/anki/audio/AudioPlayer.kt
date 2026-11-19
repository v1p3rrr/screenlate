package com.vpr.screenlate.core.anki.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.log.Log
import com.vpr.screenlate.core.common.redacted
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Plays clips and speaks text at the configured volume; a new sound stops the previous one. Main thread only. */
@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AudioSettingsRepository,
) {
    private var player: MediaPlayer? = null
    private var speech: TextToSpeech? = null
    private var speechStarted: CompletableDeferred<Boolean>? = null
    var isCaptureActive: () -> Boolean = { false }

    /** [canStart] is checked after asynchronous preparation. False if playback could not be submitted. */
    suspend fun play(pronunciation: Pronunciation, canStart: () -> Boolean = { true }): Boolean {
        val volume = settings.volume().coerceIn(0, 100) / 100f
        if (!canStart()) return false
        return when (pronunciation) {
            is Pronunciation.Clip -> playClip(pronunciation.clip, volume, canStart)
            is Pronunciation.Speech -> speak(pronunciation.text, pronunciation.language, volume, canStart)
        }
    }

    fun stop() {
        player?.release()
        player = null
        speech?.stop()
    }

    private fun playFile(
        file: File,
        volume: Float,
        canStart: () -> Boolean,
        deleteOnComplete: Boolean = false,
    ): Boolean {
        stop()
        return runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(ATTRIBUTES)
                setDataSource(file.absolutePath)
                setVolume(volume, volume)
                setOnPreparedListener { prepared ->
                    if (player !== prepared) return@setOnPreparedListener
                    prepared.setOnPreparedListener(null)
                    if (canStart()) {
                        prepared.start()
                    } else {
                        player = null
                        prepared.release()
                    }
                }
                setOnCompletionListener {
                    it.reset()
                    if (deleteOnComplete) file.delete()
                }
                prepareAsync()
            }
        }.onFailure { Log.w(TAG, "Cannot play an audio file", it.redacted()) }.isSuccess
    }

    private fun playClip(clip: AudioClip, volume: Float, canStart: () -> Boolean): Boolean =
        playFile(clip.file, volume, canStart)

    private suspend fun speak(text: String, language: Language, volume: Float, canStart: () -> Boolean): Boolean {
        stop()
        val tts = engine() ?: return false
        if (!canStart()) return false
        // Another language's voice would read the text wrongly or not at all.
        val tags = listOf(language.support.speechTag) + language.support.otherSpeechTags
        if (tags.none { tts.setLanguage(Locale.forLanguageTag(it)) >= TextToSpeech.LANG_AVAILABLE }) {
            Log.i(TAG, "No text-to-speech voice for ${language.code}")
            return false
        }
        tts.setAudioAttributes(ATTRIBUTES)

        if (isCaptureActive()) {
            return speakToFile(tts, text, volume, canStart)
        }

        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE) == TextToSpeech.SUCCESS
    }

    private suspend fun speakToFile(
        tts: TextToSpeech,
        text: String,
        volume: Float,
        canStart: () -> Boolean,
    ): Boolean {
        val tempDir = File(context.cacheDir, "tts_playback").apply { mkdirs() }
        val tempFile = File.createTempFile("tts_", ".wav", tempDir)
        val utteranceId = "tts_${UUID.randomUUID()}"
        val deferred = CompletableDeferred<Boolean>()

        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceIdParam: String?) {}
            override fun onDone(utteranceIdParam: String?) {
                if (utteranceIdParam == utteranceId) deferred.complete(true)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceIdParam: String?) {
                if (utteranceIdParam == utteranceId) deferred.complete(false)
            }
            override fun onError(utteranceIdParam: String?, errorCode: Int) {
                if (utteranceIdParam == utteranceId) deferred.complete(false)
            }
        }
        tts.setOnUtteranceProgressListener(listener)

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        val result = tts.synthesizeToFile(text, params, tempFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            tempFile.delete()
            return false
        }

        val completed = withTimeoutOrNull(5000L) { deferred.await() } == true
        if (!completed || !canStart() || !tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            return false
        }

        return playFile(tempFile, volume, canStart, deleteOnComplete = true)
    }

    /** The engine starts asynchronously on first use. */
    private suspend fun engine(): TextToSpeech? {
        val started = speechStarted ?: CompletableDeferred<Boolean>().also { deferred ->
            speechStarted = deferred
            speech = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) Log.w(TAG, "Text-to-speech is unavailable ($status)")
                deferred.complete(status == TextToSpeech.SUCCESS)
            }
        }
        if (started.await()) return speech
        // Try again next time, e.g. after the user installed an engine.
        speech?.shutdown()
        speech = null
        speechStarted = null
        return null
    }

    private companion object {
        const val TAG = "AudioPlayer"
        const val UTTERANCE = "screenlate"
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
