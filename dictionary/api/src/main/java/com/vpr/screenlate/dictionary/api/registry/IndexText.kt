package com.vpr.screenlate.dictionary.api.registry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Text from an archive's index.json as the engine returns it: the string's raw JSON content, so a line break arrives
 * as a backslash and `n`. Decoding it as a JSON string gives the text; anything that does not decode is kept as it is.
 */
fun decodeIndexText(raw: String?): String? {
    if (raw == null || BACKSLASH !in raw) return raw
    return runCatching { Json.parseToJsonElement("\"$raw\"").jsonPrimitive }
        .getOrNull()
        ?.takeIf(JsonPrimitive::isString)
        ?.content
        ?: raw
}

private const val BACKSLASH = '\\'
