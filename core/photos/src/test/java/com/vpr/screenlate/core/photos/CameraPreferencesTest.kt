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
    fun onlyEnabledLanguagesCanBeSelectedAndInvalidSelectionIsPersistentlyReplaced() = runTest {
        val profiles = LanguageProfiles(MemoryStore())
        profiles.turnOn(Language.ENGLISH)
        val cameraStore = MemoryStore()
        val camera = CameraPreferences(cameraStore, profiles)
        assertThat(camera.languages.first().selected).isEqualTo(Language.ENGLISH)
        camera.select(Language.ARABIC)
        assertThat(camera.languages.first().selected).isEqualTo(Language.ENGLISH)
        profiles.turnOff(Language.ENGLISH)
        assertThat(camera.languages.first().selected).isEqualTo(Language.JAPANESE)
        profiles.turnOn(Language.ENGLISH)
        assertThat(camera.languages.first().selected).isEqualTo(Language.JAPANESE)
    }

    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
}
