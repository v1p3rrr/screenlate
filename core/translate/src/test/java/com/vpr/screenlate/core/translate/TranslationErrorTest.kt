package com.vpr.screenlate.core.translate

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateExpiredException
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
    fun `a certificate not valid at the device's date points to the clock`() {
        val early = SSLHandshakeException("x").apply {
            initCause(CertPathValidatorException("y", CertificateNotYetValidException()))
        }
        assertThat(TranslationError.of(early))
            .isEqualTo(TranslationError(TranslationError.Kind.CERTIFICATE_DATE, detail = "CertificateNotYetValidException"))
        val late = SSLHandshakeException("x").apply { initCause(CertificateExpiredException()) }
        assertThat(kind(late)).isEqualTo(TranslationError.Kind.CERTIFICATE_DATE)
    }

    @Test
    fun `another certificate or TLS failure is a secure connection error`() {
        val handshake = SSLHandshakeException("x").apply { initCause(CertPathValidatorException("not trusted")) }
        assertThat(kind(handshake)).isEqualTo(TranslationError.Kind.SECURE_CONNECTION)
    }

    @Test
    fun `the log line names the kind, the status and the detail`() {
        assertThat(TranslationError(TranslationError.Kind.HTTP_STATUS, 503).toString()).isEqualTo("HTTP_STATUS 503")
        assertThat(TranslationError(TranslationError.Kind.REJECTED, detail = "status 205").toString()).isEqualTo("REJECTED (status 205)")
    }

    @Test
    fun `a service error keeps its kind and code`() {
        val error = TranslationException(TranslationError.Kind.LIMITED, 429)
        assertThat(TranslationError.of(error)).isEqualTo(TranslationError(TranslationError.Kind.LIMITED, 429))
    }
}
