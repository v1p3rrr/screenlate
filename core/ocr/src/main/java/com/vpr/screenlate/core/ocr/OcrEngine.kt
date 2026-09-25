package com.vpr.screenlate.core.ocr

import android.graphics.Bitmap
import com.vpr.screenlate.core.common.Language

interface OcrEngine {
    val type: OcrEngineType

    /** Recognizes text on [image]. The result uses the image's pixel coordinates. */
    suspend fun recognize(image: Bitmap, language: Language): OcrPage
}
