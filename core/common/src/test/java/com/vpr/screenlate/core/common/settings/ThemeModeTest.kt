package com.vpr.screenlate.core.common.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `system mode follows the system and the others ignore it`() {
        assertThat(ThemeMode.SYSTEM.isDark(systemDark = true)).isTrue()
        assertThat(ThemeMode.SYSTEM.isDark(systemDark = false)).isFalse()
        assertThat(ThemeMode.DARK.isDark(systemDark = false)).isTrue()
        assertThat(ThemeMode.LIGHT.isDark(systemDark = true)).isFalse()
    }
}
