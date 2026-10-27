package com.vpr.screenlate.camera

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.PhotoTile
import com.vpr.screenlate.core.ocr.PhotoTilePage
import com.vpr.screenlate.core.ocr.PhotoTiles
import com.vpr.screenlate.core.photos.FailedRegion
import com.vpr.screenlate.core.photos.FailureCause
import com.vpr.screenlate.core.photos.PhotoAttempt
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoRecognition
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.PhotoTileResult
import com.vpr.screenlate.core.photos.PhotoWork
import com.vpr.screenlate.dictionary.api.catalog.CatalogCategory
import com.vpr.screenlate.overlay.install.InstallationStatus
import org.junit.Test

class CameraPhotoControlsTest {
    @Test
    fun pendingChangeNeverRewritesTheActualRecognizedLanguage() {
        val document = photo()
        val state = state()
        assertThat(state.pending(document)).isTrue()
        assertThat(state.copy(language = Language.JAPANESE).pending(document)).isFalse()
        assertThat(state.copy(language = Language.JAPANESE, settings = PhotoOcrSettings(OcrEngines.DEVICE, "mlkit")).pending(document)).isTrue()
        assertThat((document.text as PhotoText.Recognized).recognition.language).isEqualTo(Language.JAPANESE)
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
    @Test
    fun aLanguageChangeIsNamedBeforeASettingsChange() {
        val document = photo()
        assertThat(state().pendingReason(document)).isEqualTo(CameraPending.LANGUAGE)
        assertThat(state(Language.JAPANESE).pendingReason(document)).isNull()
        assertThat(state(Language.JAPANESE).copy(settings = PhotoOcrSettings(OcrEngines.DEVICE, "mlkit")).pendingReason(document))
            .isEqualTo(CameraPending.SETTINGS)
        // Nothing recognized and nothing tried: nothing to be out of date.
        assertThat(state().pendingReason(photo(text = PhotoText.None))).isNull()
        val attempt = PhotoAttempt("one", 0, Language.JAPANESE, PhotoOcrSettings(OcrEngines.CLOUD), PhotoTiles.grid(4000, 3000), startedAt = 0)
        assertThat(state().pendingReason(photo(text = PhotoText.None).copy(attempt = attempt))).isEqualTo(CameraPending.LANGUAGE)
    }

    @Test
    fun aPhotoReadWholeIsPendingOnlyWhenTilesWouldReadMore() {
        val whole = PhotoOcrSettings(OcrEngines.CLOUD, tiled = false)
        val wholeText = photo().text as PhotoText.Recognized
        val readWhole = photo(text = PhotoText.Recognized(wholeText.recognition.copy(settings = whole)))
        val accurate = state(Language.JAPANESE)
        val quick = accurate.copy(settings = whole)
        assertThat(accurate.pendingReason(readWhole)).isEqualTo(CameraPending.SETTINGS)
        assertThat(quick.pendingReason(readWhole)).isNull()
        // A photo read by tiles stays as it is when High accuracy is off.
        assertThat(quick.pendingReason(photo())).isNull()
        // A photo of one tile reads the same either way.
        val small = readWhole.copy(rawWidth = 1500, rawHeight = 1000)
        assertThat(accurate.pendingReason(small)).isNull()
        assertThat(accurate.canReadByTiles(readWhole)).isTrue()
        assertThat(quick.canReadByTiles(readWhole)).isTrue()
        assertThat(accurate.canReadByTiles(photo())).isFalse()
        assertThat(accurate.canReadByTiles(small)).isFalse()
        assertThat(accurate.canReadByTiles(photo(text = PhotoText.None))).isFalse()
    }

    @Test
    fun aPhotoReadWholeIsOneRegion() {
        val whole = PhotoOcrSettings(OcrEngines.CLOUD, tiled = false)
        val tile = PhotoTile(0, 0, 4000, 3000)
        val recognition = (photo().text as PhotoText.Recognized).recognition.copy(settings = whole, tiles = listOf(PhotoTilePage(tile, page())))
        val status = cameraStatus(photo(text = PhotoText.Recognized(recognition)), state(Language.JAPANESE)) as CameraStatus.Recognized
        assertThat(status.done to status.total).isEqualTo(1 to 1)
        val failed = PhotoAttempt("one", 1, Language.JAPANESE, whole, listOf(tile),
            results = listOf(PhotoTileResult(tile, failure = FailureCause.NETWORK)), startedAt = 0, finished = true)
        val document = photo(text = PhotoText.None).copy(attempt = failed)
        assertThat(cameraHasWarning(document, null)).isTrue()
        assertThat(cameraMissingRegions(document)).containsExactly(tile.box)
    }

    @Test
    fun theStatusCountsRegionsAndNamesOnlyAnotherLanguage() {
        val tiles = PhotoTiles.grid(4000, 3000)
        val document = photo(text = recognized(tiles.drop(1), failed = listOf(tiles.first())))
        val status = cameraStatus(document, state(Language.JAPANESE)) as CameraStatus.Recognized
        assertThat(status.language).isNull()
        assertThat(status.done).isEqualTo(tiles.size - 1)
        assertThat(status.total).isEqualTo(tiles.size)
        assertThat(status.at).isEqualTo(42)
        assertThat(status.pending).isNull()
        val other = cameraStatus(document, state()) as CameraStatus.Recognized
        assertThat(other.language).isEqualTo(Language.JAPANESE)
        assertThat(other.pending).isEqualTo(CameraPending.LANGUAGE)
        assertThat(cameraStatus(document, state().copy(work = PhotoWork.Running(document.id, "one", 2, 6))))
            .isEqualTo(CameraStatus.Recognizing(2, 6))
        // Another photo's work does not change this one's status.
        assertThat(cameraStatus(document, state().copy(work = PhotoWork.Running(9, "one", 2, 6))))
            .isInstanceOf(CameraStatus.Recognized::class.java)
        assertThat(cameraStatus(photo(text = PhotoText.None), state())).isEqualTo(CameraStatus.Unrecognized(null))
    }

    @Test
    fun missingRegionsComeFromTheUnfinishedAttemptFirst() {
        val tiles = PhotoTiles.grid(4000, 3000)
        val document = photo(text = recognized(tiles.drop(1), failed = listOf(tiles.first())))
        assertThat(cameraMissingRegions(document)).containsExactly(tiles.first().box)
        val attempt = PhotoAttempt("one", 1, Language.JAPANESE, PhotoOcrSettings(OcrEngines.CLOUD), tiles,
            results = tiles.take(tiles.size - 1).map { PhotoTileResult(it, page = page()) }, startedAt = 0)
        assertThat(cameraMissingRegions(document.copy(attempt = attempt))).containsExactly(tiles.last().box)
        assertThat(cameraMissingRegions(photo())).isEmpty()
    }

    @Test
    fun theOverviewPassIsNotARegion() {
        val tiles = PhotoTiles.grid(4000, 3000)
        val overview = PhotoTiles.overview(4000, 3000)!!
        val document = photo(text = recognized(listOf(overview) + tiles.drop(1), failed = listOf(tiles.first())))
        val status = cameraStatus(document, state(Language.JAPANESE)) as CameraStatus.Recognized
        assertThat(status.done to status.total).isEqualTo(tiles.size - 1 to tiles.size)
        // A finished attempt that misses only the overview pass misses no region.
        val attempt = PhotoAttempt("one", 1, Language.JAPANESE, PhotoOcrSettings(OcrEngines.CLOUD), listOf(overview) + tiles,
            results = tiles.map { PhotoTileResult(it, page = page()) } + PhotoTileResult(overview, failure = FailureCause.NETWORK),
            startedAt = 0, finished = true)
        val finished = photo(text = PhotoText.None).copy(attempt = attempt)
        assertThat(cameraHasWarning(finished, null)).isFalse()
        assertThat(cameraMissingRegions(finished)).isEmpty()
    }

    @Test
    fun aMissingDictionaryIsNamedBeforeAMissingModel() {
        fun status(missing: Set<CatalogCategory>, requiresDevice: Boolean = false, deviceReady: Boolean = false) =
            InstallationStatus(Language.JAPANESE, missing, deviceReady, requiresDevice, emptyList(), 0)
        assertThat(status(emptySet()).gap()).isNull()
        assertThat(status(emptySet(), requiresDevice = true, deviceReady = true).gap()).isNull()
        assertThat(status(emptySet(), requiresDevice = true).gap()).isEqualTo(LanguageGap.MODEL)
        assertThat(status(setOf(CatalogCategory.OCR_MODEL)).gap()).isEqualTo(LanguageGap.MODEL)
        val dictionary = CatalogCategory.entries.first { it != CatalogCategory.OCR_MODEL }
        assertThat(status(setOf(dictionary, CatalogCategory.OCR_MODEL), requiresDevice = true).gap()).isEqualTo(LanguageGap.DICTIONARY)
    }

    @Test
    fun theFillShrinksInHalvesThenToItsSide() {
        assertThat(fillSteps(320, 240)).containsExactly(160 to 120, 80 to 60, 24 to 18).inOrder()
        assertThat(fillSteps(240, 320)).containsExactly(120 to 160, 60 to 80, 18 to 24).inOrder()
        assertThat(fillSteps(40, 30)).containsExactly(24 to 18)
    }

    private fun state(language: Language = Language.ENGLISH) = CameraRecognitionState(language, listOf(Language.ENGLISH, Language.JAPANESE),
        PhotoOcrSettings(OcrEngines.CLOUD), InstallationStatus(language, emptySet(), true, false, emptyList(), 0), null)

    private fun page() = OcrPage(1500, 1500, emptyList(), OcrEngineType.LENS)

    private fun recognized(tiles: List<PhotoTile>, failed: List<PhotoTile>) = PhotoText.Recognized(
        PhotoRecognition(
            Language.JAPANESE, PhotoOcrSettings(OcrEngines.CLOUD), OcrPage(4000, 3000, emptyList(), OcrEngineType.LENS),
            failed = failed.map { FailedRegion(it.box, FailureCause.NETWORK) }, recognizedAt = 42,
            tiles = tiles.map { PhotoTilePage(it, page()) },
        ),
    )
}
