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
import com.example.offlinegpt.data.engine.EmbeddingEngine
import com.example.offlinegpt.data.engine.LiteRTLMEngine
import com.example.offlinegpt.data.location.LocationProvider
import com.example.offlinegpt.data.local.ChatMessage
import com.example.offlinegpt.data.local.ChatSession
import com.example.offlinegpt.data.repository.ChatRepository
import com.example.offlinegpt.data.repository.RagRepository
import com.example.offlinegpt.util.tts.TextToSpeechManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
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
    private val ragRepository: RagRepository,
    private val auth: FirebaseAuth,
    private val locationProvider: LocationProvider,
    private val ttsManager: TextToSpeechManager,
    private val embeddingEngine: EmbeddingEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<String>>(emptyList())
    val searchResults: StateFlow<List<String>> = _searchResults

    val liteRTLMEngine = LiteRTLMEngine()
    private val userEmail: String
        get() = auth.currentUser?.email ?: "anonymous"

    val sessions: StateFlow<List<ChatSession>> = repository.getAllSessions(userEmail)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentStreamingText = MutableStateFlow<String?>(null)
    val currentStreamingText: StateFlow<String?> = _currentStreamingText.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private val _currentSessionId = mutableStateOf<Long?>(null)
    val currentSessionId: State<Long?> = _currentSessionId

    private val _aiLocationInfo = MutableStateFlow<String?>(null)
    val aiLocationInfo: StateFlow<String?> = _aiLocationInfo.asStateFlow()

    private val _isIngesting = MutableStateFlow(false)
    val isIngesting: StateFlow<Boolean> = _isIngesting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

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
        val downloadId = manager.enqueue(request)

        viewModelScope.launch(Dispatchers.IO) {
            _isDownloading.value = true
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1
                    val bytesDownloaded = if (bytesDownloadedIndex != -1) cursor.getLong(bytesDownloadedIndex) else 0L
                    val bytesTotal = if (bytesTotalIndex != -1) cursor.getLong(bytesTotalIndex) else 0L

                    if (bytesTotal > 0) {
                        _downloadProgress.value = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            _isDownloading.value = false
                            _downloadProgress.value = 1.0f
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            _isDownloading.value = false
                            _errorMessage.value = "Model download failed."
                        }
                    }
                }
                cursor.close()
                if (downloading) delay(1000)
            }
        }
        return downloadId
    }

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    val buttonText = mutableStateOf("Download AI Model")

    fun downloadGemma4Model(): Long? {
        if (!isWifiConnected()) {
            _errorMessage.value = "Error: Wi-Fi is required for model download. It will take few minutes."
            viewModelScope.launch {
                delay(2000)
                _errorMessage.value = null
            }
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
        val downloadId = manager.enqueue(request)

        buttonText.value = "Downloading..."

        viewModelScope.launch(Dispatchers.IO) {
            _isDownloading.value = true
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1
                    val bytesDownloaded = if (bytesDownloadedIndex != -1) cursor.getLong(bytesDownloadedIndex) else 0L
                    val bytesTotal = if (bytesTotalIndex != -1) cursor.getLong(bytesTotalIndex) else 0L

                    if (bytesTotal > 0) {
                        _downloadProgress.value = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            _isDownloading.value = false
                            _downloadProgress.value = 1.0f
                       //     buttonText.value = "Where am I"
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            _isDownloading.value = false
                      //      buttonText.value = "Download again"
                            _errorMessage.value = "Model download failed."
                        }
                    }
                }
                cursor.close()
                if (downloading) delay(1000)
            }
        }
        return downloadId
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

    fun checkIfGemmaModelExist(): Boolean {
        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "gemma-4-E2B-it.litertlm"
        )
        return modelFile.exists()
    }
    
    fun selectSession(sessionId: Long) {
        _isIngesting.value = false
        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "gemma-4-E2B-it.litertlm"
        )

        if (sessionId == -1L) {
            createNewRagSession()
            return
        }

        _currentSessionId.value = sessionId
        viewModelScope.launch {
            try {
                if (modelFile.exists() && !liteRTLMEngine.isInitialized()) {
                    liteRTLMEngine.initialize(modelFile.absolutePath)
                }
            } catch (e: Exception) {
                buttonText.value = "Download model again"
            }

            // Get session info - use first() to ensure we get data even if 'sessions' value is currently empty
            _currentSession.value = sessions.value.find { it.id == sessionId } 
                ?: repository.getAllSessions(userEmail).first().find { it.id == sessionId }

            repository.getMessagesForSession(sessionId).collect {
                _messages.value = it
            }
        }
    }



    fun removeRAGSession() {
        viewModelScope.launch {
            repository.findSession(userEmail, "RAG")?.let { existingSessionId ->
                repository.deleteSession(existingSessionId)
            }
        }
    }

    fun createNewRagSession() {
        removeRAGSession()
        viewModelScope.launch {
            val id = repository.createSession(userEmail, "RAG")
            selectSession(id)
        }
    }

    fun createNewSession(title: String) {
        viewModelScope.launch {
            val id = repository.createSession(userEmail, title)
            selectSession(id)
        }
    }

    suspend fun getCurrentResponse(prompt: String): String {
        if (!liteRTLMEngine.isInitialized()) {
            val modelFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "gemma-4-E2B-it.litertlm"
            )
            if (modelFile.exists()) {
                try {
                    liteRTLMEngine.initialize(modelFile.absolutePath)
                } catch (e: Exception) {
                    _currentStreamingText.value = "Failed to initialize LiteRT-LM: ${e.localizedMessage}"
                    return "Error: ${e.localizedMessage}"
                }
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
        val sessionId = currentSessionId.value ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            try {
                if (!liteRTLMEngine.isInitialized()) {
                    val modelFile = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        "gemma-4-E2B-it.litertlm"
                    )
                    if (modelFile.exists()) {
                        try {
                            liteRTLMEngine.initialize(modelFile.absolutePath)
                        } catch (e: Exception) {
                            _errorMessage.value = "Failed to initialize LiteRT-LM: ${e.localizedMessage}"
                            return@launch
                        }
                    } else {
                        _currentStreamingText.value = "Error: Model file not found. Please download the Gemma model."
                        return@launch
                    }
                }

                repository.sendMessage(sessionId, content, isUser = true)

                val responseBuilder = StringBuilder()
                _currentStreamingText.value = ""

                withContext(Dispatchers.Default) {
                    liteRTLMEngine.sendMessageStream(content)
                        .catch { error ->
                            _currentStreamingText.value = "Error: ${error.localizedMessage}"
                        }
                        .collect { chunk ->
                            val text = chunk.toString()
                            responseBuilder.append(text)
                            _currentStreamingText.value = responseBuilder.toString()
                        }
                }

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

    fun ingestPdf(uri: Uri) {
        viewModelScope.launch {
            _isIngesting.value = true
            try {
                val embeddingModelFile = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "all-MiniLM-L6-v2-quant.tflite"
                )

                if (!embeddingEngine.isInitialized()) {
                    if (embeddingModelFile.exists()) {
                        embeddingEngine.initialize(embeddingModelFile.absolutePath)
                    } else {
                        _currentStreamingText.value = "Error: Embedding model not found. Please download it."
                        return@launch
                    }
                }

                ragRepository.ingestPdf(uri) { text ->
                    embeddingEngine.embed(text)
                }

                _currentStreamingText.value = "PDF ingested successfully."
            } catch (e: Exception) {
                Log.e("ChatViewModel", "PDF ingestion failed", e)
                _currentStreamingText.value = "Error: ${e.localizedMessage}"
            }
        }
    }

    override fun onCleared() {
        ttsManager.shutdown()
    }
}
