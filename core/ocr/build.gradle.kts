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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.mlkit.text.recognition.latin)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.tesseract4android)
    implementation(project(":core:ocr-mnn"))
}
