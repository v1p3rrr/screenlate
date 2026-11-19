plugins {
    alias(libs.plugins.screenlate.android.application)
}

android {
    namespace = "com.vpr.screenlate.audioprobe"
    defaultConfig {
        applicationId = "com.vpr.screenlate.audioprobe"
        versionCode = 1
        versionName = "1"
    }
    flavorDimensions += "role"
    productFlavors {
        create("recorder") { dimension = "role" }
        create("source") {
            dimension = "role"
            applicationIdSuffix = ".source"
        }
    }
}
