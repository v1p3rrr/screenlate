plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
    alias(libs.plugins.screenlate.room)
}

android {
    namespace = "com.vpr.screenlate.dictionary.api"
}

dependencies {
    implementation(project(":core:common"))
}
