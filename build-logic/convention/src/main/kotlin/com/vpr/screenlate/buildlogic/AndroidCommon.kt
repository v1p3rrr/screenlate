package com.vpr.screenlate.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

object ScreenlateSdk {
    const val COMPILE = 37
    const val MIN = 30
    const val TARGET = 37
}

internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.compileSdk = ScreenlateSdk.COMPILE
    extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    extension.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    dependencies {
        add("testImplementation", libs.library("junit"))
        add("testImplementation", libs.library("truth"))
        add("testImplementation", libs.library("kotlinx-coroutines-test"))
        add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
        add("androidTestImplementation", libs.library("androidx-test-runner"))
    }
}
