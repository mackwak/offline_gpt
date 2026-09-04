package com.example.offlinegpt.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

interface CompassSensorManager {
    fun getAzimuthFlow(): Flow<Float>
}

@Singleton
class CompassSensorManagerImpl @Inject constructor(
    @ApplicationContext context: Context
) : CompassSensorManager, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var _accelerometerReading = FloatArray(3)
    private var _magnetometerReading = FloatArray(3)

    private var _onAzimuthChanged: ((Float) -> Unit)? = null

    override fun getAzimuthFlow(): Flow<Float> = callbackFlow {
        _onAzimuthChanged = { azimuth ->
            trySend(azimuth)
        }

        sensorManager.registerListener(this@CompassSensorManagerImpl, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this@CompassSensorManagerImpl, magnetometer, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(this@CompassSensorManagerImpl)
            _onAzimuthChanged = null
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, _accelerometerReading, 0, _accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, _magnetometerReading, 0, _magnetometerReading.size)
        }

        updateAzimuth()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateAzimuth() {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        if (SensorManager.getRotationMatrix(rotationMatrix, null, _accelerometerReading, _magnetometerReading)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthInRadians = orientationAngles[0]
            val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
            _onAzimuthChanged?.invoke(azimuthInDegrees)
        }
    }
}
