import com.vpr.screenlate.buildlogic.disableAnkiDroidLintChecks
plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vpr.screenlate.core.anki"
}

dependencies {
    api(project(":core:common"))
    // LGPL-3.0; a thin client of AnkiDroid's content provider.
    implementation(libs.ankidroid.api)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
}

disableAnkiDroidLintChecks()
