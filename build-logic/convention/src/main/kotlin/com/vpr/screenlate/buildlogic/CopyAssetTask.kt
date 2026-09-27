package com.vpr.screenlate.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Copies one project file into a generated assets directory, e.g. NOTICE for the About screen. */
abstract class CopyAssetTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: RegularFileProperty

    /** Path of the copy inside the assets root. */
    @get:Input
    abstract val assetName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve(assetName.get())
        target.parentFile.mkdirs()
        source.get().asFile.copyTo(target, overwrite = true)
    }
}
