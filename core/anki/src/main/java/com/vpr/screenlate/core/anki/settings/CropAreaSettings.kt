package com.vpr.screenlate.core.anki.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.common.settings.cached
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Screenshot crop rectangle stored as `[0, 1]` fractions of the screenshot bounds so it remains resolution-independent.
 */
@Serializable
data class NormalizedCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** Whether this rectangle defines a finite, non-degenerate area inside `[0, 1]`. */
    val isValid: Boolean
        get() = left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            left >= 0f && top >= 0f && right <= 1f && bottom <= 1f &&
            right - left >= MIN_FRACTION && bottom - top >= MIN_FRACTION

    /** Whether this crop effectively covers the entire image. */
    val isWhole: Boolean
        get() = left <= WHOLE_EPSILON && top <= WHOLE_EPSILON &&
            right >= 1f - WHOLE_EPSILON && bottom >= 1f - WHOLE_EPSILON

    /** Maps this normalized rectangle into [bounds] coordinates. */
    fun toBox(bounds: Box): Box = Box(
        left = bounds.left + left * bounds.width,
        top = bounds.top + top * bounds.height,
        right = bounds.left + right * bounds.width,
        bottom = bounds.top + bottom * bounds.height,
    )

    companion object {
        private const val MIN_FRACTION = 0.005f
        private const val WHOLE_EPSILON = 0.005f

        /** Normalizes [selection] relative to [bounds]; null when [bounds] or the clipped area is empty. */
        fun fromBox(selection: Box, bounds: Box): NormalizedCrop? {
            if (!bounds.width.isFinite() || !bounds.height.isFinite() || bounds.width <= 0f || bounds.height <= 0f) {
                return null
            }
            val clipped = selection.intersection(bounds) ?: return null
            val crop = NormalizedCrop(
                left = ((clipped.left - bounds.left) / bounds.width).coerceIn(0f, 1f),
                top = ((clipped.top - bounds.top) / bounds.height).coerceIn(0f, 1f),
                right = ((clipped.right - bounds.left) / bounds.width).coerceIn(0f, 1f),
                bottom = ((clipped.bottom - bounds.top) / bounds.height).coerceIn(0f, 1f),
            )
            return crop.takeIf { it.isValid }
        }
    }
}

/**
 * Shared screenshot crop memory settings across all languages.
 *
 * Portrait (`height >= width`) and landscape (`width > height`) screenshots keep separate remembered areas so rotating
 * the device does not apply a landscape frame to a portrait screen or vice versa.
 */
data class CropAreaSettings(
    val rememberArea: Boolean = false,
    val portrait: NormalizedCrop? = null,
    val landscape: NormalizedCrop? = null,
) {
    /** The remembered crop matching [bounds]'s orientation; null if none has been saved for that orientation. */
    fun cropFor(bounds: Box): NormalizedCrop? = if (isLandscape(bounds)) landscape else portrait

    companion object {
        internal fun isLandscape(bounds: Box): Boolean = bounds.width > bounds.height
    }
}

/** Persists the shared screenshot crop memory toggle and per-orientation last crop rectangles. */
@Singleton
class CropAreaRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun readCrop(raw: String?): NormalizedCrop? =
        raw?.let { runCatching { json.decodeFromString<NormalizedCrop>(it) }.getOrNull() }?.takeIf { it.isValid }

    private val readSettings: (Preferences) -> CropAreaSettings = { prefs ->
        CropAreaSettings(
            rememberArea = prefs[REMEMBER_AREA] ?: false,
            portrait = readCrop(prefs[PORTRAIT]),
            landscape = readCrop(prefs[LANDSCAPE]),
        )
    }

    val settings: Flow<CropAreaSettings> = dataStore.data.map(readSettings)

    /** The settings as last read, for a screen's first frame; null before the first read. */
    val cachedSettings: CropAreaSettings? get() = dataStore.cached(readSettings)

    suspend fun current(): CropAreaSettings = settings.first()

    suspend fun setRememberArea(enabled: Boolean) {
        dataStore.edit { it[REMEMBER_AREA] = enabled }
    }

    /** Saves [selection] normalized against [bounds] under its orientation bucket (landscape or portrait). */
    suspend fun saveCrop(selection: Box, bounds: Box) {
        val normalized = NormalizedCrop.fromBox(selection, bounds) ?: return
        val key = if (CropAreaSettings.isLandscape(bounds)) LANDSCAPE else PORTRAIT
        dataStore.edit { it[key] = json.encodeToString(normalized) }
    }

    companion object {
        const val REMEMBER_AREA_KEY = "anki_remember_crop_area"
        const val PORTRAIT_KEY = "anki_crop_portrait"
        const val LANDSCAPE_KEY = "anki_crop_landscape"

        private val REMEMBER_AREA = booleanPreferencesKey(REMEMBER_AREA_KEY)
        private val PORTRAIT = stringPreferencesKey(PORTRAIT_KEY)
        private val LANDSCAPE = stringPreferencesKey(LANDSCAPE_KEY)
    }
}
