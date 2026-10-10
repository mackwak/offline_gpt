package com.example.offlinegpt.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
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
    @ApplicationContext private val context: Context
) : CompassSensorManager, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var _accelerometerReading = FloatArray(3)
    private var _magnetometerReading = FloatArray(3)
    
    // Low-pass filter constant (0.0 to 1.0)
    // Smaller values mean more smoothing but more lag.
    private val alpha = 0.15f

    private var _onAzimuthChanged: ((Float) -> Unit)? = null

    override fun getAzimuthFlow(): Flow<Float> = callbackFlow {
        _onAzimuthChanged = { azimuth ->
            trySend(azimuth)
        }

        // Prioritize Rotation Vector (Sensor Fusion) for better accuracy and stability
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this@CompassSensorManagerImpl, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            // Fallback for older devices without rotation vector support
            sensorManager.registerListener(this@CompassSensorManagerImpl, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this@CompassSensorManagerImpl, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(this@CompassSensorManagerImpl)
            _onAzimuthChanged = null
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                calculateOrientation(rotationMatrix)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                _accelerometerReading = applyLowPassFilter(event.values, _accelerometerReading)
                updateFallbackOrientation()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                _magnetometerReading = applyLowPassFilter(event.values, _magnetometerReading)
                updateFallbackOrientation()
            }
        }
    }

    private fun applyLowPassFilter(input: FloatArray, output: FloatArray): FloatArray {
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
    }

    private fun updateFallbackOrientation() {
        val rotationMatrix = FloatArray(9)
        if (SensorManager.getRotationMatrix(rotationMatrix, null, _accelerometerReading, _magnetometerReading)) {
            calculateOrientation(rotationMatrix)
        }
    }

    private fun calculateOrientation(rotationMatrix: FloatArray) {
        val worldAxisForDeviceAxisX: Int
        val worldAxisForDeviceAxisY: Int

        // Handle device rotation (Portrait, Landscape, etc.) to keep North consistent
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val rotation = windowManager.defaultDisplay.rotation

        when (rotation) {
            Surface.ROTATION_0 -> {
                worldAxisForDeviceAxisX = SensorManager.AXIS_X
                worldAxisForDeviceAxisY = SensorManager.AXIS_Y
            }
            Surface.ROTATION_90 -> {
                worldAxisForDeviceAxisX = SensorManager.AXIS_Y
                worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X
            }
            Surface.ROTATION_180 -> {
                worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X
                worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Y
            }
            Surface.ROTATION_270 -> {
                worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Y
                worldAxisForDeviceAxisY = SensorManager.AXIS_X
            }
            else -> {
                worldAxisForDeviceAxisX = SensorManager.AXIS_X
                worldAxisForDeviceAxisY = SensorManager.AXIS_Y
            }
        }

        val adjustedRotationMatrix = FloatArray(9)
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            worldAxisForDeviceAxisX,
            worldAxisForDeviceAxisY,
            adjustedRotationMatrix
        )

        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(adjustedRotationMatrix, orientationAngles)
        
        val azimuthInRadians = orientationAngles[0]
        val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
        
        _onAzimuthChanged?.invoke(azimuthInDegrees)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
