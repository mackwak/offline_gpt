package com.example.offlinegpt.di

import com.example.offlinegpt.data.calculator.CalculatorService
import com.example.offlinegpt.data.calculator.CalculatorServiceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CalculatorModule {

    @Provides
    @Singleton
    fun provideCalculatorService(): CalculatorService {
        return CalculatorServiceImpl()
    }
}
