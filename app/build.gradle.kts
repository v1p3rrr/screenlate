import com.vpr.screenlate.buildlogic.CopyAssetTask
import com.vpr.screenlate.buildlogic.DownloadAssetsTask
import com.vpr.screenlate.buildlogic.disableAnkiDroidLintChecks
import com.vpr.screenlate.buildlogic.screenlateVersion
import java.util.Properties

plugins {
    alias(libs.plugins.screenlate.android.application)
    alias(libs.plugins.screenlate.android.compose)
    alias(libs.plugins.screenlate.hilt)
    alias(libs.plugins.kotlin.serialization)
    // Generates the list of libraries and their licenses for the About screen (R.raw.aboutlibraries).
    alias(libs.plugins.aboutlibraries)
}

// Release signing is optional: create keystore.properties (see README) to enable it.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.vpr.screenlate"

    // From the latest vX.Y.Z tag; see ScreenlateVersion.
    val version = screenlateVersion()
    defaultConfig {
        applicationId = "com.vpr.screenlate"
        versionCode = version.code
        versionName = version.name
        // The latest stable release; -Pscreenlate.updateFeed points test builds at another server.
        val updateFeed = providers.gradleProperty("screenlate.updateFeed")
            .getOrElse("https://api.github.com/repos/v1p3rrr/screenlate/releases/latest")
        buildConfigField("String", "UPDATE_FEED", "\"$updateFeed\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // Lists every translated language in the system's per-app language settings.
    androidResources {
        generateLocaleConfig = true
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
            versionNameSuffix = version.commit?.let { "-$it" }
            manifestPlaceholders["appLabel"] = "Screenlate Dev"
        }
        release {
            manifestPlaceholders["appLabel"] = "Screenlate"
            // Without keystore.properties, CI signs test builds of releases with the debug key and does not publish them.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug").takeIf { providers.gradleProperty("screenlate.debugSignedRelease").isPresent }
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
            "10-jmdict-english.zip" to
                "https://github.com/yomidevs/jmdict-yomitan/releases/download/2026-09-27/JMdict_english.zip",
            "20-jiten-global-frequency.zip" to
                "https://api.jiten.moe/api/frequency-list/download?downloadType=yomitan",
            "30-kanjium-pitch-accents.zip" to
                "https://github.com/toasted-nutbread/yomichan-pitch-accent-dictionary/releases/download/1.0.0/" +
                "kanjium_pitch_accents.zip",
            "40-kanjidic-english.zip" to
                "https://github.com/yomidevs/jmdict-yomitan/releases/download/2026-09-27/KANJIDIC_english.zip",
        ),
    )
    cacheDir.set(rootProject.layout.projectDirectory.dir("dicts/bundled"))
}

// The About screen shows NOTICE as it is in the repository.
val noticeAsset = tasks.register<CopyAssetTask>("copyNoticeAsset") {
    source.set(rootProject.layout.projectDirectory.file("NOTICE"))
    assetName.set("NOTICE.txt")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundledDictionaries, DownloadAssetsTask::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(noticeAsset, CopyAssetTask::outputDir)
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
    implementation(libs.androidx.compose.foundation.layout)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.androidx.datastore.preferences)
}

disableAnkiDroidLintChecks()
