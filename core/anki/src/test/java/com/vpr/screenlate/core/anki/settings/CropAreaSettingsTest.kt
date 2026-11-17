package com.vpr.screenlate.core.anki.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CropAreaSettingsTest {

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val dataStore = MemoryDataStore()
    private val repository = CropAreaRepository(dataStore)

    @Test
    fun `defaults to rememberArea off with no stored crops`() = runTest {
        val settings = repository.current()
        assertThat(settings.rememberArea).isFalse()
        assertThat(settings.portrait).isNull()
        assertThat(settings.landscape).isNull()
    }

    @Test
    fun `rememberArea toggle persists`() = runTest {
        repository.setRememberArea(true)
        assertThat(repository.settings.first().rememberArea).isTrue()

        repository.setRememberArea(false)
        assertThat(repository.settings.first().rememberArea).isFalse()
    }

    @Test
    fun `portrait and landscape crops are stored independently`() = runTest {
        val portraitBounds = Box(0f, 0f, 1080f, 2400f)
        val portraitSelection = Box(108f, 600f, 972f, 1800f)
        val landscapeBounds = Box(0f, 0f, 2400f, 1080f)
        val landscapeSelection = Box(240f, 108f, 2160f, 972f)

        repository.saveCrop(portraitSelection, portraitBounds)
        repository.saveCrop(landscapeSelection, landscapeBounds)

        val settings = repository.current()
        val restoredPortrait = settings.cropFor(portraitBounds)!!.toBox(portraitBounds)
        val restoredLandscape = settings.cropFor(landscapeBounds)!!.toBox(landscapeBounds)

        assertThat(restoredPortrait.left).isWithin(0.01f).of(108f)
        assertThat(restoredPortrait.top).isWithin(0.01f).of(600f)
        assertThat(restoredPortrait.right).isWithin(0.01f).of(972f)
        assertThat(restoredPortrait.bottom).isWithin(0.01f).of(1800f)

        assertThat(restoredLandscape.left).isWithin(0.01f).of(240f)
        assertThat(restoredLandscape.top).isWithin(0.01f).of(108f)
        assertThat(restoredLandscape.right).isWithin(0.01f).of(2160f)
        assertThat(restoredLandscape.bottom).isWithin(0.01f).of(972f)
    }

    @Test
    fun `normalized crop handles offset bounds and clips out-of-bounds selections`() {
        val bounds = Box(100f, 200f, 1100f, 2200f)
        val normalized = NormalizedCrop.fromBox(Box(0f, 100f, 600f, 1200f), bounds)!!

        assertThat(normalized.left).isWithin(0.0001f).of(0f)
        assertThat(normalized.top).isWithin(0.0001f).of(0f)
        assertThat(normalized.right).isWithin(0.0001f).of(0.5f)
        assertThat(normalized.bottom).isWithin(0.0001f).of(0.5f)
        assertThat(normalized.isWhole).isFalse()

        val mapped = normalized.toBox(bounds)
        assertThat(mapped.left).isWithin(0.01f).of(100f)
        assertThat(mapped.top).isWithin(0.01f).of(200f)
        assertThat(mapped.right).isWithin(0.01f).of(600f)
        assertThat(mapped.bottom).isWithin(0.01f).of(1200f)
    }

    @Test
    fun `whole screen crop is recognized by isWhole`() {
        val bounds = Box(0f, 0f, 1080f, 2400f)
        val normalized = NormalizedCrop.fromBox(bounds, bounds)!!
        assertThat(normalized.isWhole).isTrue()
    }

    @Test
    fun `invalid or corrupt stored crop is ignored`() = runTest {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey(CropAreaRepository.PORTRAIT_KEY)] = "not valid json"
                this[stringPreferencesKey(CropAreaRepository.LANDSCAPE_KEY)] = """{"left":0.8,"top":0.2,"right":0.1,"bottom":0.9}"""
            }
        }
        val settings = repository.current()
        assertThat(settings.portrait).isNull()
        assertThat(settings.landscape).isNull()
    }

    @Test
    fun `degenerate bounds or tiny selection returns null`() {
        assertThat(NormalizedCrop.fromBox(Box(0f, 0f, 10f, 10f), Box(0f, 0f, 0f, 100f))).isNull()
        assertThat(NormalizedCrop.fromBox(Box(0f, 0f, 1f, 1f), Box(0f, 0f, 1000f, 1000f))).isNull()
    }
}
