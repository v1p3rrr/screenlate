package com.vpr.screenlate.dictionaries

import com.vpr.screenlate.dictionary.api.catalog.CatalogEntry
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity

/**
 * Where a dictionary can be found outside the app, for users who want it elsewhere: its website or repository and a
 * direct download. The dictionary's own index.json comes first, the catalog fills what it lacks; only web links count.
 */
data class DictionaryLinks(val website: String?, val download: String?) {
    val isEmpty: Boolean get() = website == null && download == null

    companion object {
        fun of(dictionary: DictionaryEntity, catalogEntry: CatalogEntry?): DictionaryLinks {
            val website = web(dictionary.url) ?: web(catalogEntry?.homepage)
            val download = (web(dictionary.downloadUrl) ?: web(catalogEntry?.downloadUrl)).takeIf { it != website }
            return DictionaryLinks(website, download)
        }

        private fun web(url: String?): String? = url?.trim()?.takeIf {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
    }
}
