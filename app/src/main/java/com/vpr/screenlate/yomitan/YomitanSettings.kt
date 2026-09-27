package com.vpr.screenlate.yomitan

import com.vpr.screenlate.core.anki.audio.AudioSource
import com.vpr.screenlate.core.anki.audio.AudioSourceType
import com.vpr.screenlate.core.anki.settings.DuplicateBehavior
import com.vpr.screenlate.core.anki.settings.DuplicateScope
import com.vpr.screenlate.core.anki.settings.OverwriteMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** The parts of a Yomitan settings export ("Export Settings") that Screenlate can use. */
data class YomitanSettings(val profiles: List<Profile>, val currentProfile: Int) {

    data class Profile(
        val name: String,
        /** In Yomitan's priority order. */
        val dictionaries: List<Dictionary>,
        val sortFrequencyDictionary: String?,
        val anki: Anki?,
        val audio: Audio?,
        val scanLength: Int?,
        val maxResults: Int?,
        /** Text replacement rules of the profile; not imported, Japanese lookups have the common ones built in. */
        val replacementRules: Int,
        val fontSize: Int?,
        val fontFamily: String?,
        val customPopupCss: String?,
    )

    data class Dictionary(val name: String, val enabled: Boolean)

    /** A note type with its field templates. */
    data class CardFormat(
        val deck: String?,
        val model: String?,
        val fields: Map<String, String>,
        val overwriteModes: Map<String, OverwriteMode>,
    )

    /**
     * @property main the first term card format; Screenlate uses one note type at a time.
     * @property others further term card formats, kept as saved templates of their note types.
     */
    data class Anki(
        val main: CardFormat?,
        val others: List<CardFormat>,
        val tags: List<String>,
        val duplicateCheck: Boolean?,
        val duplicateScope: DuplicateScope?,
        val duplicateAllModels: Boolean?,
        val duplicateBehavior: DuplicateBehavior?,
    )

    /** @property unknown source types Screenlate does not have. */
    data class Audio(
        val enabled: Boolean,
        val sources: List<AudioSource>,
        val unknown: List<String>,
        val volume: Int?,
        val autoPlay: Boolean?,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** @throws IllegalArgumentException when the text is not a Yomitan settings export. */
        fun parse(text: String): YomitanSettings {
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw IllegalArgumentException("Not JSON")
            val options = root.obj("options") ?: throw IllegalArgumentException("No options")
            val profiles = options.array("profiles")?.mapNotNull { (it as? JsonObject)?.let(::profile) }.orEmpty()
            if (profiles.isEmpty()) throw IllegalArgumentException("No profiles")
            val current = options.int("profileCurrent")?.coerceIn(profiles.indices) ?: 0
            return YomitanSettings(profiles, current)
        }

        private fun profile(profile: JsonObject): Profile {
            val options = profile.obj("options") ?: JsonObject(emptyMap())
            val general = options.obj("general")
            val replacements = options.obj("translation")?.obj("textReplacements")
            return Profile(
                name = profile.string("name").orEmpty(),
                dictionaries = options.array("dictionaries").orEmpty().mapNotNull { item ->
                    val entry = item as? JsonObject ?: return@mapNotNull null
                    val name = entry.string("name") ?: return@mapNotNull null
                    Dictionary(name, entry.boolean("enabled") ?: true)
                },
                sortFrequencyDictionary = general?.string("sortFrequencyDictionary")?.takeIf { it.isNotBlank() },
                anki = options.obj("anki")?.let(::anki),
                audio = options.obj("audio")?.let(::audio),
                scanLength = options.obj("scanning")?.int("length"),
                maxResults = general?.int("maxResults"),
                replacementRules = replacements?.array("groups").orEmpty().sumOf { (it as? JsonArray)?.size ?: 0 },
                fontSize = general?.int("fontSize"),
                fontFamily = general?.string("fontFamily")?.trim()?.takeIf { it.isNotEmpty() },
                customPopupCss = general?.string("customPopupCss")?.takeIf { it.isNotBlank() },
            )
        }

        private fun anki(anki: JsonObject): Anki {
            // Yomitan 25+ has a list of card formats; older versions one "terms" format.
            val formats = anki.array("cardFormats")
                ?.mapNotNull { it as? JsonObject }
                ?.filter { (it.string("type") ?: "term") == "term" }
                ?: listOfNotNull(anki.obj("terms"))
            val cardFormats = formats.map(::cardFormat)
            return Anki(
                main = cardFormats.firstOrNull(),
                others = cardFormats.drop(1),
                tags = anki.array("tags").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                duplicateCheck = anki.boolean("checkForDuplicates"),
                duplicateScope = when (anki.string("duplicateScope")) {
                    "collection" -> DuplicateScope.COLLECTION
                    "deck" -> DuplicateScope.DECK
                    "deck-root" -> DuplicateScope.DECK_ROOT
                    else -> null
                },
                duplicateAllModels = anki.boolean("duplicateScopeCheckAllModels"),
                duplicateBehavior = when (anki.string("duplicateBehavior")) {
                    "prevent" -> DuplicateBehavior.PREVENT
                    "overwrite" -> DuplicateBehavior.OVERWRITE
                    "new" -> DuplicateBehavior.NEW
                    else -> null
                },
            )
        }

        private fun cardFormat(format: JsonObject): CardFormat {
            val fields = mutableMapOf<String, String>()
            val modes = mutableMapOf<String, OverwriteMode>()
            format.obj("fields")?.forEach { (name, value) ->
                when (value) {
                    is JsonPrimitive -> fields[name] = value.contentOrNull.orEmpty()
                    is JsonObject -> {
                        fields[name] = value.string("value").orEmpty()
                        overwriteMode(value.string("overwriteMode"))?.let { modes[name] = it }
                    }
                    else -> Unit
                }
            }
            return CardFormat(format.string("deck"), format.string("model"), fields, modes)
        }

        private fun overwriteMode(name: String?): OverwriteMode? = when (name) {
            "coalesce" -> OverwriteMode.COALESCE
            "coalesce-new" -> OverwriteMode.COALESCE_NEW
            "overwrite" -> OverwriteMode.OVERWRITE
            "skip" -> OverwriteMode.SKIP
            "append" -> OverwriteMode.APPEND
            "prepend" -> OverwriteMode.PREPEND
            else -> null
        }

        private fun audio(audio: JsonObject): Audio {
            val sources = mutableListOf<AudioSource>()
            val unknown = mutableListOf<String>()
            audio.array("sources").orEmpty().mapNotNull { it as? JsonObject }.forEach { source ->
                val type = source.string("type").orEmpty()
                when (val mapped = audioSourceType(type)) {
                    null -> unknown += type
                    else -> sources += AudioSource(mapped, if (mapped.hasUrl) source.string("url").orEmpty() else "")
                }
            }
            return Audio(
                enabled = audio.boolean("enabled") ?: true,
                sources = sources.distinct(),
                unknown = unknown,
                volume = audio.int("volume"),
                autoPlay = audio.boolean("autoPlay"),
            )
        }

        private fun audioSourceType(type: String): AudioSourceType? = when (type) {
            "jpod101", "jpod101-alternate" -> AudioSourceType.JAPANESE_POD_101
            "language-pod-101" -> AudioSourceType.LANGUAGE_POD_101
            "jisho" -> AudioSourceType.JISHO
            "lingua-libre" -> AudioSourceType.LINGUA_LIBRE
            "wiktionary" -> AudioSourceType.WIKTIONARY
            "text-to-speech", "text-to-speech-reading" -> AudioSourceType.TEXT_TO_SPEECH
            "custom" -> AudioSourceType.URL
            "custom-json" -> AudioSourceType.CUSTOM_JSON
            else -> null
        }

        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
        private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
        private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
        private fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull
        private fun JsonObject.int(key: String): Int? = primitive(key)?.intOrNull
        private fun JsonObject.boolean(key: String): Boolean? = primitive(key)?.booleanOrNull
    }
}
