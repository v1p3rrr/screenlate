package com.vpr.screenlate.overlay.fonts

import java.io.File
import kotlinx.serialization.json.Json

/** Fonts as a backup holds them: their list, in the form of the font folder's own list, and their files. */
object FontBackup {
    /** The name of the list, in the font folder and in a backup. */
    const val INDEX = "fonts.json"

    private val json = Json { ignoreUnknownKeys = true }

    /** @property index the list of the fonts, as [INDEX] holds it. */
    class Files(val index: String, val files: List<File>)

    fun encode(fonts: List<InstalledFont>): String = json.encodeToString(fonts)

    fun decode(text: String): List<InstalledFont> = json.decodeFromString(text)

    /**
     * [installed] with [restored] in place of the fonts they replace: the same font (by id), or a font of the user's
     * own with the same family, as adding a file does. Also returns the file names no font uses any more.
     */
    fun merge(installed: List<InstalledFont>, restored: List<InstalledFont>): Pair<List<InstalledFont>, List<String>> {
        val replaced = installed.filter { old ->
            restored.any { it.id == old.id || (it.catalogId == null && old.catalogId == null && it.family == old.family) }
        }
        val fonts = installed - replaced.toSet() + restored
        val used = fonts.flatMap { it.files }.mapTo(hashSetOf()) { it.name }
        return fonts to replaced.flatMap { it.files }.map { it.name }.filter { it !in used }
    }
}
