package com.vpr.screenlate.overlay

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.overlay.settings.SmallTextMode
import com.vpr.screenlate.overlay.settings.TextSource
import org.junit.Test

class OcrStatusTest {
    private fun device(
        final: Boolean = true,
        engines: OcrEngines = OcrEngines.BOTH,
        offline: Boolean = false,
    ) = engineLabelOf(OcrEngineType.ML_KIT, final, engines, offline)

    @Test
    fun `other engines name themselves`() {
        assertThat(engineLabelOf(OcrEngineType.LENS, true, OcrEngines.BOTH, false)).isEqualTo(EngineLabel.LENS)
        assertThat(engineLabelOf(OcrEngineType.ACCESSIBILITY, false, OcrEngines.BOTH, false))
            .isEqualTo(EngineLabel.APP_TEXT)
        assertThat(engineLabelOf(null, true, OcrEngines.BOTH, false)).isEqualTo(EngineLabel.NONE)
    }

    @Test
    fun `a device result before the final one is a draft`() {
        assertThat(device(final = false)).isEqualTo(EngineLabel.DRAFT)
    }

    @Test
    fun `a failed cloud request makes the device result a fallback`() {
        assertThat(device()).isEqualTo(EngineLabel.DEVICE_LENS_UNAVAILABLE)
        assertThat(device(offline = true)).isEqualTo(EngineLabel.DEVICE_OFFLINE)
    }

    @Test
    fun `a device word kept beside the cloud's final text is plain`() {
        assertThat(engineLabelOf(OcrEngineType.ML_KIT, true, OcrEngines.BOTH, false, keptBesideCloud = true))
            .isEqualTo(EngineLabel.DEVICE)
        assertThat(engineLabelOf(OcrEngineType.ACCESSIBILITY, true, OcrEngines.BOTH, false, keptBesideCloud = true))
            .isEqualTo(EngineLabel.APP_TEXT)
    }

    @Test
    fun `a device result by choice is plain`() {
        assertThat(device(engines = OcrEngines.DEVICE)).isEqualTo(EngineLabel.DEVICE)
        assertThat(device(engines = OcrEngines.DEVICE, offline = true)).isEqualTo(EngineLabel.DEVICE)
    }

    @Test
    fun `ocr boost follows the setting while the cloud is asked`() {
        assertThat(ocrBoostMode(SmallTextMode.ON_DEMAND, OcrEngines.BOTH, TextSource.APP_TEXT))
            .isEqualTo(SmallTextMode.ON_DEMAND)
        assertThat(ocrBoostMode(SmallTextMode.ALWAYS, OcrEngines.CLOUD, TextSource.SCREEN)).isEqualTo(SmallTextMode.ALWAYS)
    }

    @Test
    fun `ocr boost is off without the cloud`() {
        assertThat(ocrBoostMode(SmallTextMode.ALWAYS, OcrEngines.DEVICE, TextSource.SCREEN)).isEqualTo(SmallTextMode.OFF)
        assertThat(ocrBoostMode(SmallTextMode.ALWAYS, OcrEngines.BOTH, TextSource.APP_TEXT_ONLY))
            .isEqualTo(SmallTextMode.OFF)
    }

    @Test
    fun `a note waits for the final text of a recognized word`() {
        assertThat(noteWaitsForText(pending = true, OcrEngineType.ML_KIT)).isTrue()
        assertThat(noteWaitsForText(pending = true, null)).isTrue()
        assertThat(noteWaitsForText(pending = false, OcrEngineType.ML_KIT)).isFalse()
        assertThat(noteWaitsForText(pending = false, OcrEngineType.LENS)).isFalse()
    }

    @Test
    fun `a word from the app's own text needs no wait`() {
        assertThat(noteWaitsForText(pending = true, OcrEngineType.ACCESSIBILITY)).isFalse()
    }
}
