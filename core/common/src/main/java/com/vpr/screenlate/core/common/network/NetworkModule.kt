package com.vpr.screenlate.core.common.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import com.vpr.screenlate.diagnostics.Diag
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Shared HTTP client. Callers needing other timeouts derive one with [OkHttpClient.newBuilder]. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // TEMPORARY DIAGNOSTICS BUILD: per-call timings (ai/notes/diagnostics.md).
        .eventListenerFactory(Diag.networkEvents)
        .build()
}
