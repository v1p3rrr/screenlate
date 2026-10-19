package com.vpr.screenlate.backup

import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.common.settings.LanguageProfilesState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Which languages a backup holds and what saving or restoring some of them does to the turned-on languages. */
object BackupLanguages {
    /** [state] limited to the [chosen] languages, as a backup holds it; null when no turned-on language is chosen. */
    fun limited(state: LanguageProfilesState, chosen: Set<Language>): LanguageProfilesState? {
        val turnedOn = state.turnedOn.filter { it in chosen }
        if (turnedOn.isEmpty()) return null
        return LanguageProfilesState(
            turnedOn = turnedOn,
            active = state.active.takeIf { it in chosen } ?: turnedOn.first(),
            starred = state.starred.filter { it in chosen },
            flags = state.flags.filterKeys { it in chosen },
        )
    }

    /**
     * The turned-on languages after restoring the [chosen] ones from a backup whose own are [backup] (null when it has
     * none): [current] (null on a first run not answered yet) with [chosen] added after its languages. The backup's
     * active language, stars and flags apply to [chosen] only; every other language keeps its own.
     */
    fun merged(current: LanguageProfilesState?, backup: LanguageProfilesState?, chosen: Collection<Language>): LanguageProfilesState {
        val added = backup?.turnedOn.orEmpty().filter { it in chosen } + chosen
        val turnedOn = (current?.turnedOn.orEmpty() + added).distinct()
        val active = backup?.active?.takeIf { it in chosen } ?: current?.active ?: turnedOn.first()
        val starred = current?.starred.orEmpty().filter { it !in chosen } + backup?.starred.orEmpty().filter { it in chosen }
        return LanguageProfilesState(
            turnedOn = turnedOn,
            active = active,
            starred = turnedOn.filter { it in starred && it != active },
            flags = current?.flags.orEmpty().filterKeys { it !in chosen } + backup?.flags.orEmpty().filterKeys { it in chosen },
        )
    }

    /**
     * The languages a backup holds, which this version supports: those of its manifest; for a backup of an older
     * version, its turned-on languages (Japanese alone without them) and the languages of its [files] (dictionaries,
     * fonts).
     */
    fun of(manifest: BackupManifest, settings: JsonObject, files: List<String?>): List<Language> {
        manifest.languages?.let { codes -> return codes.mapNotNull { Language.of(it) }.distinct() }
        val turnedOn = runCatching {
            settings[LanguageProfiles.TURNED_ON.name]?.jsonObject?.get("value")?.jsonPrimitive?.content
        }.getOrNull()?.split(',')?.mapNotNull { Language.of(it.trim()) }.orEmpty().ifEmpty { listOf(Language.JAPANESE) }
        return (turnedOn + files.flatMap { Language.of(it)?.let(::listOf) ?: Language.withDictionaries(it) }).distinct()
    }
}
