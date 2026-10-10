package com.example.offlinegpt.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mbti_results")
data class MBTIResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userEmail: String,
    val mbtiType: String, // e.g., "INTJ"
    val timestamp: Long = System.currentTimeMillis(),
    
    // Raw scores for each trait (percentage or raw sum)
    val extroversionScore: Int,
    val introversionScore: Int,
    val sensingScore: Int,
    val intuitionScore: Int,
    val thinkingScore: Int,
    val feelingScore: Int,
    val judgingScore: Int,
    val perceivingScore: Int
)
