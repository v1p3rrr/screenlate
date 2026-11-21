package com.vpr.screenlate.core.anki.audio

import com.vpr.screenlate.core.common.Language
import java.io.File

/** File output is confined to the player's UI dispatcher; preparation callbacks may arrive after close. */
internal interface FileAudioOutput : AutoCloseable {
    fun prepare(file: File, volume: Float, ready: () -> Unit, finished: () -> Unit)
    fun start()
}

/** One engine, with serialized native calls and an acknowledged, bounded stop barrier. */
internal interface SpeechOutput {
    suspend fun prepare(language: Language, canStart: () -> Boolean): Boolean
    suspend fun synthesize(text: String, file: File, canStart: () -> Boolean): Boolean
    suspend fun speak(text: String, volume: Float, canStart: () -> Boolean, finished: () -> Unit): Boolean
    suspend fun stop(): Boolean
}
