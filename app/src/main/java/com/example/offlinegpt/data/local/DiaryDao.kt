package com.example.offlinegpt.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<DiaryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DiaryEntry): Long

    @Query("DELETE FROM diary_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)
}

