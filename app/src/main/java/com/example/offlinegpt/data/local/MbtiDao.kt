package com.example.offlinegpt.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MbtiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: MBTIResult)

    @Query("SELECT * FROM mbti_results WHERE userEmail = :userEmail ORDER BY timestamp DESC LIMIT 1")
    fun getLatestResult(userEmail: String): Flow<MBTIResult?>

    @Query("SELECT * FROM mbti_results WHERE userEmail = :userEmail ORDER BY timestamp DESC")
    fun getAllResults(userEmail: String): Flow<List<MBTIResult>>
}
