package com.example.offlinegpt.data.repository

import com.example.offlinegpt.data.local.DiaryDao
import com.example.offlinegpt.data.local.DiaryEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(
    private val diaryDao: DiaryDao
) {
    fun getAllEntries(): Flow<List<DiaryEntry>> = diaryDao.getAllEntries()

    suspend fun insertEntry(title: String, content: String, mood: String) {
        diaryDao.insertEntry(
            DiaryEntry(
                title = title,
                content = content,
                mood = mood
            )
        )
    }

    suspend fun deleteEntry(entryId: Long) {
        diaryDao.deleteEntry(entryId)
    }
}

