package com.vpr.screenlate.overlay.ui

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import org.junit.Test

class CropFocusTest {
    private val image = Box(0f, 100f, 1000f, 2100f)

    @Test
    fun `frames the paragraph with padding`() {
        val lines = listOf(Box(200f, 500f, 800f, 540f), Box(200f, 550f, 600f, 590f))

        assertThat(CropFocus.of(lines, 48f, image)).isEqualTo(Box(152f, 452f, 848f, 638f))
    }

    @Test
    fun `ignores lines without a size`() {
        val lines = listOf(Box(0f, 0f, 0f, 0f), Box(200f, 500f, 800f, 540f))

        assertThat(CropFocus.of(lines, 48f, image)).isEqualTo(Box(152f, 452f, 848f, 588f))
    }

    @Test
    fun `clips to the screenshot`() {
        val lines = listOf(Box(10f, 110f, 990f, 150f))

        assertThat(CropFocus.of(lines, 48f, image)).isEqualTo(Box(0f, 100f, 1000f, 198f))
    }

    @Test
    fun `no frame without usable lines or outside the screenshot`() {
        assertThat(CropFocus.of(listOf(Box(0f, 0f, 0f, 0f)), 48f, image)).isNull()
        assertThat(CropFocus.of(listOf(Box(100f, 2500f, 300f, 2600f)), 48f, image)).isNull()
    }
}
