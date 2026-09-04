package com.example.offlinegpt.ui.compass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.sensor.CompassSensorManager
import com.example.offlinegpt.util.SolarCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CompassViewModel @Inject constructor(
    compassSensorManager: CompassSensorManager
) : ViewModel() {

    val azimuth: StateFlow<Float> = compassSensorManager.getAzimuthFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0f
        )

    // Sun azimuth relative to North (0-360)
    // Defaulting to a central location (e.g., San Francisco) if GPS is not used
    private val defaultLat = 37.7749
    private val defaultLon = -122.4194

    val sunAzimuth: StateFlow<Float> = flow {
        while (true) {
            val position = SolarCalculator.calculateSunPosition(defaultLat, defaultLon)
            emit(position.azimuth.toFloat())
            delay(60000) // Update sun position every minute
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0f
    )
}
