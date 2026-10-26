package com.vpr.screenlate.camera

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.PhotoTiles
import com.vpr.screenlate.core.photos.PhotoAttempt
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoWork
import com.vpr.screenlate.overlay.install.InstallationStatus
import org.junit.Test

class CameraPhotoControlsTest {
    @Test
    fun pendingChangeNeverRewritesTheActualRecognizedLanguage() {
        val document = photo()
        val state = CameraRecognitionState(Language.ENGLISH, listOf(Language.ENGLISH, Language.JAPANESE),
            PhotoOcrSettings(OcrEngines.CLOUD), InstallationStatus(Language.ENGLISH, emptySet(), true, false, emptyList(), 0), null)
        assertThat(state.pending(document)).isTrue()
        assertThat(state.copy(language = Language.JAPANESE).pending(document)).isFalse()
        assertThat(state.copy(language = Language.JAPANESE, settings = PhotoOcrSettings(OcrEngines.DEVICE, "mlkit")).pending(document)).isTrue()
        assertThat((document.text as com.vpr.screenlate.core.photos.PhotoText.Recognized).recognition.language).isEqualTo(Language.JAPANESE)
    }

    @Test
    fun incompleteWarningIsTerminalRatherThanAPartialProgressWarning() {
        val original = photo()
        val attempt = PhotoAttempt("one", original.revision, Language.JAPANESE, PhotoOcrSettings(OcrEngines.CLOUD),
            PhotoTiles.grid(original.width, original.height), startedAt = 0)
        val document = original.copy(attempt = attempt)
        assertThat(cameraHasWarning(document, PhotoWork.Running(original.id, "one", 0, attempt.tiles.size))).isFalse()
        assertThat(cameraHasWarning(document, null)).isTrue()
        assertThat(cameraHasWarning(original, null)).isFalse()
        assertThat(cameraHasWarning(original.copy(attemptUnreadable = true), null)).isTrue()
    }

    @Test
    fun actionsOnlyOverflowWhenMeasuredWidthIsInsufficient() {
        assertThat(cameraActionSlots(432f, 100f, 1f)).isEqualTo(6)
        assertThat(cameraActionSlots(320f, 100f, 1f)).isEqualTo(3)
        assertThat(cameraActionSlots(320f, 280f, 1f)).isEqualTo(3)
        assertThat(cameraActionSlots(640f, 200f, 2f)).isEqualTo(3)
    }
}
