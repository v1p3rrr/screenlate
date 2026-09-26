import com.vpr.screenlate.buildlogic.disableAnkiDroidLintChecks
plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vpr.screenlate.overlay"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ocr"))
    implementation(project(":dictionary:api"))
    implementation(project(":core:anki"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
}

disableAnkiDroidLintChecks()
