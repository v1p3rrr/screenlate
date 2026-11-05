package com.vpr.screenlate.camera

import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.photos.CameraPreferences
import com.vpr.screenlate.core.photos.LookupSources
import com.vpr.screenlate.core.photos.PhotoDocuments
import com.vpr.screenlate.core.photos.PhotoRecognitionJobs
import com.vpr.screenlate.dictionary.api.catalog.DictionaryCatalog
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.overlay.install.LanguageInstallation
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only access for emulator photo workflow fixtures; absent from release builds. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhotoTestAccess {
    fun documents(): PhotoDocuments
    fun jobs(): PhotoRecognitionJobs
    fun sources(): LookupSources
    fun preferences(): CameraPreferences
    fun profiles(): LanguageProfiles
    fun dictionaries(): DictionaryRepository
    fun catalog(): DictionaryCatalog
    fun installation(): LanguageInstallation
    fun ankiSettings(): com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
    fun commonPreferences(): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
}
