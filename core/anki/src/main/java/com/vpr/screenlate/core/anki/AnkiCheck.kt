package com.vpr.screenlate.core.anki

/** How one item of a connection check came out. */
enum class CheckOutcome {
    PASSED,
    FAILED,

    /** Nothing is chosen in the settings yet. */
    NOT_CHOSEN,

    /** AnkiDroid gave an empty answer, which it does when it cannot be asked right now. */
    NO_ANSWER,

    /** An item this one needs failed, so it could not be asked. */
    NOT_CHECKED,
}

/**
 * The result of [AnkiNotes.check], item by item. [deckName] and [modelName] are the chosen deck and note type, named
 * as AnkiDroid names them now or, when it no longer has them, as saved. [missingFields] are fields of the saved
 * templates the note type no longer has; [newFields] are fields of the note type that have no template yet.
 */
data class AnkiCheck(
    val installed: CheckOutcome,
    val access: CheckOutcome = CheckOutcome.NOT_CHECKED,
    val deck: CheckOutcome = CheckOutcome.NOT_CHECKED,
    val model: CheckOutcome = CheckOutcome.NOT_CHECKED,
    val fields: CheckOutcome = CheckOutcome.NOT_CHECKED,
    val deckName: String? = null,
    val modelName: String? = null,
    val missingFields: List<String> = emptyList(),
    val newFields: List<String> = emptyList(),
)
