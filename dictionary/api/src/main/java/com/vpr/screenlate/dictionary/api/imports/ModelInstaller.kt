package com.vpr.screenlate.dictionary.api.imports

import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import java.io.File

/**
 * Installs a downloaded on-device recognition model ([CatalogEntry.isModel]). The model store belongs to OCR, outside
 * the dictionary modules, so the app binds it.
 */
interface ModelInstaller {
    /** Checks [archive] against [entry]'s checksum and installs it; the caller deletes [archive] afterwards. */
    suspend fun install(entry: CatalogEntry, archive: File)
}
