package com.vpr.screenlate.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

object ScreenlateSdk {
    const val COMPILE = 37
    const val MIN = 30
    const val TARGET = 37

    /** Used to build native code and to strip native libraries when packaging. */
    const val NDK = "29.0.14206865"

    /** Phones and the x86_64 emulator; other ABIs are dropped from dependencies too. */
    val ABIS = listOf("arm64-v8a", "x86_64")
}

internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.compileSdk = ScreenlateSdk.COMPILE
    extension.ndkVersion = ScreenlateSdk.NDK
    // The AnkiDroid API library ships a lint check for AnkiDroid's own CrowdIn translations.
    extension.lint.disable += "DuplicateCrowdInStrings"
    extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    extension.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    dependencies {
        add("testImplementation", libs.library("junit"))
        add("testImplementation", libs.library("truth"))
        add("testImplementation", libs.library("kotlinx-coroutines-test"))
        add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
        add("androidTestImplementation", libs.library("androidx-test-runner"))
        add("androidTestImplementation", libs.library("truth"))
        add("androidTestImplementation", libs.library("kotlinx-coroutines-test"))
    }
}
