package com.vpr.screenlate.core.photos.store

import android.content.Context
import androidx.room.Room
import com.vpr.screenlate.core.photos.PhotoDocuments
import com.vpr.screenlate.core.photos.PhotoRecognitionEngine
import com.vpr.screenlate.core.photos.PhotoOcrEngine
import com.vpr.screenlate.core.photos.CameraPreferenceStore
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PhotosModule {
    @Binds
    abstract fun bindDocuments(store: PhotoStore): PhotoDocuments
    @Binds
    abstract fun bindRecognition(engine: PhotoOcrEngine): PhotoRecognitionEngine

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): PhotoDatabase =
            Room.databaseBuilder(context, PhotoDatabase::class.java, "photos.db").addMigrations(PhotoDatabase.MIGRATION_1_2).build()

        @Provides
        @Singleton
        @CameraPreferenceStore
        fun provideCameraPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { File(context.noBackupFilesDir, "camera.preferences_pb") })
    }
}
