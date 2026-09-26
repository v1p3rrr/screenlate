plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
    alias(libs.plugins.screenlate.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vpr.screenlate.dictionary.api"
}

dependencies {
    api(project(":core:common"))
    api(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
}
