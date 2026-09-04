package com.example.offlinegpt.ui.chat

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.engine.LiteRTLMEngine
import com.example.offlinegpt.data.location.LocationProvider
import com.example.offlinegpt.data.local.ChatMessage
import com.example.offlinegpt.data.local.ChatSession
import com.example.offlinegpt.data.repository.ChatRepository
import com.example.offlinegpt.util.tts.TextToSpeechManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val auth: FirebaseAuth,
    private val locationProvider: LocationProvider,
    private val ttsManager: TextToSpeechManager,
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

    private val _aiLocationInfo = MutableStateFlow<String?>(null)
    val aiLocationInfo: StateFlow<String?> = _aiLocationInfo.asStateFlow()

    fun downloadModelFile(): Long? {

        val fileName = "all-MiniLM-L6-v2-quant.tflite"
        val targetFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (targetFile.exists() && targetFile.length() > 0) {
            return null
        }
        val modelUrl = "https://huggingface.co/Nihal2000/all-MiniLM-L6-v2-quant.tflite/resolve/main/${fileName}"

        Log.d("Model Download", "Model URL: $modelUrl")

        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("Downloading Gemma Embedding Model")
            .setDescription("Downloading on-device AI Embedding weights...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)

    }
    fun downloadGemma4Model(): Long? {
        // 1. Verify Wi-Fi availability before initiating download
        if (!isWifiConnected()) {
            _currentStreamingText.value = "Error: Wi-Fi is required for model download."
            return null
        }

        val fileName = "gemma-4-E2B-it.litertlm"
        val targetFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (targetFile.exists() && targetFile.length() > 0) {
            return null
        }

        val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$fileName"

        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("Downloading Gemma Model")
            .setDescription("Downloading on-device AI weights...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun checkIfEmbeddingFileExist(): Boolean {
        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "all-MiniLM-L6-v2-quant.tflite"
        )
        return modelFile.exists()
    }

    // create function to check if file gemma model exist
    fun checkIfGemmaModelExist(): Boolean {
        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "gemma-4-E2B-it.litertlm"
        )
        return modelFile.exists()
    }

    
    fun selectSession(sessionId: Long) {

        // check if file exist

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
        viewModelScope.launch {
            val id = repository.createSession(userEmail, title)
            selectSession(id)
        }
    }

    // getCurrentResponse
    suspend fun getCurrentResponse(prompt: String): String {
        // Ensure engine is initialized
        if (!liteRTLMEngine.isInitialized()) {
            val modelFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "gemma-4-E2B-it.litertlm"
            )
            if (modelFile.exists()) {
                liteRTLMEngine.initialize(modelFile.absolutePath)
            } else {
                return "Error: Gemma model not found. Please download it first."
            }
        }

        val responseBuilder = StringBuilder()
        try {
            withContext(Dispatchers.Default) {
                liteRTLMEngine.sendMessageStream(prompt)
                    .catch { error ->
                        responseBuilder.append("Error: ${error.localizedMessage}")
                    }
                    .collect { chunk ->
                        responseBuilder.append(chunk.toString())
                    }
            }
        } catch (e: Exception) {
            return "Error: ${e.localizedMessage}"
        }
        
        return responseBuilder.toString()
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

    fun askWhereAmI(compassHeading: String) {
        val location = locationProvider.getCurrentLocation()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        
        val prompt = if (location != null) {
            val address = locationProvider.getAddressFromLocation(location)
            "I am currently facing $compassHeading. The current time is $time, and my location is $address. Based on this, please describe where I am, tell me the exact time, and explain where the sun is likely positioned in the sky relative to my current position."
        } else {
            "I am currently facing $compassHeading. The current time is $time. I couldn't retrieve my precise location. Based on this, please tell me what time it is, let me know that my location is unavailable, and mention where the sun would typically be at this time of day."
        }

        viewModelScope.launch {
            Log.d("Location Info prompt", prompt)
            _aiLocationInfo.value = "Thinking..."
            val locationInfo = getCurrentResponse(prompt)
            _aiLocationInfo.value = locationInfo
            ttsManager.speak(locationInfo)
            Log.d("Location Info", locationInfo)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
