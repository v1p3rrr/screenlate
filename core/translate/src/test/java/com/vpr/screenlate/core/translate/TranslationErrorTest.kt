package com.vpr.screenlate.core.translate

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateNotYetValidException
import javax.net.ssl.SSLHandshakeException
import org.junit.Test

class TranslationErrorTest {
    private fun kind(error: Throwable) = TranslationError.of(error).kind

    @Test
    fun `network errors get their kind`() {
        assertThat(kind(UnknownHostException())).isEqualTo(TranslationError.Kind.OFFLINE)
        assertThat(kind(ConnectException())).isEqualTo(TranslationError.Kind.OFFLINE)
        assertThat(kind(SocketTimeoutException())).isEqualTo(TranslationError.Kind.TIMEOUT)
        assertThat(kind(IOException())).isEqualTo(TranslationError.Kind.NETWORK)
        assertThat(kind(IllegalStateException())).isEqualTo(TranslationError.Kind.OTHER)
    }

    @Test
    fun `a certificate the device does not accept is a secure connection error`() {
        val handshake = SSLHandshakeException("x").apply { initCause(CertificateNotYetValidException()) }
        assertThat(kind(handshake)).isEqualTo(TranslationError.Kind.SECURE_CONNECTION)
    }

    @Test
    fun `a service error keeps its kind and code`() {
        val error = TranslationException(TranslationError.Kind.LIMITED, 429)
        assertThat(TranslationError.of(error)).isEqualTo(TranslationError(TranslationError.Kind.LIMITED, 429))
    }
}
