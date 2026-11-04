package com.vpr.screenlate

import android.app.KeyguardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vpr.screenlate.camera.PhotoTestAccess
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class StartupUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun firstRunShowsLocalizedLanguagesAndScrolls(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName == "com.vpr.screenlate.verification")
        assumeTrue(context.resources.configuration.locales[0].language == "ru")
        assumeTrue("Unlocked screen required", !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val access = EntryPointAccessors.fromApplication(context.applicationContext, PhotoTestAccess::class.java)
        val preferences = access.commonPreferences()
        val original = preferences.data.first()[LanguageProfiles.FIRST_RUN]
        preferences.edit { it[LanguageProfiles.FIRST_RUN] = true }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10_000) {
                    runCatching { compose.onAllNodesWithText("Кантонский").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
                }
                compose.onNodeWithText("Кантонский").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Японский").performScrollTo().assertIsDisplayed()
            }
        } finally {
            preferences.edit {
                if (original == null) it.remove(LanguageProfiles.FIRST_RUN) else it[LanguageProfiles.FIRST_RUN] = original
            }
        }
    }
}
