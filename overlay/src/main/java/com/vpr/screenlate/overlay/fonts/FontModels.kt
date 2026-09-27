package com.vpr.screenlate.overlay.fonts

import kotlinx.serialization.Serializable

/**
 * A font file in the app's font folder.
 *
 * @param weight CSS `font-weight` it covers: "400", "700", or a range such as "100 900" for variable fonts.
 */
@Serializable
data class FontFile(val name: String, val weight: String = "400")

/**
 * A font the user downloaded from the catalog or added from a file.
 *
 * @param family the CSS family name, also usable in custom CSS.
 * @param catalogId the catalog entry it was downloaded from; null for the user's own files.
 */
@Serializable
data class InstalledFont(
    val id: String,
    val family: String,
    val files: List<FontFile>,
    val catalogId: String? = null,
)

@Serializable
data class CatalogFontFile(val url: String, val weight: String = "400")

/** A freely licensed font offered for download. */
@Serializable
data class CatalogFont(
    val id: String,
    val family: String,
    val language: String,
    val description: Map<String, String> = emptyMap(),
    val files: List<CatalogFontFile>,
    val sizeMb: Double,
    val license: String,
    val homepage: String,
) {
    fun description(languageCode: String): String = description[languageCode] ?: description["en"].orEmpty()
}

@Serializable
data class FontCatalog(val fonts: List<CatalogFont>)
