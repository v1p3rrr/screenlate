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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
