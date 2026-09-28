package com.vpr.screenlate.core.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FocusBandTest {

    @Test
    fun `a band holds the aim in its upper part`() {
        val band = FocusBand.next(height = 2000, y = 1000f, done = emptyList())!!
        assertThat(band).isEqualTo(880 until 1180)
    }

    @Test
    fun `bands stay inside the image`() {
        assertThat(FocusBand.next(2000, 10f, emptyList())).isEqualTo(0 until 300)
        assertThat(FocusBand.next(2000, 1990f, emptyList())).isEqualTo(1700 until 2000)
    }

    @Test
    fun `an aim inside a read band needs no other band`() {
        val done = listOf(880 until 1180)
        assertThat(FocusBand.next(2000, 1000f, done)).isNull()
        // Near a cut edge the text may be cut: read around the aim again.
        assertThat(FocusBand.next(2000, 1170f, done)).isNotNull()
        // The image's own edge is not a cut.
        assertThat(FocusBand.next(2000, 5f, listOf(0 until 300))).isNull()
    }

    @Test
    fun `no band without an aim or on a small image`() {
        assertThat(FocusBand.next(2000, null, emptyList())).isNull()
        assertThat(FocusBand.next(1000, 500f, emptyList())).isNull()
    }
}
