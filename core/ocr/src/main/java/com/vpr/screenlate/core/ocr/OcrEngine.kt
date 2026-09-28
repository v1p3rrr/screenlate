package com.vpr.screenlate.core.ocr

import android.graphics.Bitmap
import com.vpr.screenlate.core.common.Language

interface OcrEngine {
    val type: OcrEngineType

    /**
     * Recognizes text on [image]. The result uses the image's pixel coordinates. Returns or throws, also when
     * cancelled, only once the engine no longer reads [image], so the caller may recycle it right after.
     */
    suspend fun recognize(image: Bitmap, language: Language): OcrPage

    /** Frees models or caches that the next [recognize] can load again; called when the engine is turned off. */
    fun release() = Unit
}
