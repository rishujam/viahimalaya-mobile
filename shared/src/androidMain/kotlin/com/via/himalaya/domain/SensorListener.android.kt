package com.via.himalaya.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.util.Log
import com.via.himalaya.data.models.SensorData

class AndroidSensorListener(
    private val context: Context
) : SensorListener, SensorEventListener {

    companion object {
        private const val TAG = "SensorListener"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    // Sensors
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private var currentAccelerometer: FloatArray? = null
    private var currentGyroscope: FloatArray? = null
    private var currentMagnetometer: FloatArray? = null
    private var currentPressure: Float? = null

    private var isListening = false

    override fun getSensorData(): SensorData {
        val barAltitude = currentPressure?.let { pressure ->
            SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
        }
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return SensorData(
            accelerometer = currentAccelerometer,
            gyroscope = currentGyroscope,
            magnetometer = currentMagnetometer,
            pressure = currentPressure,
            altBaro = barAltitude,
            battery = batteryLevel
        )
    }


    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                currentAccelerometer = event.values.clone()
                Log.v(TAG, "📱 Accelerometer: ${event.values.contentToString()}")
            }
            Sensor.TYPE_GYROSCOPE -> {
                currentGyroscope = event.values.clone()
                Log.v(TAG, "🌀 Gyroscope: ${event.values.contentToString()}")
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                currentMagnetometer = event.values.clone()
                Log.v(TAG, "🧭 Magnetometer: ${event.values.contentToString()}")
            }
            Sensor.TYPE_PRESSURE -> {
                currentPressure = event.values[0]
                Log.v(TAG, "🌡️ Pressure: ${event.values[0]} hPa")
                // Calculate barometric altitude
                val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0])
                Log.v(TAG, "🏔️ Barometric altitude: $altitude m")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val accuracyText = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE"
            else -> "UNKNOWN"
        }
        Log.d(TAG, "📊 ${sensor?.name} accuracy changed to: $accuracyText")
    }

    override fun startListening() {
        if(isListening) return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        isListening = true
    }

    override fun stopListening() {
        if(!isListening) return
        sensorManager.unregisterListener(this)
    }

}