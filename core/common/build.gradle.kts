plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
}

android {
    namespace = "com.vpr.screenlate.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.okhttp)
    // TEMPORARY DIAGNOSTICS BUILD: every module reaches Diag through core:common (ai/notes/diagnostics.md).
    api(project(":diagnostics"))
    implementation(libs.androidx.datastore.preferences)
}
