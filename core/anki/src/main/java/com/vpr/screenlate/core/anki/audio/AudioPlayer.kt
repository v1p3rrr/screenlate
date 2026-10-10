package com.vpr.screenlate.core.anki.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.redacted
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred

/** Plays clips and speaks text at the configured volume; a new sound stops the previous one. Main thread only. */
@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AudioSettingsRepository,
) {
    private var player: MediaPlayer? = null
    private var speech: TextToSpeech? = null
    private var speechStarted: CompletableDeferred<Boolean>? = null

    /** False when nothing could be played, e.g. the system has no text-to-speech voice for the language. */
    suspend fun play(pronunciation: Pronunciation): Boolean {
        val volume = settings.volume().coerceIn(0, 100) / 100f
        return when (pronunciation) {
            is Pronunciation.Clip -> playClip(pronunciation.clip, volume)
            is Pronunciation.Speech -> speak(pronunciation.text, pronunciation.language, volume)
        }
    }

    fun stop() {
        player?.release()
        player = null
        speech?.stop()
    }

    private fun playClip(clip: AudioClip, volume: Float): Boolean {
        stop()
        return runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(ATTRIBUTES)
                setDataSource(clip.file.absolutePath)
                setVolume(volume, volume)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { it.reset() }
                prepareAsync()
            }
        }.onFailure { Log.w(TAG, "Cannot play an audio clip", it.redacted()) }.isSuccess
    }

    private suspend fun speak(text: String, language: Language, volume: Float): Boolean {
        stop()
        val tts = engine() ?: return false
        // Another language's voice would read the text wrongly or not at all.
        if (tts.setLanguage(Locale.forLanguageTag(language.code)) < TextToSpeech.LANG_AVAILABLE) {
            Log.i(TAG, "No text-to-speech voice for ${language.code}")
            return false
        }
        tts.setAudioAttributes(ATTRIBUTES)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE) == TextToSpeech.SUCCESS
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
