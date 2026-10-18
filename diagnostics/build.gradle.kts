// TEMPORARY DIAGNOSTICS BUILD: this whole module is removed after the analysis (ai/notes/diagnostics.md).
plugins {
    alias(libs.plugins.screenlate.android.library)
}

android {
    namespace = "com.vpr.screenlate.diagnostics"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    testImplementation(libs.mockwebserver)
}
