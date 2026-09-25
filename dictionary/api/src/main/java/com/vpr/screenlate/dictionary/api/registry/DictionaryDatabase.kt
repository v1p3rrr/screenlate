package com.vpr.screenlate.dictionary.api.registry

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DictionaryEntity::class], version = 1, exportSchema = true)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
}
