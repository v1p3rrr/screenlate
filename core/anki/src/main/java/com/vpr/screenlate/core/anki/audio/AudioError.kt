package com.vpr.screenlate.core.anki.audio

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Why an audio source request failed, without URLs or terms; [type] is the error type or the HTTP status. */
data class AudioError(val kind: Kind, val type: String) {
    enum class Kind { HTTP_STATUS, NOT_FOUND, REFUSED, TIMEOUT, SECURE_CONNECTION, NOT_A_LIST, SOURCE_LIST, OTHER }

    companion object {
        fun of(error: Throwable): AudioError {
            if (error is HttpStatusException) return AudioError(Kind.HTTP_STATUS, "HTTP ${error.code}")
            val kind = when (error) {
                is UnknownHostException -> Kind.NOT_FOUND
                is ConnectException -> Kind.REFUSED
                is InterruptedIOException -> Kind.TIMEOUT
                is SSLException -> Kind.SECURE_CONNECTION
                is NotAudioListException -> Kind.NOT_A_LIST
                is SourceListException -> Kind.SOURCE_LIST
                else -> Kind.OTHER
            }
            return AudioError(kind, (error.cause.takeIf { error is NotAudioListException } ?: error).javaClass.simpleName)
        }
    }
}

internal class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** The answer of a custom JSON source is not an audio source list. */
internal class NotAudioListException(cause: Throwable) : IOException(cause)

/** A custom URL source answered with an audio source list instead of a clip: it is a "Custom URL (JSON)" source. */
internal class SourceListException : IOException("An audio source list instead of a clip")
