package com.vpr.screenlate.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * The AnkiDroid API library ships lint checks written for AnkiDroid's own code base. Call from modules that
 * have it on the classpath; elsewhere the issue ids are unknown to lint.
 */
fun Project.disableAnkiDroidLintChecks() {
    extensions.getByType<CommonExtension>().lint.disable += listOf(
        "DuplicateCrowdInStrings",
        "DirectSystemCurrentTimeMillisUsage",
    )
}

fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("Unknown library alias: $alias") }
