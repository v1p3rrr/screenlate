package com.vpr.screenlate.core.common.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock

/**
 * Resources in the language chosen for the app.
 *
 * The system applies a per-app language to activities only: the application object, services
 * and workers keep the system language. Components that show text outside activities return
 * these from `getResources()`.
 *
 * @param base the component's base context; it must not call back into `getResources()` of the owner.
 */
class AppLanguageResources(
    private val base: Context,
    private val locales: () -> LocaleList? = { AppLanguage.locales(base) },
) {
    private var cached: Cached? = null

    fun resources(fallback: Resources): Resources {
        val wanted = locales() ?: return fallback
        val config = fallback.configuration
        cached?.let { if (it.locales == wanted && it.base == config) return it.resources }
        val localized = Configuration(config).apply { setLocales(wanted) }
        return base.createConfigurationContext(localized).resources
            .also { cached = Cached(wanted, Configuration(config), it) }
    }

    private class Cached(val locales: LocaleList, val base: Configuration, val resources: Resources)
}

/** The app's own language list, re-read from the system at most every [REFRESH_MS]. */
object AppLanguage {
    private const val REFRESH_MS = 2_000L

    @Volatile private var locales: LocaleList? = null

    @Volatile private var readAt = Long.MIN_VALUE

    /** Null when the app follows the system language. */
    fun locales(context: Context): LocaleList? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val now = SystemClock.elapsedRealtime()
        if (readAt == Long.MIN_VALUE || now - readAt > REFRESH_MS) {
            locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeUnless { it.isEmpty }
            readAt = now
        }
        return locales
    }
}
