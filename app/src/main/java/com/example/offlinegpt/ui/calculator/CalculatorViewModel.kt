package com.example.offlinegpt.ui.calculator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.offlinegpt.data.calculator.CalculatorService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val calculatorService: CalculatorService
) : ViewModel() {

    var number1 by mutableStateOf("")
    var number2 by mutableStateOf("")
    var operationResult by mutableStateOf<OperationResult?>(null)
        private set

    fun onPlusClick() {
        val n1 = number1.toDoubleOrNull()
        val n2 = number2.toDoubleOrNull()
        
        if (n1 == null || n2 == null) {
            operationResult = OperationResult.Failed("Invalid input")
            return
        }
        
        val res = calculatorService.plus(n1, n2)
        operationResult = OperationResult.Congratulations(res.toString())
    }

    fun onMinusClick() {
        val n1 = number1.toDoubleOrNull()
        val n2 = number2.toDoubleOrNull()

        if (n1 == null || n2 == null) {
            operationResult = OperationResult.Failed("Invalid input")
            return
        }

        val res = calculatorService.minus(n1, n2)
        operationResult = OperationResult.Congratulations(res.toString())
    }
}
