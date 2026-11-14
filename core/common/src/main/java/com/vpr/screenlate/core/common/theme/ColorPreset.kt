package com.vpr.screenlate.core.common.theme

/** Supported preset palettes with curated seed colors and harmonious accents. */
enum class ColorPreset(val id: String, val seedColor: Int) {
    VIOLET("violet", 0xFF7C5CFF.toInt()),
    OCEAN("ocean", 0xFF0284C7.toInt()),
    EMERALD("emerald", 0xFF059669.toInt()),
    AMBER("amber", 0xFFD97706.toInt()),
    CORAL("coral", 0xFFE11D48.toInt()),
    SUNSET("sunset", 0xFFEA580C.toInt()),
    LAVENDER("lavender", 0xFF8B5CF6.toInt()),
    TEAL("teal", 0xFF0D9488.toInt());

    val primarySeed: Int get() = seedColor

    companion object {
        fun fromId(id: String?): ColorPreset? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
