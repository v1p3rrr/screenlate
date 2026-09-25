plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
}

android {
    namespace = "com.vpr.screenlate.overlay"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
}
