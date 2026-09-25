package com.vpr.screenlate.dictionary.api.registry

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RegistryModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DictionaryDatabase =
        Room.databaseBuilder(context, DictionaryDatabase::class.java, "dictionaries.db").build()

    @Provides
    fun provideDictionaryDao(database: DictionaryDatabase): DictionaryDao = database.dictionaryDao()
}
