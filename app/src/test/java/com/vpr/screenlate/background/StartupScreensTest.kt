package com.vpr.screenlate.background

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupScreensTest {
    @Test
    fun `takes the first screen present on the phone`() {
        val present = setOf("com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity")
        val found = StartupScreens.find { it.className in present }
        assertEquals("com.hihonor.systemmanager", found?.packageName)
    }

    @Test
    fun `prefers the current screen of a maker to its older one`() {
        val found = StartupScreens.find { it.packageName == "com.huawei.systemmanager" }
        assertEquals("com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity", found?.className)
    }

    @Test
    fun `finds nothing on a phone without such screens`() {
        assertNull(StartupScreens.find { false })
    }

    @Test
    fun `lists every package in the manifest queries`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val missing = StartupScreens.packages.filterNot { "<package android:name=\"$it\" />" in manifest }
        assertTrue("Missing from <queries>: $missing", missing.isEmpty())
    }

    @Test
    fun `has no duplicates`() {
        assertEquals(StartupScreens.known.size, StartupScreens.known.toSet().size)
    }
}
