package com.example.offlinegpt.data.repository

import com.example.offlinegpt.data.local.ChatDao
import com.example.offlinegpt.data.local.ChatMessage
import com.example.offlinegpt.data.local.ChatSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    fun getAllSessions(userEmail: String): Flow<List<ChatSession>> = chatDao.getAllSessions(userEmail)

    suspend fun createSession(userEmail: String, title: String): Long {
        return chatDao.insertSession(ChatSession(userEmail = userEmail, title = title))
    }

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun sendMessage(sessionId: Long, content: String, isUser: Boolean): Long {
        return chatDao.insertMessage(
            ChatMessage(sessionId = sessionId, content = content, isUser = isUser)
        )
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteSession(sessionId)
    }
}
