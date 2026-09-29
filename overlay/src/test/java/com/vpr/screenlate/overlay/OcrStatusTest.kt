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
        cloudWithdrawn: Boolean = false,
    ) = engineLabelOf(OcrEngineType.ML_KIT, final, engines, offline, cloudWithdrawn)

    @Test
    fun `other engines name themselves`() {
        assertThat(engineLabelOf(OcrEngineType.LENS, true, OcrEngines.BOTH, false, false)).isEqualTo(EngineLabel.LENS)
        assertThat(engineLabelOf(OcrEngineType.ACCESSIBILITY, false, OcrEngines.BOTH, false, false))
            .isEqualTo(EngineLabel.APP_TEXT)
        assertThat(engineLabelOf(null, true, OcrEngines.BOTH, false, false)).isEqualTo(EngineLabel.NONE)
    }

    @Test
    fun `a device result before the final one is a draft`() {
        assertThat(device(final = false, cloudWithdrawn = true)).isEqualTo(EngineLabel.DRAFT)
    }

    @Test
    fun `a failed cloud request makes the device result a fallback`() {
        assertThat(device()).isEqualTo(EngineLabel.DEVICE_LENS_UNAVAILABLE)
        assertThat(device(offline = true)).isEqualTo(EngineLabel.DEVICE_OFFLINE)
    }

    @Test
    fun `a device result by choice is plain`() {
        assertThat(device(engines = OcrEngines.DEVICE)).isEqualTo(EngineLabel.DEVICE)
        assertThat(device(engines = OcrEngines.DEVICE, offline = true)).isEqualTo(EngineLabel.DEVICE)
        assertThat(device(cloudWithdrawn = true)).isEqualTo(EngineLabel.DEVICE)
    }

    @Test
    fun `offline wins over a withdrawn request`() {
        assertThat(device(offline = true, cloudWithdrawn = true)).isEqualTo(EngineLabel.DEVICE_OFFLINE)
    }

    @Test
    fun `ocr boost follows the setting while the cloud is asked`() {
        assertThat(ocrBoostMode(SmallTextMode.ON_DEMAND, OcrEngines.BOTH, TextSource.APP_TEXT, false))
            .isEqualTo(SmallTextMode.ON_DEMAND)
        assertThat(ocrBoostMode(SmallTextMode.ALWAYS, OcrEngines.CLOUD, TextSource.SCREEN, false))
            .isEqualTo(SmallTextMode.ALWAYS)
    }

    @Test
    fun `ocr boost is off without the cloud`() {
        assertThat(ocrBoostMode(SmallTextMode.ALWAYS, OcrEngines.DEVICE, TextSource.SCREEN, false)).isEqualTo(SmallTextMode.OFF)
        assertThat(ocrBoostMode(SmallTextMode.ALWAYS, OcrEngines.BOTH, TextSource.APP_TEXT_ONLY, false))
            .isEqualTo(SmallTextMode.OFF)
        assertThat(ocrBoostMode(SmallTextMode.ALWAYS, OcrEngines.BOTH, TextSource.APP_TEXT, true)).isEqualTo(SmallTextMode.OFF)
    }
}
