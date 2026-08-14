package com.example.offlinegpt.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlin.math.sqrt

@Dao
interface ContextDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ContextEntity>)

    @Query("SELECT * FROM rag_contexts")
    suspend fun getAllContexts(): List<ContextEntity>
}

// Math extension for vector similarity matching
fun FloatArray.cosineSimilarity(other: FloatArray): Float {
    var dotProduct = 0.0f
    var normA = 0.0f
    var normB = 0.0f
    for (i in indices) {
        dotProduct += this[i] * other[i]
        normA += this[i] * this[i]
        normB += other[i] * other[i]
    }
    return if (normA > 0 && normB > 0) dotProduct / (sqrt(normA) * sqrt(normB)) else 0.0f
}