package com.via.himalaya

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.via.himalaya.di.AppModuleFactory
import com.via.himalaya.permissions.PermissionHandler
import com.via.himalaya.sensors.SensorDataCollector
import kotlinx.coroutines.launch

@Composable
fun RealSensorTestScreen(
    permissionHandler: PermissionHandler
) {
    val context = LocalContext.current
    val appModule = AppModuleFactory.create()
    val repository = appModule.trekRepository
    val scope = rememberCoroutineScope()
    
    // Initialize sensor collector
    val sensorCollector = remember { SensorDataCollector(context) }

    var currentTrekId by remember { mutableStateOf<String?>(null) }
    var isTracking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to start real sensor tracking") }
    
    // Collect permission status
    val permissionStatus by permissionHandler.permissionStatus.collectAsStateWithLifecycle()
    
    // Collect sensor status
    val sensorStatus by sensorCollector.sensorStatus.collectAsStateWithLifecycle()
    
    // Collect current point data
    val currentPoint by sensorCollector.currentPoint.collectAsStateWithLifecycle()
    
    // Auto-save points when tracking
    LaunchedEffect(currentPoint, currentTrekId, isTracking) {
        if (isTracking && currentPoint != null && currentTrekId != null) {
            try {
                val pointWithTrekId = currentPoint!!.copy(trekId = currentTrekId!!)
                repository.savePoint(currentTrekId!!, pointWithTrekId)
                statusMessage = "✅ Point saved: ${currentPoint!!.lat}, ${currentPoint!!.lon}"
            } catch (e: Exception) {
                statusMessage = "❌ Error saving point: ${e.message}"
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "🛰️ Real Sensor Data Collection",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Permission Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (permissionStatus.hasLocationPermission) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📍 Permissions",
                    fontWeight = FontWeight.Bold
                )
                Text(text = permissionHandler.getPermissionStatusText())
                
                if (!permissionStatus.hasLocationPermission) {
                    Button(
                        onClick = { permissionHandler.checkAndRequestPermissions() },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Grant Permissions")
                    }
                }
            }
        }
        
        // Sensor Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (sensorStatus.isCollecting) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📱 Sensor Status",
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Collecting: ${if (sensorStatus.isCollecting) "✅" else "❌"}")
                Text(text = "GPS Signal: ${if (sensorStatus.hasGpsSignal) "✅" else "❌"}")
                Text(text = "Available: ${sensorStatus.availableSensors.joinToString(", ")}")
                Text(text = "Last Update: ${sensorStatus.lastUpdate}")
            }
        }
        
        // Current Point Data
        currentPoint?.let { point ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📊 Live Sensor Data",
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "📍 Location: ${point.lat}, ${point.lon}")
                    point.altGps?.let { Text(text = "🏔️ GPS Alt: ${it}m") }
                    point.altBaro?.let { Text(text = "🏔️ Baro Alt: ${it}m") }
                    point.speed?.let { Text(text = "🏃 Speed: ${it}m/s") }
                    point.bearing?.let { Text(text = "🧭 Bearing: ${it}°") }
                    point.battery?.let { Text(text = "🔋 Battery: ${it}%") }
                    point.accuracyH?.let { Text(text = "🎯 Accuracy: ${it}m") }
                    
                    point.rawSensors?.let { sensors ->
                        Text(text = "📱 Accelerometer: ${sensors.accelerometerX}, ${sensors.accelerometerY}, ${sensors.accelerometerZ}")
                        sensors.pressure?.let { Text(text = "🌡️ Pressure: ${it} hPa") }
                        sensors.temperature?.let { Text(text = "🌡️ Temperature: ${it}°C") }
                    }
                }
            }
        }
        
        // Status Message
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = statusMessage,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (permissionStatus.hasLocationPermission) {
                        sensorCollector.startCollection()
                        statusMessage = "🚀 Started sensor collection"
                    } else {
                        statusMessage = "❌ Location permission required"
                        permissionHandler.checkAndRequestPermissions()
                    }
                },
                enabled = !sensorStatus.isCollecting && permissionStatus.hasLocationPermission,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Sensors")
            }
            
            Button(
                onClick = {
                    sensorCollector.stopCollection()
                    statusMessage = "🛑 Stopped sensor collection"
                },
                enabled = sensorStatus.isCollecting,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop Sensors")
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val trekId = repository.startNewTrek("Real Sensor Trek", "real-guide-001")
                            currentTrekId = trekId
                            isTracking = true
                            statusMessage = "🎯 Started tracking trek: $trekId"
                        } catch (e: Exception) {
                            statusMessage = "❌ Error starting trek: ${e.message}"
                        }
                    }
                },
                enabled = sensorStatus.isCollecting && !isTracking,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Trek")
            }
            
            Button(
                onClick = {
                    isTracking = false
                    currentTrekId = null
                    statusMessage = "🏁 Stopped trek tracking"
                },
                enabled = isTracking,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop Trek")
            }
        }
    }
    
    // Cleanup when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            sensorCollector.stopCollection()
        }
    }
}
