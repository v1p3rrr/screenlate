package com.vpr.screenlate.core.anki.audio

import com.google.common.truth.Truth.assertThat
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException
import org.junit.Test

class AudioErrorTest {
    @Test
    fun `network errors get a kind and keep their type`() {
        assertThat(AudioError.of(ConnectException())).isEqualTo(AudioError(AudioError.Kind.REFUSED, "ConnectException"))
        assertThat(AudioError.of(SocketTimeoutException()).kind).isEqualTo(AudioError.Kind.TIMEOUT)
        assertThat(AudioError.of(UnknownHostException()).kind).isEqualTo(AudioError.Kind.NOT_FOUND)
        assertThat(AudioError.of(SSLHandshakeException("x"))).isEqualTo(AudioError(AudioError.Kind.SECURE_CONNECTION, "SSLHandshakeException"))
    }

    @Test
    fun `http statuses and unknown errors`() {
        assertThat(AudioError.of(HttpStatusException(404))).isEqualTo(AudioError(AudioError.Kind.HTTP_STATUS, "HTTP 404"))
        assertThat(AudioError.of(UnknownServiceException())).isEqualTo(AudioError(AudioError.Kind.OTHER, "UnknownServiceException"))
    }

    @Test
    fun `a list that is not a list names the parse error`() {
        val error = AudioError.of(NotAudioListException(IllegalArgumentException()))
        assertThat(error).isEqualTo(AudioError(AudioError.Kind.NOT_A_LIST, "IllegalArgumentException"))
    }
}
