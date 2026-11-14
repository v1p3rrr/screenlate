package com.vpr.screenlate.core.common

/**
 * Base64 encoder and decoder compatible with Android 7.0 (API 24) where [java.util.Base64] is missing,
 * and with JVM unit tests where [android.util.Base64] is not mocked.
 */
object Base64Compat {
    private val isAndroid: Boolean =
        System.getProperty("java.vm.name")?.contains("Dalvik", ignoreCase = true) == true

    fun encodeToString(bytes: ByteArray): String =
        if (isAndroid) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } else {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }

    fun decode(string: String): ByteArray =
        if (isAndroid) {
            android.util.Base64.decode(string, android.util.Base64.DEFAULT)
        } else {
            java.util.Base64.getDecoder().decode(string)
        }

    fun decodeMime(bytes: ByteArray): ByteArray =
        if (isAndroid) {
            android.util.Base64.decode(bytes, android.util.Base64.DEFAULT)
        } else {
            java.util.Base64.getMimeDecoder().decode(bytes)
        }

    fun decodeMime(string: String): ByteArray =
        if (isAndroid) {
            android.util.Base64.decode(string, android.util.Base64.DEFAULT)
        } else {
            java.util.Base64.getMimeDecoder().decode(string)
        }
}
