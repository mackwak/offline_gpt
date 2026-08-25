package com.example.offlinegpt.data.calculator

interface CalculatorService {
    fun plus(a: Double, b: Double): Double
    fun minus(a: Double, b: Double): Double
}

class CalculatorServiceImpl : CalculatorService {
    override fun plus(a: Double, b: Double): Double = a + b
    override fun minus(a: Double, b: Double): Double = a - b
}
