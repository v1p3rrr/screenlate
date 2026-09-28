package com.vpr.screenlate.core.ocr

/** The recognizers a scan uses. */
enum class OcrEngines {
    /** Lens, with ML Kit for the draft and as the fallback. */
    BOTH,

    /** Lens only: no draft, and no result without network. */
    CLOUD,

    /** ML Kit only. */
    DEVICE,
}

/**
 * @property deferWholeImage with both engines, ML Kit reads the bands around the aim at once but the whole image only
 *   when Lens is late or fails; when Lens answers in time, that work is saved.
 */
data class OcrOptions(
    val engines: OcrEngines = OcrEngines.BOTH,
    val deferWholeImage: Boolean = false,
)
