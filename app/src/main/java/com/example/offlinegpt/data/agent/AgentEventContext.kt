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
     * Formats all recorded events into a structured text context for the Agent prompt.
     */
    fun buildContextPrompt(): String {
        if (_events.isEmpty()) return "No prior events in context."

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder("=== EVENT CONTEXT HISTORY ===\n")

        _events.forEachIndexed { index, event ->
            val timeStr = dateFormat.format(Date(event.timestamp))
            sb.append("[$index] [$timeStr] ")
            when (event) {
                is AgentEvent.UserInputEvent -> {
                    sb.append("USER_INPUT: \"${event.message}\"\n")
                }
                is AgentEvent.LocationEvent -> {
                    sb.append("LOCATION: Address=\"${event.address ?: "Unknown"}\" (Lat: ${event.latitude}, Lon: ${event.longitude})\n")
                }
                is AgentEvent.ToolExecutionEvent -> {
                    val status = if (event.isSuccess) "SUCCESS" else "FAILED"
                    sb.append("TOOL_EXECUTION ($status): tool=\"${event.toolName}\", args=\"${event.args}\", result=\"${event.result}\"\n")
                }
                is AgentEvent.AgentThoughtEvent -> {
                    sb.append("AGENT_THOUGHT: \"${event.thought}\"\n")
                }
                is AgentEvent.SystemStateEvent -> {
                    sb.append("SYSTEM_STATE [${event.stateName}]: \"${event.description}\"\n")
                }
            }
        }
        sb.append("=============================")
        return sb.toString()
    }
}
