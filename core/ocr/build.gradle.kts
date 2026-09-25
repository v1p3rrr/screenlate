plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
}

android {
    namespace = "com.vpr.screenlate.core.ocr"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.mlkit.text.recognition.japanese)
}
