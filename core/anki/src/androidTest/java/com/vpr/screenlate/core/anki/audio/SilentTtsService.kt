package com.vpr.screenlate.core.anki.audio

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import java.util.Locale
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded silence through the real platform synthesis transport; no network, voice packs or tones. */
class SilentTtsService : TextToSpeechService() {
    private val stopped = AtomicBoolean()
    override fun onGetLanguage() = arrayOf("eng", "USA", "")
    override fun onIsLanguageAvailable(language: String?, country: String?, variant: String?): Int =
        if (language != "eng" && language != "en") TextToSpeech.LANG_NOT_SUPPORTED
        else if (country == "USA") TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
    override fun onGetDefaultVoiceNameFor(language: String?, country: String?, variant: String?) = "en-US"
    override fun onIsValidVoiceName(voiceName: String?) = if (voiceName == "en-US") TextToSpeech.SUCCESS else TextToSpeech.ERROR
    override fun onLoadVoice(voiceName: String?) = onIsValidVoiceName(voiceName)
    override fun onGetVoices() = listOf(Voice("en-US", Locale.US, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, emptySet()))
    override fun onLoadLanguage(language: String?, country: String?, variant: String?) =
        onIsLanguageAvailable(language, country, variant)
    override fun onStop() { stopped.set(true) }
    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped.set(false)
        if (callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
        val block = ByteArray(minOf(callback.maxBufferSize, 3200))
        var remaining = 16000 * 2 * 8
        var liveMarked = false
        while (remaining > 0 && !stopped.get()) {
            val count = minOf(block.size, remaining)
            if (callback.audioAvailable(block, 0, count) != TextToSpeech.SUCCESS) return
            remaining -= count
            if (!liveMarked && remaining <= 16000 * 2 * 8 - 9600 &&
                request.params.containsKey(TextToSpeech.Engine.KEY_PARAM_VOLUME)) {
                File(cacheDir, LIVE_READY).writeBytes(byteArrayOf(1))
                liveMarked = true
            }
        }
        callback.done()
    }

    companion object { const val LIVE_READY = "tts-fixture-live-ready" }
}
