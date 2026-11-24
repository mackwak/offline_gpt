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
import androidx.compose.ui.unit.minus
import androidx.compose.ui.unit.plus
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.calculator.CalculatorService
import com.example.offlinegpt.data.engine.EmbeddingEngine
import com.example.offlinegpt.data.engine.LiteRTLMEngine
import com.example.offlinegpt.data.engine.TavilyRepository
import com.example.offlinegpt.data.location.LocationProvider
import com.example.offlinegpt.data.local.ChatMessage
import com.example.offlinegpt.data.local.ChatSession
import com.example.offlinegpt.data.local.DiaryDao
import com.example.offlinegpt.data.local.DiaryEntry
import com.example.offlinegpt.data.local.FavoritePlaceDao
import com.example.offlinegpt.data.local.FavoritePlaceEntity
import com.example.offlinegpt.data.mbti.MbtiDescriptions
import com.example.offlinegpt.data.repository.ChatRepository
import com.example.offlinegpt.data.repository.RagRepository
import com.example.offlinegpt.ui.places.PlaceItem
import com.example.offlinegpt.util.tts.TextToSpeechManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    private val diaryDao: DiaryDao,
    private val favoritePlaceDao: FavoritePlaceDao,
    private val auth: FirebaseAuth,
    private val locationProvider: LocationProvider,
    private val ttsManager: TextToSpeechManager,
    private val embeddingEngine: EmbeddingEngine,
    private val calculatorService: CalculatorService,
    private val tavilyRepository: TavilyRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private companion object {
        const val DEFAULT_DIARY_EMBEDDING_WEIGHT = 0.3f
    }

    data class FavoritePlaceMatch(
        val place: FavoritePlaceEntity,
        val similarity: Float,
        val mbtiType: String
    )

    data class FavoritePlaceDecision(
        val selected: FavoritePlaceMatch?,
        val topCandidates: List<FavoritePlaceMatch>,
        val agentReason: String,
        val locationWeatherSummary: String
    )

    private val _searchResults = MutableStateFlow<List<String>>(emptyList())
    val searchResults: StateFlow<List<String>> = _searchResults

    val liteRTLMEngine = LiteRTLMEngine()
    private val userEmail: String
        get() = auth.currentUser?.email ?: "anonymous"

    val sessions: StateFlow<List<ChatSession>> = repository.getAllSessions(userEmail)
        .map { list -> list.filter { it.title != "RAG" } }
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

    private val _bestFavoriteMatch = MutableStateFlow<FavoritePlaceMatch?>(null)
    val bestFavoriteMatch: StateFlow<FavoritePlaceMatch?> = _bestFavoriteMatch.asStateFlow()

    private val _topFavoriteMatches = MutableStateFlow<List<FavoritePlaceMatch>>(emptyList())
    val topFavoriteMatches: StateFlow<List<FavoritePlaceMatch>> = _topFavoriteMatches.asStateFlow()

    private val _diaryEmbeddingWeight = MutableStateFlow(DEFAULT_DIARY_EMBEDDING_WEIGHT)
    val diaryEmbeddingWeight: StateFlow<Float> = _diaryEmbeddingWeight.asStateFlow()

    private var activeDownloadId: Long? = null

    fun clearError() {
        _errorMessage.value = null
    }

    fun setDiaryEmbeddingWeight(weight: Float) {
        _diaryEmbeddingWeight.value = weight.coerceIn(0f, 1f)
    }

    fun downloadModelFile(): Long? {
        // 1. Corrected file name and model URL
        val fileName = "universal_sentence_encoder.tflite"
        val modelUrl = "https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/1/universal_sentence_encoder.tflite"

        Log.d("Model Download", "Model URL: $modelUrl")

        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("Downloading Text Embedder Model")
            .setDescription("Downloading on-device AI embedding weights...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true) // Set to true if cellular downloads are allowed
            .setAllowedOverRoaming(false)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)
        activeDownloadId = downloadId

        viewModelScope.launch(Dispatchers.IO) {
            _isDownloading.value = true
            var downloading = true

            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)

                // Safely query DownloadManager
                manager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                        val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1
                        val bytesDownloaded = if (bytesDownloadedIndex != -1) cursor.getLong(bytesDownloadedIndex) else 0L
                        val bytesTotal = if (bytesTotalIndex != -1) cursor.getLong(bytesTotalIndex) else 0L

                        if (bytesTotal > 0L) {
                            _downloadProgress.value = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                        }

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                downloading = false
                                _isDownloading.value = false
                                _downloadProgress.value = 1.0f
                                validateModel()
                            }

                            DownloadManager.STATUS_FAILED -> {
                                downloading = false
                                _isDownloading.value = false
                                val reason = if (reasonIndex != -1) cursor.getInt(reasonIndex) else -1
                                _errorMessage.value = "Error: Gemma model not found. Please download it first."
                                Log.e("Model Download", "Failed with DownloadManager reason: $reason")
                            }
                        }
                    } else {
                        // Download canceled or purged from manager
                        downloading = false
                        _isDownloading.value = false
                        _errorMessage.value = "Download session was canceled or removed."
                    }
                } ?: run {
                    downloading = false
                    _isDownloading.value = false
                }

                if (downloading) {
                    delay(1000)
                }
            }
        }
        return downloadId
    }

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    val buttonText = mutableStateOf("Download AI Model")


    fun stopDownloading() {
        _isDownloading.value = false
        _errorMessage.value = ""
        _downloadProgress.value = 0f
        _aiLocationInfo.value = null
        activeDownloadId?.let { id ->
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.remove(id)
            activeDownloadId = null
        }
    }

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
        activeDownloadId = downloadId

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
                            buttonText.value = "Where am I"
                            _aiLocationInfo.value = null
                            validateModel()
                        }

                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            _isDownloading.value = false
                            _errorMessage.value = "Model download failed."
                            _aiLocationInfo.value = null
                        }
                    }
                } else {
                    downloading = false
                    _aiLocationInfo.value = null
                    _isDownloading.value = false
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
            "universal_sentence_encoder.tflite"
        )

        return modelFile.exists() && modelFile.length() > 0
    }

    fun checkIfGemmaModelExist(): Boolean {
        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "gemma-4-E2B-it.litertlm"
        )
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * Attempts to initialize the engine to verify if the model file is valid and hardware compatible.
     */
    fun validateModel() {
        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "gemma-4-E2B-it.litertlm"
        )
        if (!modelFile.exists() || modelFile.length() == 0L) {
            _isModelReady.value = false
            return
        }

        viewModelScope.launch {
            try {
                if (!liteRTLMEngine.isInitialized()) {
                    liteRTLMEngine.initialize(modelFile.absolutePath)
                }
                _isModelReady.value = true
            } catch (e: Exception) {
                _isModelReady.value = false
                _errorMessage.value = "Error: Gemma model not found. Please download it first."
            }
        }
    }

    fun selectSession(sessionId: Long) {
        _isIngesting.value = false
        val modelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "gemma-4-E2B-it.litertlm"
        )

        _currentSessionId.value = sessionId
        if (sessionId == -1L) {
            createNewRagSession()
            return
        }


        viewModelScope.launch {
            try {
                if (modelFile.exists() && !liteRTLMEngine.isInitialized()) {
                    liteRTLMEngine.initialize(modelFile.absolutePath)
                    _isModelReady.value = true
                } else if (liteRTLMEngine.isInitialized()) {
                    _isModelReady.value = true
                }
            } catch (e: Exception) {
                _isModelReady.value = false
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

    fun printDocumentPages() {
        viewModelScope.launch {
            ragRepository.getAllPages()
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
                    _currentStreamingText.value = "Error: Gemma model not found. Please download it first."
                    return "Error: Gemma model not found. Please download it first."
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
                        responseBuilder.append("Error: Gemma model not found. Please download it first.")
                    }
                    .collect { chunk ->
                        responseBuilder.append(chunk.toString())
                    }
            }
        } catch (e: Exception) {
            return "Error: Gemma model not found. Please download it first."
        }

        if (responseBuilder.toString().startsWith("Error:")) {
            return "Gemma model not found. Please download it first."
        } else {
            return responseBuilder.toString()
        }


    }

    fun sendRagMessage(content: String) {

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
                            _errorMessage.value = "Error: Gemma model not found. Please download it first."
                            return@launch
                        }
                    } else {
                        _currentStreamingText.value = "Error: Gemma model not found. Please download it first."
                        return@launch
                    }
                }

                // get embedding from content
                repository.sendMessage(sessionId, content, isUser = true)

                val responseBuilder = StringBuilder()
                _currentStreamingText.value = ""

                withContext(Dispatchers.Default) {
                    val embedding = embeddingEngine.embed(content)
                    val contextBuilder = StringBuilder()
                    ragRepository.findRelevantPages(embedding).forEach { page ->
                        contextBuilder.append("${page}\n")
                    }
                    Log.d("RagRepository", "contextBuilder: ${contextBuilder.toString()}")

                    val contextText = contextBuilder.toString()
                    val ragPrompt = if (contextText.isNotEmpty()) {
                        "You are a helpful assistant. Use the following context to answer the user's question.\n\n" +
                                "Context:\n$contextText\n\n" +
                                "User Question: $content\n\n" +
                                "Answer:"
                    } else {
                        content
                    }

                    liteRTLMEngine.sendMessageStream(ragPrompt)
                        .catch { error ->
                            _currentStreamingText.value = "Error: Gemma model not found. Please download it first."
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
                _currentStreamingText.value = "Error: Gemma model not found. Please download it first."
            } finally {
                _currentStreamingText.value = null
            }
        }


    }

    private suspend fun executeToolCall(toolName: String, args: String): String? {
        return when (toolName.lowercase(Locale.getDefault())) {
            "calculator", "calc" -> {
                val parts = args.split(",").map { it.trim() }
                if (parts.size >= 3) {
                    val a = parts[0].toDoubleOrNull()
                    val b = parts[1].toDoubleOrNull()
                    val op = parts[2]
                    if (a != null && b != null) {
                        val res = when (op) {
                            "+" -> calculatorService.plus(a, b)
                            "-" -> calculatorService.minus(a, b)
                            "*" -> a * b
                            "/" -> if (b != 0.0) a / b else "Division by zero"
                            else -> calculatorService.plus(a, b)
                        }
                        "$a $op $b = $res"
                    } else null
                } else if (parts.size == 2) {
                    val a = parts[0].toDoubleOrNull()
                    val b = parts[1].toDoubleOrNull()
                    if (a != null && b != null) {
                        "$a + $b = ${calculatorService.plus(a, b)}"
                    } else null
                } else null
            }
            "get_location", "location" -> {
                val loc = locationProvider.getCurrentLocation()
                if (loc != null) {
                    val address = locationProvider.getAddressFromLocation(loc)
                    "$address (Lat: ${loc.latitude}, Lon: ${loc.longitude})"
                } else {
                    "Location unavailable"
                }
            }
            "get_time", "time" -> {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            }
            "web_search" -> {
                tavilyRepository.performSearch(args).fold(
                    onSuccess = { response ->
                        val results = response.results.joinToString("\n") { result ->
                            "- ${result.title}: ${result.url}"
                        }
                        "Search Results for '$args':\n$results"
                    },
                    onFailure = { error ->
                        Log.d("promptWithTools", "Error: ${error.localizedMessage}")

                        "Web search failed: ${error.localizedMessage}"
                    }
                )
            }
            else -> null
        }
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
                            _errorMessage.value = "Error: Gemma model not found. Please download it first."
                            return@launch
                        }
                    } else {
                        _currentStreamingText.value = "Error: Model file not found. Please download the Gemma model."
                        return@launch
                    }
                }

                // get all existing messages from repository for history context
                val existingMessages = repository.getMessagesForSession(sessionId).first()
                val existing = existingMessages.joinToString("\n") { message ->
                    "${if (message.isUser) "User" else "Assistant"}: ${message.content}"
                }

                repository.sendMessage(sessionId, content, isUser = true)

                val responseBuilder = StringBuilder()
                _currentStreamingText.value = ""

                val promptWithTools = """
                You are an AI assistant with access to tools.
                Available tools:
                - calculator(a, b, op): Performs math calculations. Format: [TOOL: calculator(12, 5, +)] where op is +, -, *, /
                - get_location(): Returns current location. Format: [TOOL: get_location()]
                - get_time(): Returns current date and time. Format: [TOOL: get_time()]
                - web_search(): Performs a web search if answer is not sure. Format: [TOOL: web_search(query)]

                If you need to use a tool to answer the user's question, output [TOOL: tool_name(...)].
                Otherwise, answer directly.
                
                Context: $existing
 
                User Question: $content
            """.trimIndent()


                Log.d("promptWithTools", promptWithTools)

                withContext(Dispatchers.Default) {
                    liteRTLMEngine.sendMessageStream(promptWithTools)
                        .catch { error ->
                            Log.d("promptWithTools", "Error: ${error.localizedMessage}")
                            _currentStreamingText.value = "${error.localizedMessage}"
                        }
                        .collect { chunk ->
                            val text = chunk.toString()
                            responseBuilder.append(text)
                            _currentStreamingText.value = responseBuilder.toString()
                        }
                }

                var generatedText = responseBuilder.toString()
                val toolCallRegex = Regex("""\[TOOL:\s*(\w+)\((.*?)\)\]""")
                val toolMatches = toolCallRegex.findAll(generatedText).toList()

                if (toolMatches.isNotEmpty()) {
                    val toolResultsBuilder = StringBuilder(generatedText)
                    for (match in toolMatches) {
                        val toolName = match.groupValues[1]
                        val toolArgs = match.groupValues[2]
                        val result = executeToolCall(toolName, toolArgs)
                        if (result != null) {
                            toolResultsBuilder.append("\n\n🛠️ [Tool Executed: $toolName]\nResult: $result")
                        } else {
                            Log.d("promptWithTools", "Tool Failed")
                        }
                    }
                    generatedText = toolResultsBuilder.toString()
                    _currentStreamingText.value = generatedText
                }

                val finalResponse = generatedText

                if (finalResponse.isNotEmpty()) {
                    repository.sendMessage(sessionId, finalResponse, isUser = false)
                }
            } catch (e: Exception) {
                _currentStreamingText.value = "Error: Gemma model not found. Please download it first."
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
            "I am currently facing $compassHeading. The current time is $time, and my location is $address. Based on this, please describe where I am, tell me the exact time, and explain where the sun is likely positioned in the sky relative to my current position. Conclude if i can see the sun now"
        } else {
            "I am currently facing $compassHeading. The current time is $time. I couldn't retrieve my precise location. Based on this, please tell me what time it is, let me know that my location is unavailable, and mention where the sun would typically be at this time of day. Conclude if i can see the sun now"
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

            ragRepository.removeAllPages()

            _isIngesting.value = true
            try {
                val embeddingModelFile = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "universal_sentence_encoder.tflite"
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


                printDocumentPages()

                _currentStreamingText.value = "PDF ingested successfully."
            } catch (e: Exception) {
                Log.e("ChatViewModel", "PDF ingestion failed", e)
                _currentStreamingText.value = "Error: ${e.localizedMessage}"
            }
        }
    }

    fun pickBestFavoritePlaceByMood(
        mbti: String,
        mood: String, places: List<PlaceItem>,
        topK: Int = 3,
        onResult: ((List<PlaceItem>) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {

                if (!ensureEmbeddingEngineReady()) {
                    _errorMessage.value = "Embedding model not found. Please download the embedding model first."
                    _bestFavoriteMatch.value = null
                    _topFavoriteMatches.value = emptyList()
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(emptyList())
                    }
                    return@launch
                }

                // get the latest diary from room
                val latestDiary = diaryDao.getLatestDiary()

                val moodEmbedding = embeddingEngine.embed("feels $mood")
                val mbtiDescription = MbtiDescriptions.getDescription(mbti)
                val mbtiEmbedding = embeddingEngine.embed("MBTI $mbti profile: $mbtiDescription")



                val preferenceEmbedding = blendEmbeddings(moodEmbedding, mbtiEmbedding)

                val candidates = mutableListOf<PlaceItem>()
                for (place in places) {

                    val score = cosineSimilaritySafe(preferenceEmbedding, place.embedding!!) ?: continue
                    candidates.add(place)
                }

                val safeTopK = topK.coerceAtLeast(1)
                val topMatches = candidates
                    .sortedByDescending { it.similarity }
                    .take(safeTopK)

                _topFavoriteMatches.value = topMatches
                _bestFavoriteMatch.value = topMatches.firstOrNull()
                withContext(Dispatchers.Main) {
                    onResult?.invoke(topMatches)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to pick best favorite place", e)
                _errorMessage.value = "Failed to compare MBTI and favorite embeddings: ${e.localizedMessage}"
                _bestFavoriteMatch.value = null
                _topFavoriteMatches.value = emptyList()
                withContext(Dispatchers.Main) {
                    onResult?.invoke(emptyList())
                }
            }
        }
    }

    fun pickBestFavoritePlace(
        mbtiType: String,
        topK: Int = 3,
        onResult: ((List<FavoritePlaceMatch>) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val favorites = favoritePlaceDao.getAllFavoritePlaces().first()
                if (favorites.isEmpty()) {
                    _bestFavoriteMatch.value = null
                    _topFavoriteMatches.value = emptyList()
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(emptyList())
                    }
                    return@launch
                }

                if (!ensureEmbeddingEngineReady()) {
                    _errorMessage.value = "Embedding model not found. Please download the embedding model first."
                    _bestFavoriteMatch.value = null
                    _topFavoriteMatches.value = emptyList()
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(emptyList())
                    }
                    return@launch
                }

                // get the latest diary from room
                val latestDiary = diaryDao.getLatestDiary()

                val mbtiDescription = MbtiDescriptions.getDescription(mbtiType)
                val mbtiEmbedding = embeddingEngine.embed("MBTI $mbtiType profile: $mbtiDescription")
                val diaryEmbedding = latestDiary
                    ?.let { buildDiaryEmbeddingText(it) }
                    ?.let { embeddingEngine.embed(it) }
                val preferenceEmbedding = blendEmbeddings(mbtiEmbedding, diaryEmbedding)

                val candidates = mutableListOf<FavoritePlaceMatch>()
                for (place in favorites) {
                    val placeEmbedding = resolvePlaceEmbeddingForComparison(place, preferenceEmbedding.size) ?: run {
                        Log.w("ChatViewModel", "Skip place ${place.placeId}: invalid embedding")
                        continue
                    }

                    val score = cosineSimilaritySafe(preferenceEmbedding, placeEmbedding) ?: continue
                    candidates.add(FavoritePlaceMatch(place = place, similarity = score, mbtiType = mbtiType))
                }

                val safeTopK = topK.coerceAtLeast(1)
                val topMatches = candidates
                    .sortedByDescending { it.similarity }
                    .take(safeTopK)

                _topFavoriteMatches.value = topMatches
                _bestFavoriteMatch.value = topMatches.firstOrNull()
                withContext(Dispatchers.Main) {
                    onResult?.invoke(topMatches)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to pick best favorite place", e)
                _errorMessage.value = "Failed to compare MBTI and favorite embeddings: ${e.localizedMessage}"
                _bestFavoriteMatch.value = null
                _topFavoriteMatches.value = emptyList()
                withContext(Dispatchers.Main) {
                    onResult?.invoke(emptyList())
                }
            }
        }
    }

    fun pickBestFavoritePlaceByLocationAndWeather(
        mbtiType: String,
        topK: Int = 3,
        onResult: ((FavoritePlaceDecision) -> Unit)? = null
    ) {
        pickBestFavoritePlace(mbtiType = mbtiType, topK = topK) { topMatches ->
            viewModelScope.launch(Dispatchers.IO) {
                if (topMatches.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(
                            FavoritePlaceDecision(
                                selected = null,
                                topCandidates = emptyList(),
                                agentReason = "No favorite candidates available.",
                                locationWeatherSummary = "Location/weather unavailable"
                            )
                        )
                    }
                    return@launch
                }

                val contextSummary = getLocationWeatherSummary()
                val decisionPrompt = buildFavoriteDecisionPrompt(mbtiType, contextSummary, topMatches)
                val agentAnswer = getCurrentResponse(decisionPrompt)
                val selected = parseSelectedCandidate(agentAnswer, topMatches) ?: topMatches.firstOrNull()

                _bestFavoriteMatch.value = selected

                withContext(Dispatchers.Main) {
                    onResult?.invoke(
                        FavoritePlaceDecision(
                            selected = selected,
                            topCandidates = topMatches,
                            agentReason = agentAnswer,
                            locationWeatherSummary = contextSummary
                        )
                    )
                }
            }
        }
    }

    private fun buildFavoriteDecisionPrompt(
        mbtiType: String,
        locationWeatherSummary: String,
        candidates: List<FavoritePlaceMatch>
    ): String {
        val candidateText = candidates.mapIndexed { index, match ->
            "${index + 1}. ${match.place.name} (${match.place.category}) | rating=${match.place.rating}, address=${match.place.address}, embeddingSimilarity=${"%.4f".format(match.similarity)}"
        }.joinToString("\n")

        return """
            You are a local trip planning agent.
            Select exactly ONE best place among 3 candidates.

            User MBTI: $mbtiType
            Current location/weather: $locationWeatherSummary

            Candidates:
            $candidateText

            Output format:
            CHOICE: <1|2|3>
            REASON: <short reason considering weather + location + MBTI>
        """.trimIndent()
    }

    private fun parseSelectedCandidate(
        agentAnswer: String,
        candidates: List<FavoritePlaceMatch>
    ): FavoritePlaceMatch? {
        val match = Regex("""CHOICE\s*:\s*([1-9])""", RegexOption.IGNORE_CASE).find(agentAnswer)
        val parsedIndex = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (parsedIndex != null && parsedIndex in 1..candidates.size) {
            return candidates[parsedIndex - 1]
        }

        // Fallback: try to find first plain number in answer.
        val fallback = Regex("""\b([1-9])\b""").find(agentAnswer)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fallback != null && fallback in 1..candidates.size) {
            return candidates[fallback - 1]
        }

        return null
    }

    private suspend fun getLocationWeatherSummary(): String = withContext(Dispatchers.IO) {
        val location = locationProvider.getCurrentLocation()
        val lat = location?.latitude ?: 37.7749
        val lon = location?.longitude ?: -122.4194
        val address = if (location != null) {
            locationProvider.getAddressFromLocation(location)
        } else {
            "Unknown current address"
        }

        return@withContext try {
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&timezone=auto"
            val responseText = URL(urlString).readText()
            val json = JSONObject(responseText)
            val currentWeather = json.getJSONObject("current_weather")
            val temp = currentWeather.optDouble("temperature", Double.NaN)
            val wind = currentWeather.optDouble("windspeed", Double.NaN)
            val code = currentWeather.optInt("weathercode", -1)
            val weatherLabel = weatherCodeToText(code)

            "address=$address, lat=$lat, lon=$lon, weather=$weatherLabel, temp=${temp.toInt()}C, wind=${wind.toInt()}km/h"
        } catch (e: Exception) {
            "address=$address, lat=$lat, lon=$lon, weather=unavailable"
        }
    }

    private fun weatherCodeToText(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1, 2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Foggy"
            51, 53, 55, 61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            80, 81, 82 -> "Heavy Rain"
            95, 96, 99 -> "Thunderstorm"
            else -> "Unknown"
        }
    }

    private suspend fun resolvePlaceEmbeddingForComparison(
        place: FavoritePlaceEntity,
        expectedSize: Int
    ): FloatArray? {
        val current = place.embedding
        if (current.isNotEmpty() && current.size == expectedSize) {
            return current
        }

        // Old favorites may contain fallback vectors with different dimensions; re-embed using semantic text.
        return try {
            val semanticText = "name: ${place.name}, category: ${place.category}, rating: ${place.rating}, latitude: ${place.latitude}, longitude: ${place.longitude}"
            val refreshed = embeddingEngine.embed(semanticText)
            favoritePlaceDao.insertFavorite(place.copy(embedding = refreshed))
            refreshed
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to refresh embedding for place ${place.placeId}", e)
            null
        }
    }

    private fun buildDiaryEmbeddingText(entry: DiaryEntry): String {
        val contentSnippet = entry.content.take(800)
        return "diary title: ${entry.title}, mood: ${entry.mood}, content: $contentSnippet"
    }

    private fun blendEmbeddings(primary: FloatArray, secondary: FloatArray?): FloatArray {
        if (secondary == null || primary.size != secondary.size) return primary

        val diaryWeight = _diaryEmbeddingWeight.value
        val primaryWeight = 1f - diaryWeight
        return FloatArray(primary.size) { i ->
            (primary[i] * primaryWeight) + (secondary[i] * diaryWeight)
        }
    }

    private suspend fun ensureEmbeddingEngineReady(): Boolean {
        if (embeddingEngine.isInitialized()) return true

        val embeddingModelFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "universal_sentence_encoder.tflite"
        )
        if (!embeddingModelFile.exists()) return false

        embeddingEngine.initialize(embeddingModelFile.absolutePath)
        return embeddingEngine.isInitialized()
    }

    private fun cosineSimilaritySafe(a: FloatArray, b: FloatArray): Float? {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return null

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        if (normA == 0.0f || normB == 0.0f) return null
        return (dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))).coerceIn(-1f, 1f)
    }

    override fun onCleared() {
        ttsManager.shutdown()
    }
}
