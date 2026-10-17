import com.vpr.screenlate.buildlogic.ScreenlateSdk

// MNN (Apache-2.0) from the submodule, built for the CPU only, behind a small JNI wrapper. Its native build is slow, so
// it lives in a module of its own.
plugins {
    alias(libs.plugins.screenlate.android.library)
}

android {
    namespace = "com.vpr.screenlate.core.ocr.mnn"

    defaultConfig {
        ndk {
            abiFilters += ScreenlateSdk.ABIS
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
