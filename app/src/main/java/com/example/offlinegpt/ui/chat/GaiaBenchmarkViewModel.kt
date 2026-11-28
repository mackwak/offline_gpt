package com.example.offlinegpt.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.agent.WorkflowAgent
import com.example.offlinegpt.data.repository.GaiaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GaiaBenchmarkResult(
    val total: Int,
    val correct: Int,
    val accuracy: Float,
    val logs: List<String>
)

@HiltViewModel
class GaiaBenchmarkViewModel @Inject constructor(
    private val gaiaRepository: GaiaRepository,
    private val workflowAgent: WorkflowAgent
) : ViewModel() {

    private val _isGaiaDownloaded = MutableStateFlow(gaiaRepository.isGaiaDatasetDownloaded())
    val isGaiaDownloaded: StateFlow<Boolean> = _isGaiaDownloaded.asStateFlow()

    private val _isGaiaDownloading = MutableStateFlow(false)
    val isGaiaDownloading: StateFlow<Boolean> = _isGaiaDownloading.asStateFlow()

    private val _downloadStatus = MutableStateFlow<String?>(null)
    val downloadStatus: StateFlow<String?> = _downloadStatus.asStateFlow()

    private val _isBenchmarkRunning = MutableStateFlow(false)
    val isBenchmarkRunning: StateFlow<Boolean> = _isBenchmarkRunning.asStateFlow()

    private val _benchmarkResult = MutableStateFlow<GaiaBenchmarkResult?>(null)
    val benchmarkResult: StateFlow<GaiaBenchmarkResult?> = _benchmarkResult.asStateFlow()

    fun downloadGaiaDataset() {
        if (_isGaiaDownloading.value || _isGaiaDownloaded.value) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isGaiaDownloading.value = true
            _downloadStatus.value = "Downloading GAIA validation problems from Hugging Face..."

            gaiaRepository.downloadGaiaValidationDataset()
                .onSuccess { file ->
                    _isGaiaDownloaded.value = true
                    _downloadStatus.value = "GAIA downloaded: ${file.name}"
                }
                .onFailure { error ->
                    _downloadStatus.value = "GAIA download failed: ${error.localizedMessage ?: "unknown error"}"
                }

            _isGaiaDownloading.value = false
        }
    }

    fun runGaiaBenchmark(maxQuestions: Int = 5) {
        if (_isBenchmarkRunning.value) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _benchmarkResult.value = null

            if (!gaiaRepository.isGaiaDatasetDownloaded()) {
                _downloadStatus.value = "Download GAIA dataset first."
                return@launch
            }

            _isBenchmarkRunning.value = true
            val problems = gaiaRepository.loadValidationProblems(limit = maxQuestions)

            if (problems.isEmpty()) {
                _downloadStatus.value = "No GAIA problems found in downloaded file."
                _isBenchmarkRunning.value = false
                return@launch
            }

            var correct = 0
            val logs = mutableListOf<String>()

            problems.forEachIndexed { index, problem ->
                workflowAgent.contextManager.clear()

                val predicted = runCatching {
                    workflowAgent.runWorkflow(
                        userPrompt = problem.question,
                        maxSteps = 10,
                        onStepUpdate = { _, _ -> }
                    )
                }.getOrElse { error ->
                    "ERROR: ${error.localizedMessage ?: "workflow failed"}"
                }

                val matched = isAnswerMatch(predicted, problem.expectedAnswer)

                Log.d("MAC", "Comparing $predicted to $problem.expectedAnswer => $matched")

                if (matched) {
                    correct += 1
                }

                logs.add(
                    "#${index + 1} (${problem.id}) => ${if (matched) "PASS" else "FAIL"}\n" +
                            "Q: ${problem.question.take(90)}\n" +
                            "Expected: ${problem.expectedAnswer.take(80)}\n" +
                            "Predicted: ${predicted.take(80)}"
                )
            }

            val accuracy = if (problems.isNotEmpty()) correct.toFloat() / problems.size else 0f
            _benchmarkResult.value = GaiaBenchmarkResult(
                total = problems.size,
                correct = correct,
                accuracy = accuracy,
                logs = logs
            )

            _isBenchmarkRunning.value = false
        }
    }

    private fun isAnswerMatch(predicted: String, expected: String): Boolean {
        val normalizedPred = normalizeAnswer(predicted)
        val normalizedExpected = normalizeAnswer(expected)

        if (normalizedPred.isBlank() || normalizedExpected.isBlank()) {
            return false
        }

        return normalizedPred == normalizedExpected ||
                normalizedPred.contains(normalizedExpected) ||
                normalizedExpected.contains(normalizedPred)
    }

    private fun normalizeAnswer(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }
}


