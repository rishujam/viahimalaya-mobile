package com.via.himalaya

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.via.himalaya.data.models.Loc
import com.via.himalaya.data.models.SensorData

class SensorListener(
    private val context: Context,
    private val onLocationChange: (SensorData) -> Unit
) : SensorEventListener, LocationListener {

    companion object {
        private const val TAG = "SensorListener"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
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

    override fun onLocationChanged(location: Location) {
        Log.d("LocationChange", "LocationChanged: $location")
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val barAltitude = currentPressure?.let { pressure ->
            SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
        }
        val accV = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
            location.verticalAccuracyMeters.toDouble()
        } else null
        val loc = Loc(
            lat = location.latitude,
            lon = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accH = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            accV = accV,
            speed = if (location.hasSpeed()) location.speed.toDouble() else null
        )
        onLocationChange(
            SensorData(
                accelerometer = currentAccelerometer,
                gyroscope = currentGyroscope,
                magnetometer = currentMagnetometer,
                pressure = currentPressure,
                altBaro = barAltitude,
                location = loc,
                batteryLevel
            )
        )
    }

    fun startListening() {
        if(isListening) return

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)

        registerLocationListener()

        isListening = true
    }

    fun stopListening() {
        if(!isListening) return
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
    }

    private fun registerLocationListener() {
        if(!hasLocationPermission()) return

        try {
            // Request location updates from both GPS and Network providers
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000L, // 5 second
                    10f,    // 10 meter
                    this
                )
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    10f,
                    this
                )
            }

            // Get last known location
            val lastKnownGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastKnownNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            val lastKnown = when {
                lastKnownGps != null && lastKnownNetwork != null -> {
                    if (lastKnownGps.time > lastKnownNetwork.time) lastKnownGps else lastKnownNetwork
                }
                lastKnownGps != null -> lastKnownGps
                lastKnownNetwork != null -> lastKnownNetwork
                else -> null
            }

            lastKnown?.let {
                Log.d(TAG, "📍 Last known location: ${it.latitude}, ${it.longitude}")
                onLocationChanged(it)
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception requesting location updates", e)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}