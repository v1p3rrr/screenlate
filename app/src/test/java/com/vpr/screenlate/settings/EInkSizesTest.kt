package com.vpr.screenlate.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EInkSizesTest {

    private class Stores(val settings: AppSettingsRepository, val overlay: OverlaySettingsRepository, val popup: PopupAppearanceRepository) {
        val sizes = EInkSizes(settings, overlay, popup)

        suspend fun bubble() = overlay.settings.first().bubbleSizeDp

        suspend fun font() = popup.fontSize()
    }

    /** Preferences in memory; a file-backed store is flaky on Windows when written in quick succession. */
    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private fun stores(): Stores {
        val store = MemoryDataStore()
        return Stores(AppSettingsRepository(store), OverlaySettingsRepository(store), PopupAppearanceRepository(store))
    }

    @Test
    fun `restore brings back the enlarged sizes once and keeps one set by hand`() = runTest {
        val stores = stores()
        stores.overlay.setBubbleSize(48)
        stores.popup.setFontSize(15)
        stores.sizes.enlarge()
        assertThat(stores.bubble()).isEqualTo(EInkSizes.BUBBLE_DP)
        assertThat(stores.font()).isEqualTo(15 + EInkSizes.FONT_STEP)

        stores.popup.setFontSize(20)
        stores.sizes.restore()
        assertThat(stores.bubble()).isEqualTo(48)
        assertThat(stores.font()).isEqualTo(20)
        assertThat(stores.settings.eInkEnlargement.first()).isNull()

        // Nothing is left to restore: a later size stays.
        stores.overlay.setBubbleSize(56)
        stores.sizes.restore()
        assertThat(stores.bubble()).isEqualTo(56)
    }

    @Test
    fun `a forgotten enlargement is not undone`() = runTest {
        val stores = stores()
        stores.overlay.setBubbleSize(40)
        stores.sizes.enlarge()
        stores.sizes.forget()
        stores.sizes.restore()
        assertThat(stores.bubble()).isEqualTo(EInkSizes.BUBBLE_DP)
    }
}
