package com.example.offlinegpt.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ChatSession::class,
        ChatMessage::class,
        Document::class,
        DocumentPage::class,
        MBTIResult::class,
        FavoritePlaceEntity::class,
        DiaryEntry::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun ragDao(): RagDao
    abstract fun mbtiDao(): MbtiDao
    abstract fun favoritePlaceDao(): FavoritePlaceDao
    abstract fun diaryDao(): DiaryDao
}
