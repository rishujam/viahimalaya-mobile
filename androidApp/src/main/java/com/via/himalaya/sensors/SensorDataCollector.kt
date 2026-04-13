package com.via.himalaya.sensors

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
import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.RawSensors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

class SensorDataCollector(private val context: Context) : SensorEventListener, LocationListener {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    
    // Sensors
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
    private val humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
    
    // Current sensor data
    private var currentAccelerometer: FloatArray? = null
    private var currentGyroscope: FloatArray? = null
    private var currentMagnetometer: FloatArray? = null
    private var currentPressure: Float? = null
    private var currentTemperature: Float? = null
    private var currentHumidity: Float? = null
    private var currentLocation: Location? = null
    
    // State flows for real-time updates
    private val _currentPoint = MutableStateFlow<Point?>(null)
    val currentPoint: StateFlow<Point?> = _currentPoint.asStateFlow()
    
    private val _sensorStatus = MutableStateFlow<SensorStatus>(SensorStatus.Stopped)
    val sensorStatus: StateFlow<SensorStatus> = _sensorStatus.asStateFlow()
    
    private var isCollecting = false
    
    data class SensorStatus(
        val isCollecting: Boolean = false,
        val hasLocationPermission: Boolean = false,
        val hasGpsSignal: Boolean = false,
        val availableSensors: List<String> = emptyList(),
        val lastUpdate: String = "Never"
    ) {
        companion object {
            val Stopped = SensorStatus()
        }
    }
    
    fun startCollection() {
        if (isCollecting) return
        
        Log.d("SensorCollector", "🚀 Starting sensor data collection...")
        
        // Check available sensors and log them
        val availableSensors = mutableListOf<String>()
        
        accelerometer?.let { 
            availableSensors.add("Accelerometer")
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("SensorCollector", "✅ Accelerometer registered")
        } ?: Log.w("SensorCollector", "❌ Accelerometer not available")
        
        gyroscope?.let { 
            availableSensors.add("Gyroscope")
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("SensorCollector", "✅ Gyroscope registered")
        } ?: Log.w("SensorCollector", "❌ Gyroscope not available")
        
        magnetometer?.let { 
            availableSensors.add("Magnetometer")
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("SensorCollector", "✅ Magnetometer registered")
        } ?: Log.w("SensorCollector", "❌ Magnetometer not available")
        
        pressureSensor?.let { 
            availableSensors.add("Pressure")
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("SensorCollector", "✅ Pressure sensor registered")
        } ?: Log.w("SensorCollector", "❌ Pressure sensor not available")
        
        temperatureSensor?.let { 
            availableSensors.add("Temperature")
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("SensorCollector", "✅ Temperature sensor registered")
        } ?: Log.w("SensorCollector", "❌ Temperature sensor not available")
        
        humiditySensor?.let { 
            availableSensors.add("Humidity")
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("SensorCollector", "✅ Humidity sensor registered")
        } ?: Log.w("SensorCollector", "❌ Humidity sensor not available")
        
        // Start location updates
        startLocationUpdates()
        
        isCollecting = true
        _sensorStatus.value = SensorStatus(
            isCollecting = true,
            hasLocationPermission = hasLocationPermission(),
            availableSensors = availableSensors,
            lastUpdate = "Starting..."
        )
        
        Log.d("SensorCollector", "📊 Available sensors: ${availableSensors.joinToString(", ")}")
    }
    
    fun stopCollection() {
        if (!isCollecting) return
        
        Log.d("SensorCollector", "🛑 Stopping sensor data collection...")
        
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
        
        isCollecting = false
        _sensorStatus.value = SensorStatus.Stopped
        _currentPoint.value = null
        
        Log.d("SensorCollector", "✅ Sensor collection stopped")
    }
    
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w("SensorCollector", "❌ Location permission not granted")
            return
        }
        
        try {
            // Request location updates from both GPS and Network providers
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // 1 second
                    1f,    // 1 meter
                    this
                )
                Log.d("SensorCollector", "✅ GPS location updates requested")
            }
            
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    1f,
                    this
                )
                Log.d("SensorCollector", "✅ Network location updates requested")
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
                Log.d("SensorCollector", "📍 Last known location: ${it.latitude}, ${it.longitude}")
                onLocationChanged(it)
            }
            
        } catch (e: SecurityException) {
            Log.e("SensorCollector", "❌ Security exception requesting location updates", e)
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
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                currentAccelerometer = event.values.clone()
                Log.v("SensorCollector", "📱 Accelerometer: ${event.values.contentToString()}")
            }
            Sensor.TYPE_GYROSCOPE -> {
                currentGyroscope = event.values.clone()
                Log.v("SensorCollector", "🌀 Gyroscope: ${event.values.contentToString()}")
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                currentMagnetometer = event.values.clone()
                Log.v("SensorCollector", "🧭 Magnetometer: ${event.values.contentToString()}")
            }
            Sensor.TYPE_PRESSURE -> {
                currentPressure = event.values[0]
                Log.v("SensorCollector", "🌡️ Pressure: ${event.values[0]} hPa")
                
                // Calculate barometric altitude
                val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0])
                Log.v("SensorCollector", "🏔️ Barometric altitude: $altitude m")
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                currentTemperature = event.values[0]
                Log.v("SensorCollector", "🌡️ Temperature: ${event.values[0]}°C")
            }
            Sensor.TYPE_RELATIVE_HUMIDITY -> {
                currentHumidity = event.values[0]
                Log.v("SensorCollector", "💧 Humidity: ${event.values[0]}%")
            }
        }
        
        // Update current point with latest sensor data
        updateCurrentPoint()
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        val accuracyText = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE"
            else -> "UNKNOWN"
        }
        Log.d("SensorCollector", "📊 ${sensor.name} accuracy changed to: $accuracyText")
    }
    
    override fun onLocationChanged(location: Location) {
        currentLocation = location
        
        Log.d("SensorCollector", "📍 Location updated:")
        Log.d("SensorCollector", "   Lat: ${location.latitude}")
        Log.d("SensorCollector", "   Lon: ${location.longitude}")
        Log.d("SensorCollector", "   Alt: ${location.altitude}m")
        Log.d("SensorCollector", "   Accuracy: ${location.accuracy}m")
        Log.d("SensorCollector", "   Speed: ${location.speed}m/s")
        Log.d("SensorCollector", "   Bearing: ${location.bearing}°")
        Log.d("SensorCollector", "   Provider: ${location.provider}")
        
        updateCurrentPoint()
        
        _sensorStatus.value = _sensorStatus.value.copy(
            hasGpsSignal = true,
            lastUpdate = "Location: ${location.latitude}, ${location.longitude}"
        )
    }
    
    @Deprecated("Deprecated in API level 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
        Log.d("SensorCollector", "📡 Location provider $provider status changed to: $status")
    }
    
    override fun onProviderEnabled(provider: String) {
        Log.d("SensorCollector", "✅ Location provider enabled: $provider")
    }
    
    override fun onProviderDisabled(provider: String) {
        Log.w("SensorCollector", "❌ Location provider disabled: $provider")
    }
    
    private fun updateCurrentPoint() {
        val location = currentLocation ?: return
        
        // Get battery level
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        // Calculate barometric altitude if pressure sensor is available
        val baroAltitude = currentPressure?.let { pressure ->
            SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
        }
        
        // Create RawSensors object
        val rawSensors = RawSensors(
            accelerometerX = currentAccelerometer?.get(0)?.toDouble(),
            accelerometerY = currentAccelerometer?.get(1)?.toDouble(),
            accelerometerZ = currentAccelerometer?.get(2)?.toDouble(),
            gyroscopeX = currentGyroscope?.get(0)?.toDouble(),
            gyroscopeY = currentGyroscope?.get(1)?.toDouble(),
            gyroscopeZ = currentGyroscope?.get(2)?.toDouble(),
            magnetometerX = currentMagnetometer?.get(0)?.toDouble(),
            magnetometerY = currentMagnetometer?.get(1)?.toDouble(),
            magnetometerZ = currentMagnetometer?.get(2)?.toDouble(),
            pressure = currentPressure?.toDouble(),
            temperature = currentTemperature?.toDouble(),
            humidity = currentHumidity?.toDouble()
        )
        
        // Create Point object
        val point = Point(
            trekId = "", // Will be set when saving to a specific trek
            lat = location.latitude,
            lon = location.longitude,
            timestamp = Clock.System.now(),
            altGps = if (location.hasAltitude()) location.altitude else null,
            altBaro = baroAltitude?.toDouble(),
            accuracyH = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            accuracyV = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else null,
            speed = if (location.hasSpeed()) location.speed.toDouble() else null,
            bearing = if (location.hasBearing()) location.bearing.toDouble() else null,
            battery = batteryLevel,
            rawSensors = rawSensors
        )
        
        _currentPoint.value = point
        
        // Log the complete point data
        Log.d("SensorCollector", "📊 Complete Point Data:")
        Log.d("SensorCollector", "   📍 GPS: ${point.lat}, ${point.lon}")
        Log.d("SensorCollector", "   🏔️ Altitude GPS: ${point.altGps}m")
        Log.d("SensorCollector", "   🏔️ Altitude Baro: ${point.altBaro}m")
        Log.d("SensorCollector", "   🎯 Accuracy H: ${point.accuracyH}m")
        Log.d("SensorCollector", "   🎯 Accuracy V: ${point.accuracyV}m")
        Log.d("SensorCollector", "   🏃 Speed: ${point.speed}m/s")
        Log.d("SensorCollector", "   🧭 Bearing: ${point.bearing}°")
        Log.d("SensorCollector", "   🔋 Battery: ${point.battery}%")
        Log.d("SensorCollector", "   📱 Sensors: $rawSensors")
    }
    
    fun getCurrentPointData(): Point? = _currentPoint.value
    
    fun getSensorStatus(): SensorStatus = _sensorStatus.value
}