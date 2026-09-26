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

    @Query("SELECT * FROM dictionaries WHERE id = :id")
    suspend fun get(id: Long): DictionaryEntity?

    @Query("SELECT * FROM dictionaries WHERE title = :title")
    suspend fun findByTitle(title: String): DictionaryEntity?

    @Query("SELECT COALESCE(MAX(priority), -1) FROM dictionaries")
    suspend fun maxPriority(): Int

    @Insert
    suspend fun insert(dictionary: DictionaryEntity): Long

    @Update
    suspend fun update(dictionary: DictionaryEntity)

    @Update
    suspend fun update(dictionaries: List<DictionaryEntity>)

    @Delete
    suspend fun delete(dictionary: DictionaryEntity)
}
