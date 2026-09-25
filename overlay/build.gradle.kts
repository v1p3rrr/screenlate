plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
}

android {
    namespace = "com.vpr.screenlate.overlay"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ocr"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
}
