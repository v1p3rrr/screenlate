plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
    alias(libs.plugins.screenlate.room)
}

android {
    namespace = "com.vpr.screenlate.core.photos"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:ocr"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
}
