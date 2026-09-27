package com.vpr.screenlate.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReleasesTest {

    private val release = Releases.parse(
        """
        {
          "tag_name": "v0.3.1",
          "name": "Screenlate v0.3.1",
          "html_url": "https://github.com/v1p3rrr/screenlate/releases/tag/v0.3.1",
          "draft": false,
          "prerelease": false,
          "body": "## Changes\n\n- Faster **imports**\n- Fix the popup font",
          "assets": [
            {"name": "screenlate-v0.3.1-arm64-v8a.apk", "browser_download_url": "https://example.org/arm64.apk", "size": 69000000},
            {"name": "screenlate-v0.3.1-armeabi-v7a.apk", "browser_download_url": "https://example.org/arm32.apk", "size": 64000000},
            {"name": "screenlate-v0.3.1-universal.apk", "browser_download_url": "https://example.org/universal.apk", "size": 92000000},
            {"name": "screenlate-v0.3.1-source.tar.gz", "browser_download_url": "https://example.org/source.tar.gz"}
          ],
          "author": {"login": "someone"}
        }
        """.trimIndent(),
    )

    @Test
    fun `version codes follow the build`() {
        assertThat(Releases.versionCode("v0.3.1")).isEqualTo(301)
        assertThat(Releases.versionCode("1.12.5")).isEqualTo(11205)
        assertThat(Releases.versionCode("v1.2")).isNull()
        assertThat(Releases.versionCode("v1.100.0")).isNull()
    }

    @Test
    fun `only newer stable releases are offered`() {
        assertThat(Releases.isNewer(release, installedCode = 300)).isTrue()
        assertThat(Releases.isNewer(release, installedCode = 301)).isFalse()
        assertThat(Releases.isNewer(release.copy(prerelease = true), installedCode = 100)).isFalse()
        assertThat(Releases.isNewer(release.copy(draft = true), installedCode = 100)).isFalse()
        assertThat(Releases.isNewer(release.copy(tag = "nightly"), installedCode = 100)).isFalse()
    }

    @Test
    fun `the device's abi comes first, the universal apk last`() {
        assertThat(Releases.apkFor(release, listOf("arm64-v8a", "armeabi-v7a"))?.url).isEqualTo("https://example.org/arm64.apk")
        assertThat(Releases.apkFor(release, listOf("armeabi-v7a", "armeabi"))?.url).isEqualTo("https://example.org/arm32.apk")
        assertThat(Releases.apkFor(release, listOf("x86_64"))?.url).isEqualTo("https://example.org/universal.apk")
        assertThat(Releases.apkFor(release.copy(assets = emptyList()), listOf("x86_64"))).isNull()
    }

    @Test
    fun `notes drop markdown markers`() {
        assertThat(Releases.notes(release)).isEqualTo("- Faster imports\n- Fix the popup font")
        assertThat(Releases.notes(release.copy(body = null))).isEmpty()
        assertThat(Releases.notes(release.copy(body = "### Fixed\n- A crash"))).isEqualTo("Fixed\n- A crash")
    }
}
