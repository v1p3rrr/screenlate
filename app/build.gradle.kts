import com.vpr.screenlate.buildlogic.DownloadAssetsTask
import com.vpr.screenlate.buildlogic.disableAnkiDroidLintChecks
import java.util.Properties

plugins {
    alias(libs.plugins.screenlate.android.application)
    alias(libs.plugins.screenlate.android.compose)
    alias(libs.plugins.screenlate.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is optional: create keystore.properties (see README) to enable it.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.vpr.screenlate"

    defaultConfig {
        applicationId = "com.vpr.screenlate"
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Screenlate Dev"
        }
        release {
            manifestPlaceholders["appLabel"] = "Screenlate"
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
        }
    }
}

// Dictionaries installed on first launch. Numeric prefixes set their initial priority. Versions are pinned where
// the publisher offers stable URLs; see NOTICE for licenses.
val bundledDictionaries = tasks.register<DownloadAssetsTask>("downloadBundledDictionaries") {
    assetPath.set("dictionaries")
    files.putAll(
        mapOf(
            "10-jitendex.zip" to
                "https://github.com/stephenmk/stephenmk.github.io/releases/download/2026.08.11.0/jitendex-yomitan.zip",
            "20-jiten-global-frequency.zip" to
                "https://api.jiten.moe/api/frequency-list/download?downloadType=yomitan",
            "30-kanjium-pitch-accents.zip" to
                "https://github.com/toasted-nutbread/yomichan-pitch-accent-dictionary/releases/download/1.0.0/" +
                "kanjium_pitch_accents.zip",
        ),
    )
    cacheDir.set(rootProject.layout.projectDirectory.dir("dicts/bundled"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundledDictionaries, DownloadAssetsTask::outputDir)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ocr"))
    implementation(project(":core:anki"))
    implementation(project(":dictionary:api"))
    implementation(project(":dictionary:engine-hoshidicts"))
    implementation(project(":dictionary:render-yomitan"))
    implementation(project(":overlay"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}

disableAnkiDroidLintChecks()
