package com.vpr.screenlate.dictionary.api.registry

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionaries ORDER BY priority, id")
    fun observeAll(): Flow<List<DictionaryEntity>>

    @Query("SELECT * FROM dictionaries ORDER BY priority, id")
    suspend fun getAll(): List<DictionaryEntity>

    @Insert
    suspend fun insert(dictionary: DictionaryEntity): Long

    @Update
    suspend fun update(dictionaries: List<DictionaryEntity>)

    @Delete
    suspend fun delete(dictionary: DictionaryEntity)
}
