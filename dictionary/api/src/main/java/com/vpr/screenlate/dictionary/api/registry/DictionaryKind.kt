package com.vpr.screenlate.dictionary.api.registry

import com.vpr.screenlate.dictionary.api.DictionaryMetadata

enum class DictionaryKind {
    /** Term bank with glossaries. */
    TERM,

    /** Term meta bank with frequency data. */
    FREQUENCY,

    /** Term meta bank with pitch accent data. */
    PITCH,

    /** Kanji bank. */
    KANJI,
    ;

    companion object {
        fun of(metadata: DictionaryMetadata): DictionaryKind = when {
            metadata.termCount > 0 -> TERM
            metadata.frequencyCount > 0 -> FREQUENCY
            metadata.pitchCount > 0 -> PITCH
            metadata.kanjiCount > 0 -> KANJI
            else -> TERM
        }
    }
}
