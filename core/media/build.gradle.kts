plugins {
    alias(libs.plugins.screenlate.android.library)
}

android {
    namespace = "com.vpr.screenlate.core.media"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
