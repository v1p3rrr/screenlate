package com.vpr.screenlate.overlay.web

import android.content.Context
import com.vpr.screenlate.dictionary.api.model.LookupResult
import com.vpr.screenlate.overlay.R
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Builds the JSON state that `Popup.render` expects (see the comment at the top of popup.js). */
object PageState {
    /**
     * @param matched length of the matched prefix of [text] in code points.
     * @param pending OCR is still refining the text; shows a spinner.
     * @param engine OCR engine label, empty to hide.
     */
    fun build(
        context: Context,
        dark: Boolean,
        text: String,
        matched: Int,
        results: List<LookupResult>,
        message: String?,
        pending: Boolean = false,
        engine: String = "",
    ): JsonObject = buildJsonObject {
        put("theme", if (dark) "dark" else "light")
        put("pending", pending)
        put("engine", engine)
        putJsonObject("source") {
            put("text", text)
            put("matched", matched)
        }
        put("results", Json.encodeToJsonElement(ListSerializer(LookupResult.serializer()), results))
        message?.let { put("message", it) }
        putJsonObject("labels") {
            put("noResults", context.getString(R.string.overlay_no_results))
            put("addNote", context.getString(R.string.overlay_add_note))
            put("playAudio", context.getString(R.string.overlay_play_audio))
        }
    }

    /** Length of the first result's match in code points, for highlighting. */
    fun matchedLength(results: List<LookupResult>): Int =
        results.firstOrNull()?.matched?.let { it.codePointCount(0, it.length) } ?: 0
}
