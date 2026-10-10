package com.vpr.screenlate.core.ocr

import android.graphics.Bitmap
import com.vpr.screenlate.core.common.Language

interface OcrEngine {
    val type: OcrEngineType

    /** Whether this engine can read [language]'s script; [recognize] throws [NoDeviceOcrException] otherwise. */
    fun reads(language: Language): Boolean = true

    /**
     * Recognizes text on [image]. The result uses the image's pixel coordinates. Returns or throws, also when
     * cancelled, only once the engine no longer reads [image], so the caller may recycle it right after.
     */
    suspend fun recognize(image: Bitmap, language: Language): OcrPage

    /** Frees models or caches that the next [recognize] can load again; called when the engine is turned off. */
    fun release() = Unit
}

/** No on-device engine reads the language: ML Kit has no model for its script and no downloaded model does. */
class NoDeviceOcrException : IllegalStateException("No on-device recognition for this language")
