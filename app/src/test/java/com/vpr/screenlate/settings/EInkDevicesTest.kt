package com.vpr.screenlate.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EInkDevicesTest {
    @Test
    fun `e-ink makers are recognized by manufacturer, brand or model`() {
        assertTrue(EInkDevices.isLikely("ONYX", "Onyx", "NoteAir3C"))
        assertTrue(EInkDevices.isLikely("Qualcomm", "BOOX", "Palma"))
        assertTrue(EInkDevices.isLikely("Bigme", "Bigme", "HiBreak Pro"))
        assertTrue(EInkDevices.isLikely("Meebook", "Meebook", "M7"))
        assertTrue(EInkDevices.isLikely("PocketBook", "PocketBook", "PB743K3"))
    }

    @Test
    fun `only the e-ink series of Hisense counts`() {
        assertTrue(EInkDevices.isLikely("Hisense", "Hisense", "A9"))
        assertTrue(EInkDevices.isLikely("Hisense", "Hisense", "Hisense A7 5G"))
        assertFalse(EInkDevices.isLikely("Hisense", "Hisense", "Infinity H50"))
        assertFalse(EInkDevices.isLikely("Hisense", "Hisense", "A50"))
    }

    @Test
    fun `ordinary phones are not offered e-ink mode`() {
        assertFalse(EInkDevices.isLikely("HONOR", "HONOR", "BVL-N49"))
        assertFalse(EInkDevices.isLikely("Google", "google", "Pixel 10 Pro"))
        assertFalse(EInkDevices.isLikely("samsung", "samsung", "SM-S928B"))
    }
}
