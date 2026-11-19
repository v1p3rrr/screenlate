plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
}

android {
    namespace = "com.vpr.screenlate.core.media"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
}

