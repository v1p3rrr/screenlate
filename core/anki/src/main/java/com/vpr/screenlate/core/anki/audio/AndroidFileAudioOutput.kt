package com.vpr.screenlate.core.anki.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

internal class AndroidFileAudioOutput : FileAudioOutput {
    private val player = MediaPlayer()
    private var closed = false

    override fun prepare(file: File, volume: Float, ready: () -> Unit, finished: () -> Unit) {
        player.setAudioAttributes(speechAudioAttributes())
        player.setVolume(volume, volume)
        player.setDataSource(file.absolutePath)
        player.setOnPreparedListener { if (!closed) ready() }
        player.setOnCompletionListener { if (!closed) finished() }
        player.setOnErrorListener { _, _, _ -> if (!closed) finished(); true }
        player.prepareAsync()
    }

    override fun start() { if (!closed) player.start() }

    override fun close() {
        if (closed) return
        player.setOnPreparedListener(null)
        player.setOnCompletionListener(null)
        player.setOnErrorListener(null)
        player.release()
        closed = true
    }
}

internal fun speechAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()
