package com.vpr.screenlate.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

/** A GitHub release, as `GET /repos/{owner}/{repo}/releases/latest` returns it. */
@Serializable
data class Release(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    /** The changelog, in Markdown. */
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

/** Reading releases the way the release workflow publishes them: `screenlate-<tag>-<abi>.apk` plus a universal APK. */
object Releases {
    private val TAG = Regex("""v?(\d+)\.(\d+)\.(\d+)""")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Release = json.decodeFromString(text)

    /** The version code the build gives this tag (X·10000 + Y·100 + Z); null for tags of another form. */
    fun versionCode(tag: String): Int? {
        val (major, minor, patch) = TAG.matchEntire(tag.trim())?.destructured?.toList()?.map(String::toInt) ?: return null
        if (minor >= 100 || patch >= 100) return null
        return major * 10000 + minor * 100 + patch
    }

    /** A stable release newer than the installed version. */
    fun isNewer(release: Release, installedCode: Long): Boolean =
        !release.draft && !release.prerelease && (versionCode(release.tag) ?: 0) > installedCode

    /** The APK for the first of [abis] (the device's, in preference order) that the release has, else the universal one. */
    fun apkFor(release: Release, abis: List<String>): ReleaseAsset? {
        fun named(suffix: String) = release.assets.firstOrNull { it.name == "screenlate-${release.tag}-$suffix.apk" }
        return abis.firstNotNullOfOrNull(::named) ?: named("universal")
    }

    /** The changelog as plain text: Markdown heading marks and bold markers are dropped, and so is a leading "Changes". */
    fun notes(release: Release): String = release.body.orEmpty().lines()
        .map { line -> line.trimEnd().let { if (it.startsWith("#")) it.trimStart('#').trim() else it }.replace("**", "") }
        .dropWhile { it.isBlank() || it.equals("Changes", ignoreCase = true) }
        .joinToString("\n")
        .trim()
}
