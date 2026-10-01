package com.vpr.screenlate.anki

import javax.inject.Inject
import javax.inject.Singleton

/**
 * What AnkiDroid answered last while the app runs: the Anki screen opens with it, dimmed until AnkiDroid answers
 * again, instead of an empty screen that fills a moment later.
 */
@Singleton
class AnkiConnectionCache @Inject constructor() {
    @Volatile
    var last: AnkiScreenState? = null
}
