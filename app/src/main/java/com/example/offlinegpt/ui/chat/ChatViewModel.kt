package com.example.offlinegpt.ui.chat

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.local.ChatMessage
import com.example.offlinegpt.data.local.ChatSession
import com.example.offlinegpt.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val userEmail: String
        get() = auth.currentUser?.email ?: "anonymous"

    val sessions: StateFlow<List<ChatSession>> = repository.getAllSessions(userEmail)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentSessionId = mutableStateOf<Long?>(null)
    val currentSessionId: State<Long?> = _currentSessionId

    fun selectSession(sessionId: Long) {
        _currentSessionId.value = sessionId
        viewModelScope.launch {
            repository.getMessagesForSession(sessionId).collect {
                _messages.value = it
            }
        }
    }

    fun createNewSession(title: String) {
        viewModelScope.launch {
            val id = repository.createSession(userEmail, title)
            selectSession(id)
        }
    }

    fun sendMessage(content: String) {
        val sessionId = _currentSessionId.value ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            // Save user message
            repository.sendMessage(sessionId, content, isUser = true)
            
            // Mocking bot response for now
            repository.sendMessage(sessionId, "Echo: $content", isUser = false)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
                _messages.value = emptyList()
            }
        }
    }
}
