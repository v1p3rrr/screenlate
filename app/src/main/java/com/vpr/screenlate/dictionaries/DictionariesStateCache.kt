package com.vpr.screenlate.dictionaries

import javax.inject.Inject
import javax.inject.Singleton

/** The Dictionaries screen's last state while the app runs: the screen opens with it and updates in place. */
@Singleton
class DictionariesStateCache @Inject constructor() {
    @Volatile
    var last: DictionariesState? = null
}
