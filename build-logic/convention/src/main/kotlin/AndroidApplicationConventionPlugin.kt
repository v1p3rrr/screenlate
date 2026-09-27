import com.android.build.api.dsl.ApplicationExtension
import com.vpr.screenlate.buildlogic.ScreenlateSdk
import com.vpr.screenlate.buildlogic.configureAndroidCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

private val ALL_ABIS = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64", "armeabi", "mips", "mips64", "riscv64")

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig {
                    minSdk = ScreenlateSdk.MIN
                    targetSdk = ScreenlateSdk.TARGET
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                // Release builds on CI pass -Pscreenlate.splitAbis for one APK per ABI plus a universal one; other
                // builds make one APK with the supported ABIs. Libraries' native code for other ABIs is dropped.
                if (providers.gradleProperty("screenlate.splitAbis").isPresent) {
                    splits.abi {
                        isEnable = true
                        reset()
                        include(*ScreenlateSdk.ABIS.toTypedArray())
                        isUniversalApk = true
                    }
                    packaging.jniLibs.excludes += (ALL_ABIS - ScreenlateSdk.ABIS.toSet()).map { "lib/$it/**" }
                } else {
                    defaultConfig.ndk { abiFilters += ScreenlateSdk.ABIS }
                }
                // Yomitan archives are already compressed; stored entries can also be opened as file descriptors.
                androidResources { noCompress += "zip" }
            }
        }
    }
}
