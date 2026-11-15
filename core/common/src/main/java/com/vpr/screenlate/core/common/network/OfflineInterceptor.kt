package com.vpr.screenlate.core.common.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Thrown when an outbound network request is blocked because Forced Offline Mode is active. */
class OfflineModeException(
    message: String = "Network requests are blocked in Forced Offline Mode",
) : IOException(message)

/**
 * Interceptor that immediately blocks outbound HTTP requests when Forced Offline Mode is active,
 * guaranteeing zero socket/network leakage.
 */
class OfflineInterceptor(private val isOffline: () -> Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (isOffline()) {
            throw OfflineModeException()
        }
        return chain.proceed(chain.request())
    }
}
