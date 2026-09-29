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

/**
 * Downloads files into a generated assets directory. Downloads are kept in [cacheDir] so a clean build does not
 * fetch them again; a changed URL is downloaded again, and deleting the cache forces a new download.
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
        val cache = DownloadCache(cacheDir.get().asFile)
        for ((name, url) in files.get()) {
            val cached = cache.get(name, url) { logger.lifecycle("Downloading $name from $url") }
            cached.copyTo(File(target, name), overwrite = true)
        }
    }
}
