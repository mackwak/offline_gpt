package com.example.offlinegpt.data.agent

import android.util.Log
import com.example.offlinegpt.data.calculator.CalculatorService
import com.example.offlinegpt.data.engine.LiteRTLMEngine
import com.example.offlinegpt.data.engine.TavilyRepository
import com.example.offlinegpt.data.location.LocationProvider
import com.google.gson.Gson
import kotlinx.coroutines.flow.catch
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
    private val liteRTLMEngine: LiteRTLMEngine,
    private val calculatorService: CalculatorService,
    private val locationProvider: LocationProvider,
    private val tavilyRepository: TavilyRepository,
    private val gson: Gson
) {
    val contextManager = AgentEventContextManager()

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
        - Check the EVENT CONTEXT history to see previous tool results before calling tools again.
        - If you have enough info, set "action": "COMPLETE" and populate "finalAnswer".
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
        maxSteps: Int = 5,
        onStepUpdate: (step: Int, response: AgentResponseFormat) -> Unit
    ): String {
        // Record user input event
        contextManager.addEvent(AgentEvent.UserInputEvent(message = userPrompt))

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

        var currentStep = 0
        while (currentStep < maxSteps) {
            currentStep++
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

            when (parsedResponse.action) {
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
                    val msg = parsedResponse.finalAnswer ?: "Workflow ended with state ${parsedResponse.action}"
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
                        response.results.joinToString("\n") { "- ${it.title}: ${it.url}" }
                    },
                    onFailure = { "Web search failed: ${it.localizedMessage}" }
                )
            }
            else -> "Error: Unknown tool $toolName"
        }
    }

    /**
     * Parses the LLM raw output into AgentResponseFormat JSON schema.
     */
    private fun parseAgentResponse(rawText: String): AgentResponseFormat {
        return try {
            val jsonStart = rawText.indexOf('{')
            val jsonEnd = rawText.lastIndexOf('}')
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                val jsonString = rawText.substring(jsonStart, jsonEnd + 1)
                gson.fromJson(jsonString, AgentResponseFormat::class.java)
            } else {
                AgentResponseFormat(
                    thought = "Failed to parse JSON responseFormat",
                    action = AgentAction.COMPLETE,
                    finalAnswer = rawText
                )
            }
        } catch (e: Exception) {
            Log.e("WorkflowAgent", "JSON parsing error: ${e.localizedMessage}")
            AgentResponseFormat(
                thought = "JSON parse exception",
                action = AgentAction.COMPLETE,
                finalAnswer = rawText
            )
        }
    }
}
