package com.example.offlinegpt.ui.calculator

sealed class OperationResult {
    data class Congratulations(val result: String) : OperationResult()
    data class Failed(val error: String) : OperationResult()
}
