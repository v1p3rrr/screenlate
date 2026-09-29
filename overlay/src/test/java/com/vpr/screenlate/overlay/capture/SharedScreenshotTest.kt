package com.vpr.screenlate.overlay.capture

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SharedScreenshotTest {
    private val freed = mutableListOf<Bitmap>()

    /** Never drawn or read here; the Android stub cannot be constructed normally. */
    private val bitmap: Bitmap = unsafe().allocateInstance(Bitmap::class.java) as Bitmap
    private val shot = SharedScreenshot(CapturedScreen(bitmap, 0, 0), free = { freed += it })

    private fun unsafe(): sun.misc.Unsafe =
        sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe

    @Test
    fun `the scan alone frees it on release`() {
        shot.release()

        assertThat(freed).containsExactly(bitmap)
    }

    @Test
    fun `a note keeps it after the scan ends`() {
        assertThat(shot.retain()).isTrue()
        shot.release()
        assertThat(freed).isEmpty()

        shot.release()
        assertThat(freed).containsExactly(bitmap)
    }

    @Test
    fun `a freed screenshot cannot be taken or freed again`() {
        shot.release()

        assertThat(shot.retain()).isFalse()
        shot.release()
        assertThat(freed).hasSize(1)
    }
}
