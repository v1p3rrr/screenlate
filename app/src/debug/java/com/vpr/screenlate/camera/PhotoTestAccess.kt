package com.vpr.screenlate.camera

import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.photos.CameraPreferences
import com.vpr.screenlate.core.photos.PhotoDocuments
import com.vpr.screenlate.core.photos.PhotoRecognitionJobs
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only access for emulator photo workflow fixtures; absent from release builds. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhotoTestAccess {
    fun documents(): PhotoDocuments
    fun jobs(): PhotoRecognitionJobs
    fun preferences(): CameraPreferences
    fun profiles(): LanguageProfiles
    fun ankiSettings(): com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
    fun commonPreferences(): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
}
