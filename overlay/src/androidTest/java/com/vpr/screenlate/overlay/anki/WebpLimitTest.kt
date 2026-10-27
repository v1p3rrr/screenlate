package com.vpr.screenlate.overlay.anki

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.Test
import org.junit.runner.RunWith

/** The platform limit [webpSize] keeps note pictures under. */
@RunWith(AndroidJUnit4::class)
class WebpLimitTest {
    @Test
    fun theEncoderRefusesALongerSide() {
        assertThat(encodes(WEBP_MAX_SIDE, 1)).isTrue()
        assertThat(encodes(WEBP_MAX_SIDE + 1, 1)).isFalse()
        assertThat(encodes(1, WEBP_MAX_SIDE + 1)).isFalse()
    }

    private fun encodes(width: Int, height: Int): Boolean {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            return bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, ByteArrayOutputStream())
        } finally {
            bitmap.recycle()
        }
    }
}
