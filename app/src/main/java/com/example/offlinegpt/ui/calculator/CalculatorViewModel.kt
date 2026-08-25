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
    var result by mutableStateOf("")
        private set

    fun onPlusClick() {
        val n1 = number1.toDoubleOrNull() ?: 0.0
        val n2 = number2.toDoubleOrNull() ?: 0.0
        result = calculatorService.plus(n1, n2).toString()
    }

    fun onMinusClick() {
        val n1 = number1.toDoubleOrNull() ?: 0.0
        val n2 = number2.toDoubleOrNull() ?: 0.0
        result = calculatorService.minus(n1, n2).toString()
    }
}
