package com.vpr.screenlate.core.common.theme

/** Configuration of the active color scheme. */
data class AppPaletteConfig(
    val useSystemWallpaper: Boolean = false, // Android 12+ Monet
    val preset: ColorPreset? = ColorPreset.VIOLET,
    val customSeedColor: Int? = null,
) {
    val effectiveSeed: Int
        get() = customSeedColor ?: preset?.seedColor ?: ColorPreset.VIOLET.seedColor
}
