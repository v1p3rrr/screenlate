package com.vpr.screenlate

import android.app.Application
import android.content.res.Resources
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vpr.screenlate.core.common.locale.AppLanguageResources
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.DictionaryRepair
import com.vpr.screenlate.dictionary.api.imports.DictionaryReset
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
        dictionaryImports.installBundled()
        MainScope().launch(Dispatchers.IO) {
            // The user confirmed it; a reset the process died in is run once more, and reported when that dies too.
            runCatching { dictionaryReset.resumeInterrupted() }
                .onFailure { Log.w(TAG, "Finishing the dictionary reset failed: ${it.javaClass.simpleName}") }
        }
        MainScope().launch(Dispatchers.IO) {
            // A failed check must not stop the app from starting; the home screen checks again.
            runCatching { dictionaryRepair.repair() }.onFailure { Log.w(TAG, "Dictionary repair failed: ${it.javaClass.simpleName}") }
        }
    }

    private companion object {
        const val TAG = "Screenlate"
    }
}
