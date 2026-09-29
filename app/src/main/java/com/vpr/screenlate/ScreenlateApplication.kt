package com.vpr.screenlate

import android.app.Application
import android.content.res.Resources
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vpr.screenlate.core.common.locale.AppLanguageResources
import com.vpr.screenlate.dictionary.api.imports.DictionaryImports
import com.vpr.screenlate.dictionary.api.imports.DictionaryRepair
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
        MainScope().launch(Dispatchers.IO) { dictionaryRepair.repair() }
    }
}
