package com.example.offlinegpt.ui.chat

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.engine.LiteRTLMEngine
import com.example.offlinegpt.data.local.ChatMessage
import com.example.offlinegpt.data.local.ChatSession
import com.example.offlinegpt.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val liteRTLMEngine = LiteRTLMEngine()
    private val userEmail: String
        get() = auth.currentUser?.email ?: "anonymous"

    val sessions: StateFlow<List<ChatSession>> = repository.getAllSessions(userEmail)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentStreamingText = MutableStateFlow<String?>(null)
    val currentStreamingText: StateFlow<String?> = _currentStreamingText.asStateFlow()

    private val _currentSessionId = mutableStateOf<Long?>(null)
    val currentSessionId: State<Long?> = _currentSessionId

    fun downloadGemma4Model() {
        val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("Downloading Gemma 4 Model")
            .setDescription("Downloading on-device AI weights...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "gemma-4-E2B-it.litertlm")
            .setAllowedOverMetered(false) // Require Wi-Fi by default for large files

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
    
    fun selectSession(sessionId: Long) {

        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "gemma-4-E2B-it.litertlm"
        )

        _currentSessionId.value = sessionId
        viewModelScope.launch {
            if (modelFile.exists()) {
                // Pass to your LiteRTLMEngine instance
                liteRTLMEngine.initialize(modelFile.absolutePath)
            }
            repository.getMessagesForSession(sessionId).collect {
                _messages.value = it
            }
        }
    }

    fun createNewSession(title: String) {
       // downloadGemma4Model()
        viewModelScope.launch {
            val id = repository.createSession(userEmail, title)
            selectSession(id)
        }
    }

    fun sendMessage(content: String) {
        val sessionId = _currentSessionId.value ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            try {
                // 1. Save user message (DB write)
                repository.sendMessage(sessionId, content, isUser = true)

                val responseBuilder = StringBuilder()
                _currentStreamingText.value = ""

                // 2. Stream AI response from LiteRT
                // We move the collection to Dispatchers.Default to ensure the native 
                // 'callback_thread_pool' is not hindered by UI thread contention.
                withContext(Dispatchers.Default) {
                    liteRTLMEngine.sendMessageStream(content)
                        .catch { error ->
                            _currentStreamingText.value = "Error: ${error.localizedMessage}"
                        }
                        .collect { chunk ->
                            val text = chunk.toString()
                            responseBuilder.append(text)
                            // Update UI on Main thread
                            _currentStreamingText.value = responseBuilder.toString()
                        }
                }

                // 3. Save the full AI response to database
                val finalResponse = responseBuilder.toString()
                if (finalResponse.isNotEmpty()) {
                    repository.sendMessage(sessionId, finalResponse, isUser = false)
                }
            } catch (e: Exception) {
                _currentStreamingText.value = "Error: ${e.localizedMessage}"
            } finally {
                _currentStreamingText.value = null
            }
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
