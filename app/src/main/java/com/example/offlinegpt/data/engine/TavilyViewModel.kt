package com.example.offlinegpt.data.engine

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.TavilyResult
import com.example.offlinegpt.data.TavilySearchRequest
import com.example.offlinegpt.di.TavilyApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Named
import javax.inject.Singleton

// Response Body
data class TavilySearchResponse(
    val query: String,
    val answer: String?,
    val results: List<TavilyResult>
)

sealed interface TavilyUiState {
    object Idle : TavilyUiState
    object Loading : TavilyUiState
    data class Success(val data: TavilySearchResponse) : TavilyUiState
    data class Error(val message: String) : TavilyUiState
}

@HiltViewModel
class TavilyViewModel @Inject constructor(
    private val repository: TavilyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TavilyUiState>(TavilyUiState.Idle)
    val uiState: StateFlow<TavilyUiState> = _uiState.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = TavilyUiState.Loading
            val result = repository.performSearch(query)

            _uiState.value = result.fold(
                onSuccess = { TavilyUiState.Success(it) },
                onFailure = { TavilyUiState.Error(it.localizedMessage ?: "Unknown error occurred") }
            )
        }
    }
}



@Singleton
class TavilyRepository @Inject constructor(
    private val api: TavilyApiService
) {
    suspend fun performSearch(query: String): Result<TavilySearchResponse> {
        return try {
            val response = api.search(
                apiKey = "Bearer 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",
                request = TavilySearchRequest(query = query)
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error: ${response.errorBody()?.string() ?: response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}