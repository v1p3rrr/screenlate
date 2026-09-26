// This module links the GPL-3.0 hoshidicts library. Keep GPL code confined here so it can be swapped out.
plugins {
    alias(libs.plugins.screenlate.android.library)
    alias(libs.plugins.screenlate.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vpr.screenlate.dictionary.engine.hoshidicts"
    ndkVersion = "29.0.14206865"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
}

dependencies {
    implementation(project(":dictionary:api"))
    implementation(libs.kotlinx.serialization.json)
}
