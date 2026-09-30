package com.vpr.screenlate.overlay

/** Extras the overlay puts on intents that open the app. */
object OverlayIntents {
    /** Which screen the app should open; see the `OPEN_*` values. */
    const val EXTRA_OPEN = "open"

    /** The Anki settings, e.g. from the grey ➕ of a broken Anki setup. */
    const val OPEN_ANKI_SETTINGS = "anki"
    const val OPEN_DICTIONARIES = "dictionaries"
}
