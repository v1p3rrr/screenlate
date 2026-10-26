package com.vpr.screenlate.core.photos.store

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.ocr.PhotoTile
import com.vpr.screenlate.core.photos.FailureCause
import com.vpr.screenlate.core.photos.PhotoAttempt
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoTileResult
import com.vpr.screenlate.core.photos.TestPhotos
import com.vpr.screenlate.core.ocr.OcrEngines
import org.junit.Test

class AttemptCodecTest {
    private val tiles = listOf(PhotoTile(0, 0, 1500, 1500), PhotoTile(1300, 0, 2800, 1500), PhotoTile(0, 1300, 1500, 2800))
    private val attempt = PhotoAttempt("attempt", 4, Language.JAPANESE, PhotoOcrSettings(OcrEngines.BOTH, "mlkit"), tiles,
        listOf(PhotoTileResult(tiles[0], page = TestPhotos.recognition().page), PhotoTileResult(tiles[1], failure = FailureCause.NETWORK)), 42)

    @Test
    fun completedAndPendingTileProgressSurvives() {
        val restored = AttemptCodec.decode(AttemptCodec.encode(attempt))!!
        assertThat(restored).isEqualTo(attempt)
        assertThat(restored.remaining).containsExactly(tiles[1], tiles[2]).inOrder()
        assertThat(restored.finished).isFalse()
    }

    @Test
    fun damageNeverBecomesUsableProgress() {
        val encoded = AttemptCodec.encode(attempt)
        for (i in listOf(0, 10, encoded.size / 2, encoded.lastIndex)) {
            val damaged = encoded.copyOf().also { it[i] = (it[i] + 1).toByte() }
            assertThat(AttemptCodec.decode(damaged)).isNull()
        }
        assertThat(AttemptCodec.decode(encoded.copyOf(12))).isNull()
    }
}
