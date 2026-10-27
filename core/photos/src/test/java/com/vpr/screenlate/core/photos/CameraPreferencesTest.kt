package com.vpr.screenlate.core.photos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CameraPreferencesTest {
    @Test
    fun firstUseRemembersActiveLanguageAndThenStaysIndependent() = runTest {
        val appStore = MemoryStore()
        val profiles = LanguageProfiles(appStore)
        profiles.turnOn(Language.ENGLISH)
        val cameraStore = MemoryStore()
        val camera = CameraPreferences(cameraStore, profiles)
        assertThat(camera.languages.first().selected).isEqualTo(Language.ENGLISH)
        profiles.setActive(Language.JAPANESE)
        assertThat(camera.languages.first().selected).isEqualTo(Language.ENGLISH)
        camera.select(Language.JAPANESE)
        assertThat(camera.languages.first().selected).isEqualTo(Language.JAPANESE)
        assertThat(appStore.data.first()[stringPreferencesKey("language")]).isNull()
    }

    @Test
    fun onlyEnabledLanguagesCanBeSelectedAndATurnedOffChoiceReturnsWhenTurnedOnAgain() = runTest {
        val profiles = LanguageProfiles(MemoryStore())
        profiles.turnOn(Language.ENGLISH)
        val cameraStore = MemoryStore()
        val camera = CameraPreferences(cameraStore, profiles)
        assertThat(camera.languages.first().selected).isEqualTo(Language.ENGLISH)
        camera.select(Language.ARABIC)
        assertThat(camera.languages.first().selected).isEqualTo(Language.ENGLISH)
        profiles.turnOff(Language.ENGLISH)
        assertThat(camera.languages.first().selected).isEqualTo(Language.JAPANESE)
        profiles.turnOn(Language.ENGLISH, activate = false)
        assertThat(camera.languages.first().selected).isEqualTo(Language.ENGLISH)
    }

    @Test
    fun aChoiceMadeWhileTheRememberedLanguageIsOffReplacesIt() = runTest {
        val profiles = LanguageProfiles(MemoryStore())
        profiles.turnOn(Language.ENGLISH)
        profiles.turnOn(Language.KOREAN)
        val camera = CameraPreferences(MemoryStore(), profiles)
        camera.select(Language.ENGLISH)
        profiles.turnOff(Language.ENGLISH)
        assertThat(camera.languages.first().selected).isEqualTo(profiles.current().active)
        camera.select(Language.KOREAN)
        profiles.turnOn(Language.ENGLISH, activate = false)
        assertThat(camera.languages.first().selected).isEqualTo(Language.KOREAN)
    }

    @Test
    fun theHistoryStartsManualAndUnlimitedAndKeepsItsSettings() = runTest {
        val camera = CameraPreferences(MemoryStore(), LanguageProfiles(MemoryStore()))
        assertThat(camera.history.first()).isEqualTo(HistorySettings(autoSave = false, limits = HistoryLimits(0, 0)))
        camera.setAutoSave(true)
        camera.setLimits(HistoryLimits(count = 50, megabytes = 500))
        assertThat(camera.history.first()).isEqualTo(HistorySettings(autoSave = true, limits = HistoryLimits(50, 500)))
    }

    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
}
