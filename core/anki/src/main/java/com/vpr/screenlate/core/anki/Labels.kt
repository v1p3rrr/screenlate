package com.vpr.screenlate.core.anki

import androidx.annotation.StringRes
import com.vpr.screenlate.core.anki.audio.AudioSourceType

/** Display names shared by the app screens and the popup. */
@get:StringRes
val AudioSourceType.label: Int
    get() = when (this) {
        AudioSourceType.JAPANESE_POD_101 -> R.string.audio_source_jpod
        AudioSourceType.LANGUAGE_POD_101 -> R.string.audio_source_languagepod
        AudioSourceType.JISHO -> R.string.audio_source_jisho
        AudioSourceType.LINGUA_LIBRE -> R.string.audio_source_lingualibre
        AudioSourceType.WIKTIONARY -> R.string.audio_source_wiktionary
        AudioSourceType.TEXT_TO_SPEECH -> R.string.audio_source_tts
        AudioSourceType.URL -> R.string.audio_source_url
        AudioSourceType.CUSTOM_JSON -> R.string.audio_source_json
    }

@get:StringRes
val AnkiProblem.message: Int
    get() = when (this) {
        AnkiProblem.NOT_INSTALLED -> R.string.anki_problem_not_installed
        AnkiProblem.NO_PERMISSION -> R.string.anki_problem_no_permission
        AnkiProblem.MODEL_MISSING -> R.string.anki_problem_model_missing
        AnkiProblem.DECK_MISSING -> R.string.anki_problem_deck_missing
        AnkiProblem.FIELDS_CHANGED -> R.string.anki_problem_fields_changed
    }
