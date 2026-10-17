import com.vpr.screenlate.buildlogic.ScreenlateSdk

// The on-device OCR comparison (PP-OCR on ONNX Runtime and on MNN, Tesseract, ML Kit) as instrumented tests. Not part of the
// app. Gradle properties: `ocrCompareMnn`, a folder with MNN's source in `mnn-src/MNN-<version>` and a static build per
// ABI in `mnn-<abi>`; `ocrCompareData`, the test assets (`models/`, `images/`); `ocrCompareAbis`, comma-separated ABIs
// to package instead of all (a smaller APK for one device).
plugins {
    alias(libs.plugins.screenlate.android.library)
}

val mnnRoot = providers.gradleProperty("ocrCompareMnn").orNull
val dataRoot = providers.gradleProperty("ocrCompareData").orNull
val abis = providers.gradleProperty("ocrCompareAbis").orNull?.split(",")

android {
    namespace = "com.vpr.screenlate.experiments.ocrcompare"

    defaultConfig {
        ndk {
            abiFilters += abis ?: ScreenlateSdk.ABIS
        }
        if (mnnRoot != null) {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DANDROID_STL=c++_static", "-DMNN_ROOT=$mnnRoot")
                }
            }
        }
    }

    if (mnnRoot != null) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.31.6"
            }
        }
    }

    androidResources {
        noCompress += listOf("onnx", "mnn", "traineddata")
    }
}

androidComponents {
    onVariants { variant ->
        if (dataRoot != null) variant.androidTest?.sources?.assets?.addStaticSourceDirectory(dataRoot)
    }
}

dependencies {
    implementation(project(":core:ocr"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    implementation("cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
}
