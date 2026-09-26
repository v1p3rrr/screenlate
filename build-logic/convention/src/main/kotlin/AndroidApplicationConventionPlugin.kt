import com.android.build.api.dsl.ApplicationExtension
import com.vpr.screenlate.buildlogic.ScreenlateSdk
import com.vpr.screenlate.buildlogic.configureAndroidCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

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
                    ndk { abiFilters += ScreenlateSdk.ABIS }
                }
                // Yomitan archives are already compressed; stored entries can also be opened as file descriptors.
                androidResources { noCompress += "zip" }
            }
        }
    }
}
