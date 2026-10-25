package com.vpr.screenlate.core.photos

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord

object TestPhotos {
    /**
     * A page with what a stored recognition must keep: vertical Japanese with character boxes, a right-to-left
     * Arabic line, combining marks, separators and a paragraph from another engine.
     */
    fun page(): OcrPage = OcrPage(
        width = 4000,
        height = 3000,
        engine = OcrEngineType.LENS,
        paragraphs = listOf(
            OcrParagraph(
                lines = listOf(
                    OcrLine(
                        words = listOf(
                            OcrWord(
                                text = "日本語",
                                separator = "",
                                box = Box(3800f, 100f, 3860f, 280f),
                                characterBoxes = listOf(
                                    Box(3800f, 100f, 3860f, 160f),
                                    Box(3800f, 160f, 3860f, 220f),
                                    Box(3800f, 220f, 3860f, 280f),
                                ),
                            ),
                        ),
                        box = Box(3800f, 100f, 3860f, 280f),
                        vertical = true,
                    ),
                ),
            ),
            OcrParagraph(
                lines = listOf(
                    OcrLine(
                        words = listOf(
                            OcrWord("مرحبا", " ", Box(900f, 1000f, 1100f, 1060f)),
                            OcrWord("بالعالم", "", Box(600f, 1000f, 880f, 1060f)),
                        ),
                        box = Box(600f, 1000f, 1100f, 1060f),
                        vertical = false,
                        rightToLeft = true,
                    ),
                    OcrLine(
                        words = listOf(
                            OcrWord("Tiếng", " ", Box(100f, 1100f, 260f, 1160f)),
                            OcrWord("Việt", "\n", Box(280f, 1100f, 400f, 1160f)),
                        ),
                        box = Box(100f, 1100f, 400f, 1160f),
                        vertical = false,
                    ),
                ),
                engine = OcrEngineType.ML_KIT,
            ),
        ),
    )

    fun recognition(
        language: Language = Language.JAPANESE,
        failed: List<FailedRegion> = emptyList(),
    ): PhotoRecognition = PhotoRecognition(
        language = language,
        settings = PhotoOcrSettings(OcrEngines.BOTH, deviceModel = "mlkit"),
        page = page(),
        failed = failed,
        recognizedAt = 1_700_000_000_000L,
    )
}
