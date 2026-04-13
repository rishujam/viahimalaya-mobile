# LocationBufferManager Usage Guide

## Overview

The LocationBufferManager is a comprehensive solution for handling GPS location data in the ViaHimalaya mobile app. It provides:

- **Distance Filtering**: Only saves points that are >15 meters apart
- **Batch Database Writes**: Saves points in batches of 20 for better performance
- **Power Optimization**: Automatically detects stationary mode and emits power-saving signals
- **Thread-Safe Operations**: All operations are coroutine-safe
- **Real-time Statistics**: Provides buffer stats and tracking state

## Architecture

```
┌─────────────────────┐    ┌──────────────────────┐    ┌─────────────────┐
│   GPS Provider      │───▶│ LocationBufferManager│───▶│  TrekRepository │
│   (SensorData)      │    │                      │    │   (Database)    │
└─────────────────────┘    └──────────────────────┘    └─────────────────┘
                                      │
                                      ▼
                           ┌──────────────────────┐
                           │ Power Optimization   │
                           │ Events (Flow)        │
                           └──────────────────────┘
```

## Key Components

### 1. BufferModels.kt
- `TrackingState`: STOPPED, TRACKING, STATIONARY
- `PowerOptimizationSignal`: GPS control signals
- `BufferConfig`: Configuration parameters
- `BufferStats`: Real-time statistics

### 2. LocationBufferManager.kt
- Main buffer management logic
- Distance filtering using Haversine formula
- Batch saving with SQLDelight transactions
- Stationary mode detection
- Power optimization events

### 3. TrekRepository.saveBatch()
- New method for batch database writes
- Uses SQLDelight transactions for performance
- Handles JSON serialization of RawSensors

## Usage Examples

### Basic Usage

```kotlin
// Initialize (done in AppModule)
val locationBufferManager = LocationBufferManager(
    trekRepository = trekRepository,
    config = BufferConfig(
        minDistanceMeters = 15.0,
        batchSize = 20,
        stationaryTimeoutMinutes = 5L
    )
)

// Start tracking
locationBufferManager.startTracking("trek-id-123")

// Process location updates
val point = Point(
    lat = 18.5677932,
    lon = 73.7673544,
    timestamp = Clock.System.now(),
    // ... other fields
)
locationBufferManager.onNewLocation(point)

// Stop tracking
locationBufferManager.stopTracking()
```

### Integration with TrekTrackingViewModel

```kotlin
class TrekTrackingViewModel(
    private val trekRepository: TrekRepository,
    private val locationBufferManager: LocationBufferManager
) {
    fun startTracking(trekName: String, guideId: String) {
        viewModelScope.launch {
            val trekId = trekRepository.startNewTrek(trekName, guideId)
            locationBufferManager.startTracking(trekId)
            observeBufferManagerEvents()
        }
    }
    
    fun onLocationReceived(point: Point) {
        viewModelScope.launch {
            locationBufferManager.onNewLocation(point)
        }
    }
    
    private fun observeBufferManagerEvents() {
        // Observe power optimization events
        viewModelScope.launch {
            locationBufferManager.powerOptimizationEvents.collect { event ->
                when (event.signal) {
                    PowerOptimizationSignal.STOP_GPS_PROVIDER -> {
                        // Stop GPS to save battery
                    }
                    PowerOptimizationSignal.RESUME_GPS_PROVIDER -> {
                        // Resume GPS tracking
                    }
                    // Handle other signals...
                }
            }
        }
        
        // Observe buffer statistics
        viewModelScope.launch {
            locationBufferManager.bufferStats.collect { stats ->
                // Update UI with buffer stats
                updateUI(stats)
            }
        }
    }
}
```

### Integration with SensorDataCollector

```kotlin
class SensorDataCollector {
    private val locationBufferManager = AppModule.locationBufferManager
    
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val point = Point(
                lat = location.latitude,
                lon = location.longitude,
                timestamp = Clock.System.now(),
                altGps = if (location.hasAltitude()) location.altitude else null,
                accuracyH = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                bearing = if (location.hasBearing()) location.bearing.toDouble() else null,
                battery = getBatteryLevel(),
                rawSensors = getCurrentSensorData()
            )
            
            // Let LocationBufferManager handle distance filtering and batching
            GlobalScope.launch {
                locationBufferManager.onNewLocation(point)
            }
        }
    }
}
```

## Power Optimization

The LocationBufferManager automatically handles power optimization:

### Stationary Mode Detection
- If no new points are added for 5 minutes, enters STATIONARY mode
- Emits `STOP_GPS_PROVIDER` signal to save battery
- UI/Service should stop GPS updates

### Resume from Stationary Mode
- Call `resumeTracking()` when significant motion is detected
- Automatically exits STATIONARY mode and resumes tracking
- Emits `RESUME_GPS_PROVIDER` signal

### Example Power Management

```kotlin
// In your location service
locationBufferManager.powerOptimizationEvents.collect { event ->
    when (event.signal) {
        PowerOptimizationSignal.STOP_GPS_PROVIDER -> {
            locationManager.removeUpdates(locationListener)
            // Start significant motion detection
            startSignificantMotionDetection()
        }
        PowerOptimizationSignal.RESUME_GPS_PROVIDER -> {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, // 1 second
                0f,    // 0 meters
                locationListener
            )
        }
    }
}

// When significant motion detected
private fun onSignificantMotionDetected() {
    locationBufferManager.resumeTracking()
}
```

## Configuration Options

```kotlin
data class BufferConfig(
    val minDistanceMeters: Double = 15.0,      // Minimum distance between points
    val batchSize: Int = 20,                   // Points per batch save
    val stationaryTimeoutMinutes: Long = 5L    // Stationary detection timeout
)
```

### Recommended Configurations

**High Accuracy Mode** (for detailed tracking):
```kotlin
BufferConfig(
    minDistanceMeters = 5.0,
    batchSize = 50,
    stationaryTimeoutMinutes = 2L
)
```

**Battery Saving Mode** (for long treks):
```kotlin
BufferConfig(
    minDistanceMeters = 25.0,
    batchSize = 10,
    stationaryTimeoutMinutes = 10L
)
```

**Default Mode** (balanced):
```kotlin
BufferConfig() // Uses default values
```

## Monitoring and Statistics

### Buffer Statistics
```kotlin
data class BufferStats(
    val currentBufferSize: Int,                    // Current points in buffer
    val totalPointsProcessed: Long,                // Total points processed
    val totalBatchesSaved: Long,                   // Total batches saved
    val lastPointTimestamp: Instant?,              // Last point timestamp
    val lastBatchSaveTimestamp: Instant?,          // Last batch save time
    val averageDistanceBetweenPoints: Double?      // Average distance between points
)
```

### Real-time Monitoring
```kotlin
// Observe buffer stats
locationBufferManager.bufferStats.collect { stats ->
    println("Buffer size: ${stats.currentBufferSize}")
    println("Total points: ${stats.totalPointsProcessed}")
    println("Average distance: ${stats.averageDistanceBetweenPoints}m")
}

// Observe tracking state
locationBufferManager.trackingState.collect { state ->
    when (state) {
        TrackingState.STOPPED -> println("Tracking stopped")
        TrackingState.TRACKING -> println("Actively tracking")
        TrackingState.STATIONARY -> println("Stationary mode - saving battery")
    }
}
```

## Testing

### Manual Testing
```kotlin
// Force flush buffer for testing
locationBufferManager.flushBuffer()

// Get current buffer size
val bufferSize = locationBufferManager.getCurrentBufferSize()

// Get current buffer contents
val currentPoints = locationBufferManager.getCurrentBuffer()
```

### Integration Testing
1. Start tracking with a test trek ID
2. Add points with varying distances
3. Verify distance filtering works correctly
4. Verify batch saves occur at correct intervals
5. Test stationary mode detection
6. Test power optimization events

## Error Handling

The LocationBufferManager handles errors gracefully:

- **Database errors**: Logged but don't crash the app
- **Invalid points**: Filtered out automatically  
- **Coroutine cancellation**: Properly cleaned up
- **Memory management**: Automatic buffer clearing

## Performance Considerations

- **Memory Usage**: Buffer holds max 20 points (configurable)
- **Database Performance**: Batch writes are ~10x faster than individual saves
- **CPU Usage**: Haversine distance calculation is optimized
- **Battery Usage**: Automatic GPS stopping in stationary mode

## Thread Safety

All LocationBufferManager operations are thread-safe:
- Uses coroutines with proper dispatchers
- StateFlow for reactive state management
- Atomic operations for statistics
- Proper synchronization for buffer operations

## Cleanup

Always cleanup resources when done:

```kotlin
// In ViewModel onCleared()
override fun onCleared() {
    locationBufferManager.cleanup()
    super.onCleared()
}

// In Service onDestroy()
override fun onDestroy() {
    locationBufferManager.cleanup()
    super.onDestroy()
}
```

This ensures proper resource cleanup and prevents memory leaks.