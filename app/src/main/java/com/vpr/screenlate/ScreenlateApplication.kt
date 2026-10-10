package com.vpr.screenlate

import android.app.Application
import android.content.res.Resources
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vpr.screenlate.core.common.locale.AppLanguageResources
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.DictionaryRepair
import com.vpr.screenlate.dictionary.api.imports.DictionaryReset
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class ScreenlateApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dictionaryImports: DictionaryImports

    @Inject
    lateinit var dictionaryRepair: DictionaryRepair

    @Inject
    lateinit var dictionaryReset: DictionaryReset

    @Inject
    lateinit var languageProfiles: LanguageProfiles

    @Inject
    lateinit var dictionaryRepository: DictionaryRepository

    private var languageResources: AppLanguageResources? = null

    // Workers, notifications and injected contexts show text in the app's language, not the system's.
    override fun getResources(): Resources {
        val base = baseContext ?: return super.getResources()
        val language = languageResources ?: AppLanguageResources(base).also { languageResources = it }
        return language.resources(super.getResources())
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        MainScope().launch(Dispatchers.IO) {
            // Before anything writes the settings: an install without any is a first run.
            runCatching { languageProfiles.settleFirstRun { dictionaryRepository.getAll().isNotEmpty() } }
                .onFailure { Log.w(TAG, "Settling the first run failed: ${it.javaClass.simpleName}") }
            dictionaryImports.installBundled()
            launch {
                // The user confirmed it; a reset the process died in is run once more, and reported when that dies too.
                runCatching { dictionaryReset.resumeInterrupted() }
                    .onFailure { Log.w(TAG, "Finishing the dictionary reset failed: ${it.javaClass.simpleName}") }
            }
            launch {
                // A failed check must not stop the app from starting; the home screen checks again.
                runCatching { dictionaryRepair.repair() }.onFailure { Log.w(TAG, "Dictionary repair failed: ${it.javaClass.simpleName}") }
            }
        }
    }

    private companion object {
        const val TAG = "Screenlate"
    }
}
