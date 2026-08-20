package com.example.offlinegpt.di

import android.content.Context
import androidx.room.Room
import com.example.offlinegpt.data.local.AppDatabase
import com.example.offlinegpt.data.local.ChatDao
import com.example.offlinegpt.data.local.ContextDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "offlinegpt_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    fun provideContextDao(database: AppDatabase): ContextDao {
        return database.contextDao()
    }
}
