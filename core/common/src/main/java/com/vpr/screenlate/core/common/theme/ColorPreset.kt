package com.vpr.screenlate.core.common.theme

/** Supported preset palettes with curated seed colors and harmonious accents. */
enum class ColorPreset(val id: String, val seedColor: Int, val accentShift: Float = 55f) {
    VIOLET("violet", 0xFF7C5CFF.toInt(), accentShift = 55f),     // Classic Screenlate violet + pink
    OCEAN("ocean", 0xFF0284C7.toInt(), accentShift = 50f),       // Sky blue + cyan/amber
    EMERALD("emerald", 0xFF059669.toInt(), accentShift = 45f),   // Deep emerald + lime gold
    AMBER("amber", 0xFFD97706.toInt(), accentShift = 60f),       // Golden amber + warm bronze
    CORAL("coral", 0xFFE11D48.toInt(), accentShift = 45f),       // Vivid coral red + peach
    SUNSET("sunset", 0xFFEA580C.toInt(), accentShift = 50f),     // Vibrant orange + magenta
    LAVENDER("lavender", 0xFF8B5CF6.toInt(), accentShift = 45f), // Soft purple + berry
    TEAL("teal", 0xFF0D9488.toInt(), accentShift = 50f);        // Teal + coral

    val primarySeed: Int get() = seedColor

    companion object {
        fun fromId(id: String?): ColorPreset? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
