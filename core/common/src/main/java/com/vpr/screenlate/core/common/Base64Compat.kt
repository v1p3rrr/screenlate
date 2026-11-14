package com.vpr.screenlate.core.common

/**
 * Base64 encoder and decoder compatible with Android 7.0 (API 24) where [java.util.Base64] is missing,
 * and with JVM unit tests where [android.util.Base64] is not mocked.
 */
object Base64Compat {
    private interface Backend {
        fun encodeToString(bytes: ByteArray): String
        fun decode(string: String): ByteArray
        fun decodeMime(bytes: ByteArray): ByteArray
        fun decodeMime(string: String): ByteArray
    }

    private val isAndroid: Boolean =
        System.getProperty("java.vm.name")?.contains("Dalvik", ignoreCase = true) == true

    private val backend: Backend = if (isAndroid) AndroidBackend else JvmBackend

    fun encodeToString(bytes: ByteArray): String = backend.encodeToString(bytes)

    fun decode(string: String): ByteArray = backend.decode(string)

    fun decodeMime(bytes: ByteArray): ByteArray = backend.decodeMime(bytes)

    fun decodeMime(string: String): ByteArray = backend.decodeMime(string)

    private object AndroidBackend : Backend {
        override fun encodeToString(bytes: ByteArray): String =
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        override fun decode(string: String): ByteArray =
            android.util.Base64.decode(string, android.util.Base64.DEFAULT)

        override fun decodeMime(bytes: ByteArray): ByteArray =
            android.util.Base64.decode(bytes, android.util.Base64.DEFAULT)

        override fun decodeMime(string: String): ByteArray =
            android.util.Base64.decode(string, android.util.Base64.DEFAULT)
    }

    private object JvmBackend : Backend {
        override fun encodeToString(bytes: ByteArray): String =
            java.util.Base64.getEncoder().encodeToString(bytes)

        override fun decode(string: String): ByteArray =
            java.util.Base64.getDecoder().decode(string)

        override fun decodeMime(bytes: ByteArray): ByteArray =
            java.util.Base64.getMimeDecoder().decode(bytes)

        override fun decodeMime(string: String): ByteArray =
            java.util.Base64.getMimeDecoder().decode(string)
    }
}
