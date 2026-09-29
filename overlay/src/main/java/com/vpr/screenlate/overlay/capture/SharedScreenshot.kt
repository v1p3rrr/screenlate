package com.vpr.screenlate.overlay.capture

import android.graphics.Bitmap

/**
 * The screenshot of a scan while several parts use it: the scan with its OCR boost bands, and a note being added
 * (its crop editor draws it). Each user other than the scan takes it with [retain] and gives it back with [release];
 * the bitmap is freed when the last user gives it back. Main thread only.
 */
class SharedScreenshot(val screen: CapturedScreen, private val free: (Bitmap) -> Unit = Bitmap::recycle) {
    private var users = 1

    val bitmap: Bitmap get() = screen.bitmap

    /** Takes the screenshot for one more user, who must [release] it; false when it was freed already. */
    fun retain(): Boolean {
        if (users == 0) return false
        users++
        return true
    }

    fun release() {
        if (users == 0) return
        users--
        if (users == 0) free(screen.bitmap)
    }
}
