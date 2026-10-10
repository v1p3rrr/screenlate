package com.vpr.screenlate.overlay

import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.overlay.settings.SmallTextMode
import com.vpr.screenlate.overlay.settings.TextSource

/** What the popup's OCR source chip says. */
internal enum class EngineLabel { NONE, LENS, APP_TEXT, DRAFT, DEVICE, DEVICE_OFFLINE, DEVICE_LENS_UNAVAILABLE }

/**
 * The chip for text from [engine]. An on-device result is "unavailable" only when cloud recognition was wanted and
 * failed, not when only the device recognizes by choice. [keptBesideCloud]: the word was read by the device and kept
 * because the cloud's final text has none under the aim; the cloud worked, so it is just "on device".
 */
internal fun engineLabelOf(
    engine: OcrEngineType?,
    final: Boolean,
    engines: OcrEngines,
    offline: Boolean,
    keptBesideCloud: Boolean = false,
): EngineLabel = when {
    engine == OcrEngineType.LENS -> EngineLabel.LENS
    engine == OcrEngineType.ACCESSIBILITY -> EngineLabel.APP_TEXT
    engine != OcrEngineType.ML_KIT && engine != OcrEngineType.DEVICE_MODEL -> EngineLabel.NONE
    !final -> EngineLabel.DRAFT
    keptBesideCloud -> EngineLabel.DEVICE
    engines == OcrEngines.DEVICE -> EngineLabel.DEVICE
    offline -> EngineLabel.DEVICE_OFFLINE
    else -> EngineLabel.DEVICE_LENS_UNAVAILABLE
}

/** OCR boost asks cloud recognition, so it is off while only the device recognizes and without recognition. */
internal fun ocrBoostMode(setting: SmallTextMode, engines: OcrEngines, textSource: TextSource): SmallTextMode = when {
    engines == OcrEngines.DEVICE -> SmallTextMode.OFF
    textSource == TextSource.APP_TEXT_ONLY -> SmallTextMode.OFF
    else -> setting
}

/**
 * ➕ waits while the scan still refines its text ([pending]), so a note takes the final text where it has the word. A
 * word read from the app's own text ([engine]) is exact already.
 */
internal fun noteWaitsForText(pending: Boolean, engine: OcrEngineType?): Boolean =
    pending && engine != OcrEngineType.ACCESSIBILITY
