package com.example.offlinegpt.data.agent

import com.google.gson.annotations.SerializedName

/**
 * Defines the structured JSON output format for the AI Agent.
 * Used for responseFormat constraint in LLM reasoning loops.
 */
enum class AgentAction {
    @SerializedName("EXECUTE_TOOL")
    EXECUTE_TOOL,

    @SerializedName("COMPLETE")
    COMPLETE,

    @SerializedName("NEED_MORE_INFO")
    NEED_MORE_INFO,

    @SerializedName("ERROR")
    ERROR
}

data class ToolCallData(
    @SerializedName("toolName") val toolName: String,
    @SerializedName("args") val args: Map<String, Any?> = emptyMap()
)

data class AgentResponseFormat(
    @SerializedName("thought") val thought: String? = null,
    @SerializedName("action") val action: AgentAction = AgentAction.COMPLETE,
    @SerializedName("toolCall") val toolCall: ToolCallData? = null,
    @SerializedName("finalAnswer") val finalAnswer: String? = null
)
