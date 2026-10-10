package com.example.offlinegpt.di

import com.example.offlinegpt.data.sensor.CompassSensorManager
import com.example.offlinegpt.data.sensor.CompassSensorManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds
    @Singleton
    abstract fun bindCompassSensorManager(
        compassSensorManagerImpl: CompassSensorManagerImpl
    ): CompassSensorManager
}
