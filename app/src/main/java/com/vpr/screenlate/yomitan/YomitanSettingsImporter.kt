package com.vpr.screenlate.yomitan

import android.util.Log
import com.vpr.screenlate.dictionary.api.registry.dictionaryKey
import com.vpr.screenlate.core.anki.AnkiAvailability
import com.vpr.screenlate.core.anki.AnkiDroid
import com.vpr.screenlate.core.anki.AnkiNotes
import com.vpr.screenlate.core.anki.audio.AudioSettingsRepository
import com.vpr.screenlate.core.anki.settings.AnkiSettingsRepository
import com.vpr.screenlate.core.anki.settings.NoteTemplate
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.dictionary.api.registry.DictionaryRepository
import com.vpr.screenlate.dictionary.api.registry.keptTermDictionaries
import com.vpr.screenlate.dictionary.api.settings.LookupSettings
import com.vpr.screenlate.dictionary.api.settings.LookupSettingsRepository
import com.vpr.screenlate.overlay.fonts.PopupFonts
import com.vpr.screenlate.overlay.settings.PopupAppearance
import com.vpr.screenlate.overlay.settings.PopupAppearanceRepository
import com.vpr.screenlate.settings.PopupAppearanceViewModel
import javax.inject.Inject

enum class YomitanSection { DICTIONARIES, ANKI, AUDIO, LOOKUP, APPEARANCE }

/** What an import applied and what it had to skip, per section. */
/** @property language the language whose settings were changed, named when several are turned on or it is not the active one. */
data class ImportSummary(
    val language: Language? = null,
    val dictionaries: DictionaryOutcome? = null,
    val anki: AnkiOutcome? = null,
    val audio: AudioOutcome? = null,
    val lookup: LookupOutcome? = null,
    val appearance: AppearanceOutcome? = null,
)

/**
 * @property missing dictionaries of the profile that are not installed here.
 * @property sortMissing the sort frequency dictionary when it is not installed.
 * @property keptOn dictionaries the profile switches off that stay on, as a language would have none with definitions.
 */
data class DictionaryOutcome(
    val matched: Int,
    val missing: List<String>,
    val sortDictionary: String?,
    val sortMissing: String?,
    val keptOn: List<String> = emptyList(),
)

/**
 * @property unavailable AnkiDroid could not be asked; the templates were kept for when the note type is chosen.
 * @property droppedFields fields of the Yomitan template that the note type does not have.
 * @property savedModels other card formats, kept as templates of their note types.
 */
data class AnkiOutcome(
    val deck: String?,
    val deckMissing: String?,
    val model: String?,
    val modelMissing: String?,
    val unavailable: Boolean,
    val droppedFields: List<String>,
    val savedModels: List<String>,
)

data class AudioOutcome(val sources: Int, val unknown: List<String>, val disabledInYomitan: Boolean)

/** @property skippedReplacements text replacement rules of the profile that were not imported. */
data class LookupOutcome(val scanLength: Int?, val maxResults: Int?, val skippedReplacements: Int)

/**
 * @property cssLines lines of the imported custom popup CSS; 0 when the profile has none.
 * @property cssIssues syntax problems and missing fonts in that CSS.
 * @property fontFamily Yomitan's font family, which is not imported (desktop fonts are not on the phone).
 */
data class AppearanceOutcome(val fontSize: Int?, val cssLines: Int, val cssIssues: Int, val fontFamily: String?)

/** Applies one profile of a Yomitan settings export; every applied setting stays editable in the app. */
class YomitanSettingsImporter @Inject constructor(
    private val dictionaries: DictionaryRepository,
    private val lookupSettings: LookupSettingsRepository,
    private val ankiSettings: AnkiSettingsRepository,
    private val ankiDroid: AnkiDroid,
    private val notes: AnkiNotes,
    private val audioSettings: AudioSettingsRepository,
    private val appearance: PopupAppearanceRepository,
    private val fonts: PopupFonts,
    private val profiles: LanguageProfiles,
) {
    /**
     * Applies [sections] of [profile] to the settings of the profile's language; a profile without one, or with a
     * language Screenlate does not support, goes to the active language.
     */
    suspend fun apply(profile: YomitanSettings.Profile, sections: Set<YomitanSection>): ImportSummary {
        val state = profiles.current()
        val language = Language.of(profile.language) ?: state.active
        return apply(profile, sections, language).copy(language = language.takeIf { state.several || it != state.active })
    }

    private suspend fun apply(profile: YomitanSettings.Profile, sections: Set<YomitanSection>, language: Language) = ImportSummary(
        dictionaries = if (YomitanSection.DICTIONARIES in sections) applyDictionaries(profile, language) else null,
        anki = if (YomitanSection.ANKI in sections) profile.anki?.let { applyAnki(it, language) } else null,
        audio = if (YomitanSection.AUDIO in sections) profile.audio?.let { applyAudio(it, language) } else null,
        lookup = if (YomitanSection.LOOKUP in sections) applyLookup(profile, language) else null,
        appearance = if (YomitanSection.APPEARANCE in sections) applyAppearance(profile, language) else null,
    ).also { summary ->
        Log.i(
            TAG,
            "Yomitan settings imported: ${sections.joinToString()}; dictionaries ${summary.dictionaries?.matched ?: "-"} matched, " +
                "${summary.dictionaries?.missing?.size ?: "-"} missing",
        )
    }

    /** Text size and [language]'s custom CSS; an empty custom CSS in the profile keeps the current one. */
    private suspend fun applyAppearance(profile: YomitanSettings.Profile, language: Language): AppearanceOutcome {
        profile.fontSize?.let { appearance.setFontSize(it) }
        val css = profile.customPopupCss
        css?.let { appearance.setCustomCss(language, it) }
        return AppearanceOutcome(
            fontSize = profile.fontSize?.coerceIn(PopupAppearance.MIN_FONT_SIZE, PopupAppearance.MAX_FONT_SIZE),
            cssLines = css?.lines()?.count { it.isNotBlank() } ?: 0,
            cssIssues = css?.let { PopupAppearanceViewModel.cssIssues(it, language, fonts.installed.value).size } ?: 0,
            fontFamily = profile.fontFamily,
        )
    }

    /**
     * Yomitan's order first for the dictionaries installed here, the others after them in their current order; the
     * sort dictionary becomes [language]'s.
     */
    private suspend fun applyDictionaries(profile: YomitanSettings.Profile, language: Language): DictionaryOutcome {
        val installed = dictionaries.getAll().sortedBy { it.priority }
        val byKey = installed.groupBy { dictionaryKey(it.title) }
        val matched = profile.dictionaries.mapNotNull { preference ->
            val dictionary = installed.firstOrNull { it.title == preference.name }
                ?: byKey[dictionaryKey(preference.name)]?.firstOrNull()
            dictionary?.let { it to preference }
        }.distinctBy { it.first.id }
        val missing = profile.dictionaries.map { it.name }.filter { name -> matched.none { it.second.name == name } }
        val ordered = matched.map { it.first } + installed.filter { dictionary -> matched.none { it.first.id == dictionary.id } }
        val switches = matched.associate { (dictionary, preference) -> dictionary.id to preference.enabled }
        val withoutFiles = dictionaries.missingFiles().mapTo(hashSetOf()) { it.id }
        val switched = ordered.map { it.copy(enabled = switches[it.id] ?: it.enabled) }
        val kept = keptTermDictionaries(installed, switched, profiles.current().turnedOn) { it.id !in withoutFiles }
        dictionaries.reorder(ordered.map { it.id }, enabled = switches + kept.associate { it.id to true })
        val sortName = profile.sortFrequencyDictionary
        val sort = sortName?.let { name ->
            installed.filter { it.frequencyCount > 0 }
                .firstOrNull { it.title == name || dictionaryKey(it.title) == dictionaryKey(name) }
        }
        sort?.let { dictionaries.setSortDictionary(language, it.id) }
        return DictionaryOutcome(
            matched = matched.size,
            missing = missing,
            sortDictionary = sort?.title,
            sortMissing = sortName.takeIf { sort == null },
            keptOn = kept.map { it.title },
        )
    }

    private suspend fun applyAnki(anki: YomitanSettings.Anki, language: Language): AnkiOutcome {
        val main = anki.main
        val available = ankiDroid.availability() == AnkiAvailability.READY
        val deck = if (available) ankiDroid.decks().firstOrNull { it.name == main?.deck } else null
        val model = if (available) ankiDroid.models().firstOrNull { it.name == main?.model } else null
        val modelFields = model?.let { ankiDroid.fields(it.id) }.orEmpty()
        ankiSettings.update(language) { settings ->
            var saved = settings.savedTemplates
            settings.modelName?.let { saved = saved + (it to NoteTemplate(settings.fields, settings.overwriteModes)) }
            anki.others.forEach { format ->
                format.model?.let { saved = saved + (it to NoteTemplate(format.fields, format.overwriteModes)) }
            }
            var next = settings.copy(
                tags = anki.tags.joinToString(" ").ifBlank { settings.tags },
                duplicateCheck = anki.duplicateCheck ?: settings.duplicateCheck,
                duplicateScope = anki.duplicateScope ?: settings.duplicateScope,
                duplicateAllModels = anki.duplicateAllModels ?: settings.duplicateAllModels,
                duplicateBehavior = anki.duplicateBehavior ?: settings.duplicateBehavior,
            )
            if (main?.model != null) {
                if (model != null && modelFields.isNotEmpty()) {
                    next = next.copy(
                        modelId = model.id,
                        modelName = model.name,
                        fields = modelFields.associateWith { main.fields[it].orEmpty() },
                        overwriteModes = main.overwriteModes.filterKeys { it in modelFields },
                    )
                    saved = saved - model.name
                } else {
                    // Chosen later in the Anki settings, the note type gets these templates back.
                    saved = saved + (main.model to NoteTemplate(main.fields, main.overwriteModes))
                }
            }
            if (deck != null) next = next.copy(deckId = deck.id, deckName = deck.name)
            next.copy(savedTemplates = saved)
        }
        notes.invalidate()
        return AnkiOutcome(
            deck = deck?.name,
            deckMissing = main?.deck.takeIf { deck == null },
            model = model?.name,
            modelMissing = main?.model.takeIf { model == null },
            unavailable = !available,
            droppedFields = if (model != null) main?.fields.orEmpty().keys.filter { it !in modelFields } else emptyList(),
            savedModels = anki.others.mapNotNull { it.model },
        )
    }

    private suspend fun applyAudio(audio: YomitanSettings.Audio, language: Language): AudioOutcome {
        audioSettings.update(language) { settings ->
            settings.copy(
                sources = if (audio.enabled) audio.sources else emptyList(),
                volume = audio.volume?.coerceIn(0, 100) ?: settings.volume,
                autoPlay = audio.autoPlay ?: settings.autoPlay,
            )
        }
        return AudioOutcome(audio.sources.size, audio.unknown, disabledInYomitan = !audio.enabled)
    }

    private suspend fun applyLookup(profile: YomitanSettings.Profile, language: Language): LookupOutcome {
        profile.scanLength?.let { lookupSettings.setScanLength(language, it) }
        profile.maxResults?.let { lookupSettings.setMaxResults(it.coerceAtLeast(1)) }
        return LookupOutcome(
            scanLength = profile.scanLength?.coerceIn(LookupSettings.MIN_SCAN_LENGTH, LookupSettings.MAX_SCAN_LENGTH),
            maxResults = profile.maxResults,
            skippedReplacements = profile.replacementRules,
        )
    }
}

private const val TAG = "YomitanImport"
