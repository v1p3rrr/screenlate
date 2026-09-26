package com.vpr.screenlate.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Downloads files into a generated assets directory. Downloads are kept in [cacheDir] so a clean build does not
 * fetch them again; delete the cache to force a new download.
 */
abstract class DownloadAssetsTask : DefaultTask() {
    /** Asset file name to URL. */
    @get:Input
    abstract val files: MapProperty<String, String>

    /** Subdirectory of the assets root that receives the files. */
    @get:Input
    abstract val assetPath: Property<String>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val target = outputDir.get().asFile.resolve(assetPath.get())
        target.deleteRecursively()
        target.mkdirs()
        val cache = cacheDir.get().asFile.apply { mkdirs() }
        for ((name, url) in files.get()) {
            val cached = File(cache, name)
            if (!cached.exists()) {
                logger.lifecycle("Downloading $name from $url")
                fetch(url, cached)
            }
            cached.copyTo(File(target, name), overwrite = true)
        }
    }

    private fun fetch(url: String, destination: File) {
        val partial = File(destination.parentFile, "${destination.name}.part")
        var location = url
        repeat(MAX_REDIRECTS) {
            val connection = URI(location).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "screenlate-build")
            when (val code = connection.responseCode) {
                in 300..399 -> {
                    location = URI(location).resolve(connection.getHeaderField("Location")).toString()
                    connection.disconnect()
                }
                HttpURLConnection.HTTP_OK -> {
                    connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
                    check(partial.renameTo(destination)) { "Cannot move $partial to $destination" }
                    return
                }
                else -> error("Download of $url failed with HTTP $code")
            }
        }
        error("Too many redirects for $url")
    }

    private companion object {
        const val MAX_REDIRECTS = 10
    }
}
