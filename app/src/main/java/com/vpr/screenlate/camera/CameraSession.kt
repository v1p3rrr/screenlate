package com.vpr.screenlate.camera

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.camera.core.ImageCapture
import com.vpr.screenlate.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/** The flash of the next capture; the top bar's icon cycles through the modes in this order. */
enum class FlashMode(
    val cameraX: Int,
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
) {
    OFF(ImageCapture.FLASH_MODE_OFF, R.drawable.ic_flash_off, R.string.camera_flash_off),
    AUTO(ImageCapture.FLASH_MODE_AUTO, R.drawable.ic_flash_auto, R.string.camera_flash_auto),
    ON(ImageCapture.FLASH_MODE_ON, R.drawable.ic_flash_on, R.string.camera_flash_on),
    ;

    val next: FlashMode get() = entries[(ordinal + 1) % entries.size]
}

/**
 * Camera page choices that last while the app process lives and are never stored: the flash is off at every app
 * start, and the page asks for the camera permission by itself once. Used on the main thread.
 */
@Singleton
class CameraSession @Inject constructor() {
    val flash = MutableStateFlow(FlashMode.OFF)

    /** The page asked for the permission on its own; later requests come from its button. */
    var permissionAsked = false

    /** The last request was refused without the system dialog's choice left: the button opens the app's settings. */
    var permissionBlocked = false
}
