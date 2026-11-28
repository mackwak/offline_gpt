package com.example.offlinegpt.data.agent

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents an event in the AI Workflow context.
 */
sealed class AgentEvent {
    abstract val timestamp: Long

    data class UserInputEvent(
        override val timestamp: Long = System.currentTimeMillis(),
        val message: String
    ) : AgentEvent()

    data class LocationEvent(
        override val timestamp: Long = System.currentTimeMillis(),
        val latitude: Double,
        val longitude: Double,
        val address: String?
    ) : AgentEvent()

    data class ToolExecutionEvent(
        override val timestamp: Long = System.currentTimeMillis(),
        val toolName: String,
        val args: String,
        val result: String,
        val isSuccess: Boolean
    ) : AgentEvent()

    data class AgentThoughtEvent(
        override val timestamp: Long = System.currentTimeMillis(),
        val thought: String
    ) : AgentEvent()

    data class SystemStateEvent(
        val stateName: String,
        val description: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()
}

/**
 * Manages the accumulation and formatting of all workflow events into LLM Context.
 */
class AgentEventContextManager {

    private val _events = mutableListOf<AgentEvent>()
    val events: List<AgentEvent> get() = _events.toList()

    fun addEvent(event: AgentEvent) {
        _events.add(event)
    }

    fun clear() {
        _events.clear()
    }

    /**
     * Formats recorded events into a structured, token-safe text context for the Agent prompt.
     */
    fun buildContextPrompt(maxChars: Int = 2000): String {
        if (_events.isEmpty()) return "No prior events in context."

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder("=== EVENT CONTEXT HISTORY ===\n")

        // Retain only recent 10 events to prevent prefill token overflow
        val recentEvents = _events.takeLast(10)
        recentEvents.forEachIndexed { index, event ->
            val timeStr = dateFormat.format(Date(event.timestamp))
            sb.append("[$index] [$timeStr] ")
            when (event) {
                is AgentEvent.UserInputEvent -> {
                    val msg = if (event.message.length > 300) event.message.take(300) + "..." else event.message
                    sb.append("USER_INPUT: \"$msg\"\n")
                }
                is AgentEvent.LocationEvent -> {
                    sb.append("LOCATION: Address=\"${event.address ?: "Unknown"}\" (Lat: ${event.latitude}, Lon: ${event.longitude})\n")
                }
                is AgentEvent.ToolExecutionEvent -> {
                    val status = if (event.isSuccess) "SUCCESS" else "FAILED"
                    // Truncate long tool execution results
                    val truncatedResult = if (event.result.length > 350) event.result.take(350) + "... [truncated]" else event.result
                    sb.append("TOOL_EXECUTION ($status): tool=\"${event.toolName}\", args=\"${event.args}\", result=\"$truncatedResult\"\n")
                }
                is AgentEvent.AgentThoughtEvent -> {
                    val thought = if (event.thought.length > 250) event.thought.take(250) + "..." else event.thought
                    sb.append("AGENT_THOUGHT: \"$thought\"\n")
                }
                is AgentEvent.SystemStateEvent -> {
                    sb.append("SYSTEM_STATE [${event.stateName}]: \"${event.description}\"\n")
                }
            }
        }
        sb.append("=============================")

        val result = sb.toString()
        return if (result.length > maxChars) result.takeLast(maxChars) else result
    }
}
