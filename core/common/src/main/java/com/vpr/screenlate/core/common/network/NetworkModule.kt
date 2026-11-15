package com.vpr.screenlate.core.common.network

import com.vpr.screenlate.core.common.settings.AppSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Shared HTTP client. Callers needing other timeouts derive one with [OkHttpClient.newBuilder]. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(appSettings: AppSettingsRepository): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(OfflineInterceptor { appSettings.cachedOfflineMode == true })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
