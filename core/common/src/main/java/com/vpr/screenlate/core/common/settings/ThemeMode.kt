package com.vpr.screenlate.core.common.settings

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Whether this mode shows the dark theme; [systemDark] is the system's night mode, which [ThemeMode.SYSTEM] follows. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
