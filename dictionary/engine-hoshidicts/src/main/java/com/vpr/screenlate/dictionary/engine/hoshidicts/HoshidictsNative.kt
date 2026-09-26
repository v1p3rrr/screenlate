package com.vpr.screenlate.dictionary.engine.hoshidicts

/**
 * JNI surface of `libscreenlate_hoshidicts.so` (see `src/main/cpp/jni_bridge.cpp`).
 *
 * Strings are passed as UTF-8 byte arrays and structured results come back as UTF-8 JSON. Calls that take a
 * session handle must not run concurrently with each other.
 */
internal object HoshidictsNative {
    init {
        System.loadLibrary("screenlate_hoshidicts")
    }

    external fun create(): Long

    external fun destroy(handle: Long)

    external fun importDictionary(zipPath: ByteArray, outputDir: ByteArray, lowRam: Boolean): ByteArray

    external fun load(
        handle: Long,
        terms: Array<ByteArray>,
        frequencies: Array<ByteArray>,
        pitches: Array<ByteArray>,
        kanji: Array<ByteArray>,
    )

    external fun lookup(
        handle: Long,
        text: ByteArray,
        maxResults: Int,
        scanLength: Int,
        frequencyDictionary: ByteArray?,
        frequencyOrder: Int,
        primaryReading: ByteArray?,
    ): ByteArray

    external fun styles(handle: Long): ByteArray

    external fun media(handle: Long, dictionary: ByteArray, path: ByteArray): ByteArray?

    external fun kanji(handle: Long, character: ByteArray): ByteArray
}
