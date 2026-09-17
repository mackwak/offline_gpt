package com.example.offlinegpt.ui.mbti

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.local.MBTIResult
import com.example.offlinegpt.data.mbti.MBTIQuestion
import com.example.offlinegpt.data.mbti.MbtiTrait
import com.example.offlinegpt.data.repository.MbtiRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MbtiViewModel @Inject constructor(
    private val repository: MbtiRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val questions = repository.getQuestions()
    private val answers = mutableMapOf<Int, Int>() // questionId to score (-2 to 2)

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _uiState = MutableStateFlow<MbtiUiState>(MbtiUiState.Assessment)
    val uiState: StateFlow<MbtiUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<MBTIResult>> = repository.getHistory(userEmail)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val userEmail: String
        get() = auth.currentUser?.email ?: "anonymous"

    val currentQuestion: StateFlow<MBTIQuestion?> = _currentQuestionIndex
        .map { index -> if (index < questions.size) questions[index] else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), questions.getOrNull(0))

    val progress: StateFlow<Float> = _currentQuestionIndex
        .map { index -> if (questions.isNotEmpty()) (index.toFloat() / questions.size.toFloat()) else 0f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    fun onAnswerSelected(score: Int) {
        val question = currentQuestion.value ?: return
        answers[question.id] = score * question.direction

        if (_currentQuestionIndex.value < questions.size - 1) {
            _currentQuestionIndex.value += 1
        } else {
            calculateAndSaveResult()
        }
    }

    private fun calculateAndSaveResult() {
        val traitScores = mutableMapOf<MbtiTrait, Int>().withDefault { 0 }
        
        questions.forEach { q ->
            val score = answers[q.id] ?: 0
            traitScores[q.trait] = traitScores.getValue(q.trait) + score
        }

        val mbtiType = StringBuilder()
        mbtiType.append(if (traitScores.getValue(MbtiTrait.E_I) >= 0) "E" else "I")
        mbtiType.append(if (traitScores.getValue(MbtiTrait.S_N) >= 0) "S" else "N")
        mbtiType.append(if (traitScores.getValue(MbtiTrait.T_F) >= 0) "T" else "F")
        mbtiType.append(if (traitScores.getValue(MbtiTrait.J_P) >= 0) "J" else "P")

        val result = MBTIResult(
            userEmail = userEmail,
            mbtiType = mbtiType.toString(),
            extroversionScore = traitScores.getValue(MbtiTrait.E_I).coerceAtLeast(0),
            introversionScore = if (traitScores.getValue(MbtiTrait.E_I) < 0) -traitScores.getValue(MbtiTrait.E_I) else 0,
            sensingScore = traitScores.getValue(MbtiTrait.S_N).coerceAtLeast(0),
            intuitionScore = if (traitScores.getValue(MbtiTrait.S_N) < 0) -traitScores.getValue(MbtiTrait.S_N) else 0,
            thinkingScore = traitScores.getValue(MbtiTrait.T_F).coerceAtLeast(0),
            feelingScore = if (traitScores.getValue(MbtiTrait.T_F) < 0) -traitScores.getValue(MbtiTrait.T_F) else 0,
            judgingScore = traitScores.getValue(MbtiTrait.J_P).coerceAtLeast(0),
            perceivingScore = if (traitScores.getValue(MbtiTrait.J_P) < 0) -traitScores.getValue(MbtiTrait.J_P) else 0
        )

        viewModelScope.launch {
            repository.saveResult(result)
            _uiState.value = MbtiUiState.Finished(result)
        }
    }

    fun restart() {
        _currentQuestionIndex.value = 0
        answers.clear()
        _uiState.value = MbtiUiState.Assessment
    }
}

sealed class MbtiUiState {
    object Assessment : MbtiUiState()
    data class Finished(val result: MBTIResult) : MbtiUiState()
}
