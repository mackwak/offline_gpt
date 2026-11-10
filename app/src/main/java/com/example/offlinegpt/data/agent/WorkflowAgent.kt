package com.example.offlinegpt.data.agent

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.offlinegpt.data.calculator.CalculatorService
import com.example.offlinegpt.data.engine.EmbeddingEngine
import com.example.offlinegpt.data.engine.LiteRTLMEngine
import com.example.offlinegpt.data.engine.TavilyRepository
import com.example.offlinegpt.data.location.LocationProvider
import com.example.offlinegpt.data.repository.RagRepository
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Workflow Agent that enforces structured JSON responseFormat,
 * maintains complete EventContext history, and executes automated ReAct loops.
 */
@Singleton
class WorkflowAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val liteRTLMEngine: LiteRTLMEngine,
    private val embeddingEngine: EmbeddingEngine,
    private val ragRepository: RagRepository,
    private val calculatorService: CalculatorService,
    private val locationProvider: LocationProvider,
    private val tavilyRepository: TavilyRepository,
    private val gson: Gson
) {
    val contextManager = AgentEventContextManager()

    suspend fun ensureEngineInitialized() {
        if (!liteRTLMEngine.isInitialized()) {
            val modelFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "gemma-4-E2B-it.litertlm"
            )
            if (modelFile.exists()) {
                liteRTLMEngine.initialize(modelFile.absolutePath)
                contextManager.addEvent(
                    AgentEvent.SystemStateEvent("ENGINE_INITIALIZED", "LiteRT-LM Engine successfully initialized.")
                )
            } else {
                throw IllegalStateException("Model file not found. Please download the Gemma model first.")
            }
        }

        if (!embeddingEngine.isInitialized()) {
            val embeddingModelFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "universal_sentence_encoder.tflite"
            )
            if (embeddingModelFile.exists()) {
                embeddingEngine.initialize(embeddingModelFile.absolutePath)
                contextManager.addEvent(
                    AgentEvent.SystemStateEvent("EMBEDDING_INITIALIZED", "Embedding Engine successfully initialized.")
                )
            }
        }

        // Clear previous DB documents and ingest raw/coverletter.pdf
        if (embeddingEngine.isInitialized()) {
            ragRepository.removeAllPages()

            ragRepository.ingestRawPdf(
                rawResId = com.example.offlinegpt.R.raw.coverletter,
                fileName = "coverletter.pdf"
            ) { text ->
                embeddingEngine.embed(text)
            }
            contextManager.addEvent(
                AgentEvent.SystemStateEvent("RAG_PDF_LOADED", "Cleared old DB entries and loaded raw/coverletter.pdf into vector DB.")
            )
        }
    }

    /**
     * System prompt establishing the Agent identity, tools, and responseFormat JSON schema.
     */
    private fun getSystemPrompt(eventContext: String, userPrompt: String): String {
        return """
        You are an autonomous AI Workflow Agent.
        
        CRITICAL: You MUST ALWAYS respond ONLY in valid JSON strictly following this responseFormat schema:
        {
          "thought": "Your step-by-step reasoning process",
          "action": "EXECUTE_TOOL" | "COMPLETE" | "NEED_MORE_INFO" | "ERROR",
          "toolCall": {
            "toolName": "calculator" | "get_location" | "get_time" | "web_search",
            "args": { "param1": "value1" }
          },
          "finalAnswer": "Your response to the user when action is COMPLETE"
        }
        
        AVAILABLE TOOLS:
        1. "calculator": performs math calculations. args: { "a": 10, "b": 5, "op": "+" }
        2. "get_location": gets current GPS location and address. args: {}
        3. "get_time": gets current timestamp. args: {}
        4. "web_search": searches web. args: { "query": "search query" }
        
        RULES:
        - Check the EVENT CONTEXT history and RAG DB entries before calling tools again.
        - Escape all newlines inside JSON string values as \n.
        - If the query cannot be answered from local RAG DB context, execute "web_search".
        - If you have enough info to formulate a final answer, set "action": "COMPLETE" and populate "finalAnswer".
        - Do NOT include any markdown code blocks outside JSON. Output pure valid JSON.
        
        $eventContext
        
        CURRENT USER QUESTION: $userPrompt
        """.trimIndent()
    }

    /**
     * Executes a full multi-step Agent workflow.
     */
    suspend fun runWorkflow(
        userPrompt: String,
        maxSteps: Int = 15,
        onStepUpdate: (step: Int, response: AgentResponseFormat) -> Unit
    ): String {
        // Ensure LiteRT-LM engine & RAG DB are initialized before AI workflow runs
        ensureEngineInitialized()

        // Record user input event
        contextManager.addEvent(AgentEvent.UserInputEvent(message = userPrompt))

        /*
        // Record location event if available
        val loc = locationProvider.getCurrentLocation()
        if (loc != null) {
            val addr = locationProvider.getAddressFromLocation(loc)
            contextManager.addEvent(
                AgentEvent.LocationEvent(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    address = addr
                )
            )
        }
        */

        // Query DB Vector Search (RAG) for initial user prompt context
        if (embeddingEngine.isInitialized()) {
            try {
                val queryEmbedding = embeddingEngine.embed(userPrompt)
                val dbRelevantPages = ragRepository.findRelevantPages(queryEmbedding, topK = 3)
                if (dbRelevantPages.isNotEmpty()) {
                    val dbContextStr = dbRelevantPages.joinToString("\n---\n")
                    contextManager.addEvent(
                        AgentEvent.SystemStateEvent("RAG_DB_SEARCH", "Retrieved RAG pages from DB:\n$dbContextStr")
                    )
                }
            } catch (e: Exception) {
                Log.e("WorkflowAgent", "Error retrieving from RAG DB", e)
            }
        }

        var currentStep = 0
        while (currentStep < maxSteps) {
            currentStep++

            // Reset native conversation state per step to prevent prefill token duplication
            liteRTLMEngine.resetConversation()

            val contextPrompt = contextManager.buildContextPrompt()
            val fullPrompt = getSystemPrompt(contextPrompt, userPrompt)

            val responseSb = StringBuilder()
            liteRTLMEngine.sendMessageStream(fullPrompt)
                .catch { e -> responseSb.append("Error: ${e.localizedMessage}") }
                .collect { chunk -> responseSb.append(chunk.toString()) }

            val rawOutput = responseSb.toString()
            val parsedResponse = parseAgentResponse(rawOutput)

            // Record Agent thought into Context
            parsedResponse.thought?.let { thought ->
                contextManager.addEvent(AgentEvent.AgentThoughtEvent(thought = thought))
            }

            onStepUpdate(currentStep, parsedResponse)

            when (parsedResponse.resolvedAction) {
                AgentAction.COMPLETE -> {
                    val finalAnswer = parsedResponse.finalAnswer ?: rawOutput
                    contextManager.addEvent(
                        AgentEvent.SystemStateEvent("WORKFLOW_COMPLETED", "Final answer provided.")
                    )
                    return finalAnswer
                }

                AgentAction.EXECUTE_TOOL -> {
                    val toolCall = parsedResponse.toolCall
                    if (toolCall != null) {
                        val result = executeTool(toolCall.toolName, toolCall.args)
                        contextManager.addEvent(
                            AgentEvent.ToolExecutionEvent(
                                toolName = toolCall.toolName,
                                args = gson.toJson(toolCall.args),
                                result = result,
                                isSuccess = !result.startsWith("Error")
                            )
                        )
                    } else {
                        contextManager.addEvent(
                            AgentEvent.SystemStateEvent("TOOL_ERROR", "Action was EXECUTE_TOOL but toolCall was null")
                        )
                    }
                }

                AgentAction.NEED_MORE_INFO, AgentAction.ERROR -> {
                    val msg = parsedResponse.finalAnswer ?: "Workflow ended with state ${parsedResponse.resolvedAction}"
                    return msg
                }
            }
        }

        return "Agent reached maximum workflow steps ($maxSteps) without completing."
    }

    private suspend fun executeTool(toolName: String, argsMap: Map<String, Any?>): String {
        return when (toolName.lowercase(Locale.getDefault())) {
            "calculator", "calc" -> {
                val a = argsMap["a"]?.toString()?.toDoubleOrNull()
                val b = argsMap["b"]?.toString()?.toDoubleOrNull()
                val op = argsMap["op"]?.toString() ?: "+"
                if (a != null && b != null) {
                    val res = when (op) {
                        "+" -> calculatorService.plus(a, b)
                        "-" -> calculatorService.minus(a, b)
                        "*" -> a * b
                        "/" -> if (b != 0.0) a / b else "Division by zero"
                        else -> calculatorService.plus(a, b)
                    }
                    "$a $op $b = $res"
                } else "Error: Invalid calculation arguments $argsMap"
            }
            "get_location", "location" -> {
                val loc = locationProvider.getCurrentLocation()
                if (loc != null) {
                    val address = locationProvider.getAddressFromLocation(loc)
                    "$address (Lat: ${loc.latitude}, Lon: ${loc.longitude})"
                } else "Location unavailable"
            }
            "get_time", "time" -> {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            }
            "web_search" -> {
                val query = argsMap["query"]?.toString() ?: ""
                tavilyRepository.performSearch(query).fold(
                    onSuccess = { response ->
                        val summary = if (!response.answer.isNullOrBlank()) "Direct Answer: ${response.answer}\n\n" else ""
                        val details = response.results.take(3).joinToString("\n\n") { result ->
                            val snippet = if (result.content.length > 250) result.content.take(250) + "..." else result.content
                            "Title: ${result.title}\nSnippet: $snippet\nURL: ${result.url}"
                        }
                        val searchResultText = "$summary$details"

                        // Vectorize web search content and save to Room DB for RAG
                        if (embeddingEngine.isInitialized()) {
                            ragRepository.ingestWebSearchText(query, searchResultText) { text ->
                                embeddingEngine.embed(text)
                            }
                            contextManager.addEvent(
                                AgentEvent.SystemStateEvent("RAG_WEB_INGESTED", "Saved web search vector embeddings to DB for query: $query")
                            )
                        }

                        searchResultText
                    },
                    onFailure = { "Web search failed: ${it.localizedMessage}" }
                )
            }
            else -> "Error: Unknown tool $toolName"
        }
    }

    /**
     * Parses the LLM raw output into AgentResponseFormat JSON schema with robust sanitization and fallback resolution.
     */
    private fun parseAgentResponse(rawText: String): AgentResponseFormat {
        return try {
            val jsonStart = rawText.indexOf('{')
            val jsonEnd = rawText.lastIndexOf('}')
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                val jsonString = rawText.substring(jsonStart, jsonEnd + 1)
                val sanitizedJson = sanitizeJsonString(jsonString)

                val reader = JsonReader(StringReader(sanitizedJson)).apply { isLenient = true }
                val parsed: AgentResponseFormat = gson.fromJson(reader, AgentResponseFormat::class.java)

                if (parsed.action == null) {
                    val inferredAction = inferActionFromJson(jsonString, parsed)
                    parsed.copy(action = inferredAction)
                } else {
                    parsed
                }
            } else {
                fallbackParseAgentResponse(rawText)
            }
        } catch (e: Exception) {
            Log.e("WorkflowAgent", "JSON parsing error, using fallback: ${e.localizedMessage}")
            fallbackParseAgentResponse(rawText)
        }
    }

    private fun sanitizeJsonString(json: String): String {
        val sb = StringBuilder()
        var inString = false
        var isEscaped = false
        for (i in json.indices) {
            val ch = json[i]
            if (ch == '"' && !isEscaped) {
                inString = !inString
            }
            if (inString && (ch == '\n' || ch == '\r')) {
                sb.append("\\n")
            } else {
                sb.append(ch)
            }
            isEscaped = (ch == '\\' && !isEscaped)
        }
        return sb.toString()
    }

    private fun fallbackParseAgentResponse(rawText: String): AgentResponseFormat {
        val thoughtRegex = Regex("""["']thought["']\s*:\s*["'](.*?)["']""", RegexOption.DOT_MATCHES_ALL)
        val finalAnswerRegex = Regex("""["']finalAnswer["']\s*:\s*["'](.*?)["']""", RegexOption.DOT_MATCHES_ALL)

        val thoughtMatch = thoughtRegex.find(rawText)?.groupValues?.get(1)
        val finalAnswerMatch = finalAnswerRegex.find(rawText)?.groupValues?.get(1)

        val cleanAnswer = if (!finalAnswerMatch.isNullOrBlank()) {
            finalAnswerMatch.replace("\\n", "\n")
        } else {
            rawText.replace(Regex("""```json|```|\{|\}"""), "").trim()
        }

        return AgentResponseFormat(
            thought = thoughtMatch ?: "Synthesizing answer from retrieved context",
            action = AgentAction.COMPLETE,
            finalAnswer = cleanAnswer
        )
    }

    private fun inferActionFromJson(jsonString: String, parsed: AgentResponseFormat): AgentAction {
        val lowerJson = jsonString.lowercase(Locale.getDefault())
        return when {
            lowerJson.contains("\"action\": \"execute_tool\"") ||
            lowerJson.contains("\"action\":\"execute_tool\"") ||
            parsed.toolCall != null -> AgentAction.EXECUTE_TOOL

            lowerJson.contains("\"action\": \"complete\"") ||
            lowerJson.contains("\"action\":\"complete\"") ||
            !parsed.finalAnswer.isNullOrBlank() -> AgentAction.COMPLETE

            lowerJson.contains("\"action\": \"need_more_info\"") -> AgentAction.NEED_MORE_INFO
            lowerJson.contains("\"action\": \"error\"") -> AgentAction.ERROR
            else -> AgentAction.COMPLETE
        }
    }
}
