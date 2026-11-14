package com.vpr.screenlate.overlay.ui

import android.content.res.Resources
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import sun.misc.Unsafe

class WindowMetricsCompatTest {

    private fun unsafe(): Unsafe =
        Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe

    @Test
    fun `null resources returns 0 for dimension`() {
        assertThat(WindowMetricsCompat.resourceDimension(null, "status_bar_height")).isEqualTo(0)
    }

    @Test
    fun `resource dimension returns 0 when identifier is 0`() {
        val resources = unsafe().allocateInstance(Resources::class.java) as Resources
        assertThat(WindowMetricsCompat.resourceDimension(resources, "status_bar_height")).isEqualTo(0)
        assertThat(WindowMetricsCompat.resourceDimension(resources, "navigation_bar_height")).isEqualTo(0)
    }
}
