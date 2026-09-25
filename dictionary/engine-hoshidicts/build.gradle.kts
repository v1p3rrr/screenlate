// This module links the GPL-3.0 hoshidicts library. Keep GPL code confined here so it can be swapped out.
plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
}

android {
    namespace = "com.vpr.screenlate.dictionary.engine.hoshidicts"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":dictionary:api"))
}
