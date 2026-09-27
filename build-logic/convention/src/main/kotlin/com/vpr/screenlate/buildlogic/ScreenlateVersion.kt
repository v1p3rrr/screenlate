package com.vpr.screenlate.buildlogic

import org.gradle.api.Project

/**
 * The app version: the name X.Y.Z of the latest `vX.Y.Z` tag and the code X·10000 + Y·100 + Z. Without a tag the
 * version is 0.1.0. [commit] is the short hash of the checked-out commit, appended to debug version names.
 */
data class ScreenlateVersion(val name: String, val code: Int, val commit: String?) {
    companion object {
        private val TAG = Regex("""v?(\d+)\.(\d+)\.(\d+)""")

        fun parse(tag: String?, commit: String?): ScreenlateVersion {
            val parts = tag?.trim()?.let(TAG::matchEntire)?.destructured?.toList()?.map(String::toInt)
            val (major, minor, patch) = parts ?: listOf(0, 1, 0)
            require(minor < 100 && patch < 100) { "Minor and patch versions must stay below 100: $tag" }
            return ScreenlateVersion("$major.$minor.$patch", major * 10000 + minor * 100 + patch, commit?.trim()?.ifEmpty { null })
        }
    }
}

/**
 * Reads the version from git; a source archive without git history carries it in `version.txt` instead.
 */
fun Project.screenlateVersion(): ScreenlateVersion {
    val file = providers.fileContents(rootProject.layout.projectDirectory.file("version.txt")).asText.orNull
    if (file != null) return ScreenlateVersion.parse(file, null)
    fun git(vararg args: String): String? = runCatching {
        providers.exec {
            commandLine("git", *args)
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().ifEmpty { null }
    }.getOrNull()
    return ScreenlateVersion.parse(
        tag = git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*"),
        commit = git("rev-parse", "--short", "HEAD"),
    )
}
