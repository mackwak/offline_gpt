package com.example.offlinegpt.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ChatSession::class, ChatMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
