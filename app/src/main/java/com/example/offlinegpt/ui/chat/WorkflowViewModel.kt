package com.example.offlinegpt.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.agent.AgentEvent
import com.example.offlinegpt.data.agent.WorkflowAgent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkflowUiState(
    val isRunning: Boolean = false,
    val currentStep: Int = 0,
    val events: List<AgentEvent> = emptyList(),
    val latestThought: String? = null,
    val finalAnswer: String? = null,
    val error: String? = null
)

@HiltViewModel
class WorkflowViewModel @Inject constructor(
    private val workflowAgent: WorkflowAgent
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowUiState())
    val uiState: StateFlow<WorkflowUiState> = _uiState.asStateFlow()

    fun runWorkflow(userQuery: String) {
        if (userQuery.isBlank() || _uiState.value.isRunning) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunning = true,
                currentStep = 0,
                latestThought = null,
                finalAnswer = null,
                error = null
            )

            try {
                val finalAnswer = workflowAgent.runWorkflow(
                    userPrompt = userQuery,
                    maxSteps = 15,
                    onStepUpdate = { step, responseFormat ->
                        _uiState.value = _uiState.value.copy(
                            currentStep = step,
                            events = workflowAgent.contextManager.events,
                            latestThought = responseFormat.thought
                        )
                    }
                )

                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    events = workflowAgent.contextManager.events,
                    finalAnswer = finalAnswer
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    error = e.localizedMessage ?: "Unknown error occurred during workflow execution."
                )
            }
        }
    }

    fun clearWorkflow() {
        workflowAgent.contextManager.clear()
        _uiState.value = WorkflowUiState()
    }
}
