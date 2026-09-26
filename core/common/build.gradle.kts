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
    implementation(libs.androidx.datastore.preferences)
}
