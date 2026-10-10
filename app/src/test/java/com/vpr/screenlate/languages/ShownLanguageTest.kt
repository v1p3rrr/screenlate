package com.vpr.screenlate.languages

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShownLanguageTest {

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun `the active language shows until another is picked, and one turned off gives way to it`() =
        runTest(UnconfinedTestDispatcher()) {
            val profiles = LanguageProfiles(MemoryDataStore())
            profiles.turnOn(Language.ENGLISH, activate = false)
            val shown = ShownLanguage(profiles, backgroundScope)
            assertThat(shown.language.value).isEqualTo(Language.JAPANESE)

            shown.show(Language.ENGLISH)
            assertThat(shown.language.value).isEqualTo(Language.ENGLISH)

            profiles.turnOff(Language.ENGLISH)
            assertThat(shown.language.value).isEqualTo(Language.JAPANESE)
            assertThat(shown.profiles.value.several).isFalse()
        }

    @Test
    fun `a language not turned on is not shown`() = runTest(UnconfinedTestDispatcher()) {
        val shown = ShownLanguage(LanguageProfiles(MemoryDataStore()), backgroundScope)
        shown.show(Language.ENGLISH)
        assertThat(shown.language.value).isEqualTo(Language.JAPANESE)
    }
}
