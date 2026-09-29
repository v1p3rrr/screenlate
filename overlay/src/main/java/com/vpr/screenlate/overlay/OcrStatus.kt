package com.vpr.screenlate.overlay

import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.overlay.settings.SmallTextMode
import com.vpr.screenlate.overlay.settings.TextSource

/** What the popup's OCR source chip says. */
internal enum class EngineLabel { NONE, LENS, APP_TEXT, DRAFT, DEVICE, DEVICE_OFFLINE, DEVICE_LENS_UNAVAILABLE }

/**
 * The chip for text from [engine]. An on-device result is "unavailable" only when cloud recognition was wanted and
 * failed: not when only the device recognizes by choice or a note withdrew the cloud request ([cloudWithdrawn]).
 */
internal fun engineLabelOf(
    engine: OcrEngineType?,
    final: Boolean,
    engines: OcrEngines,
    offline: Boolean,
    cloudWithdrawn: Boolean,
): EngineLabel = when {
    engine == OcrEngineType.LENS -> EngineLabel.LENS
    engine == OcrEngineType.ACCESSIBILITY -> EngineLabel.APP_TEXT
    engine != OcrEngineType.ML_KIT -> EngineLabel.NONE
    !final -> EngineLabel.DRAFT
    engines == OcrEngines.DEVICE -> EngineLabel.DEVICE
    offline -> EngineLabel.DEVICE_OFFLINE
    cloudWithdrawn -> EngineLabel.DEVICE
    else -> EngineLabel.DEVICE_LENS_UNAVAILABLE
}

/**
 * OCR boost asks cloud recognition, so it is off while only the device recognizes, without recognition, and in a scan
 * whose cloud request a note withdrew.
 */
internal fun ocrBoostMode(
    setting: SmallTextMode,
    engines: OcrEngines,
    textSource: TextSource,
    cloudWithdrawn: Boolean,
): SmallTextMode = when {
    engines == OcrEngines.DEVICE -> SmallTextMode.OFF
    textSource == TextSource.APP_TEXT_ONLY -> SmallTextMode.OFF
    cloudWithdrawn -> SmallTextMode.OFF
    else -> setting
}
