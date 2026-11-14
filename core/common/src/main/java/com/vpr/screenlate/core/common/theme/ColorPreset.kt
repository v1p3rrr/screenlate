package com.vpr.screenlate.core.common.theme

/** Supported preset palettes with curated seed colors and harmonious accents. */
enum class ColorPreset(val id: String, val seedColor: Int) {
    VIOLET("violet", 0xFF7C5CFF.toInt()),
    OCEAN("ocean", 0xFF0284C7.toInt()),
    EMERALD("emerald", 0xFF059669.toInt()),
    AMBER("amber", 0xFFD97706.toInt()),
    CORAL("coral", 0xFFE11D48.toInt()),
    SUNSET("sunset", 0xFFEA580C.toInt()),
    LAVENDER("lavender", 0xFF9333EA.toInt()),
    TEAL("teal", 0xFF0097A7.toInt()),
    COBALT("cobalt", 0xFF4169E1.toInt()),
    GRAPHITE("graphite", 0xFF475569.toInt());

    val primarySeed: Int get() = seedColor

    companion object {
        fun fromId(id: String?): ColorPreset? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
