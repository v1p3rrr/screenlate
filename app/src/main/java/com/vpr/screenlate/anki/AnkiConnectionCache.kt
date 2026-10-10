package com.vpr.screenlate.anki

import com.vpr.screenlate.core.common.Language
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What AnkiDroid answered last for each language's setup while the app runs: the Anki screen opens with it, dimmed
 * until AnkiDroid answers again, instead of an empty screen that fills a moment later.
 */
@Singleton
class AnkiConnectionCache @Inject constructor() {
    val last = ConcurrentHashMap<Language, AnkiScreenState>()
}
