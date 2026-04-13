# ViaHimalaya Trek Tracking - Local Database & ViewModel Implementation

This document describes the implementation of the Local Database layer and Shared ViewModel logic for trek recording in the ViaHimalaya KMP mobile app.

## Architecture Overview

The implementation follows a clean architecture pattern with the following layers:

```
┌─────────────────────────────────────┐
│           Presentation              │
│    (TrekTrackingViewModel)          │
├─────────────────────────────────────┤
│            Repository               │
│        (TrekRepository)             │
├─────────────────────────────────────┤
│            Database                 │
│    (SQLDelight + Platform Drivers) │
└─────────────────────────────────────┘
```

## Components

### 1. Data Schema (SQLDelight)

**File**: `shared/src/commonMain/sqldelight/com/via/himalaya/database/ViaHimalayaDatabase.sq`

#### Tables:

**Trek Table**:
- `id` (TEXT, Primary Key) - UUID for the trek
- `name` (TEXT) - Trek name
- `guideId` (TEXT) - Guide identifier
- `startTime` (INTEGER) - Unix timestamp
- `isSynced` (INTEGER) - Boolean flag for sync status

**Point Table**:
- `id` (INTEGER, Auto-increment Primary Key)
- `trekId` (TEXT, Foreign Key) - References Trek.id
- `lat` (REAL) - Latitude
- `lon` (REAL) - Longitude
- `timestamp` (INTEGER) - Unix timestamp
- `altGps` (REAL) - GPS altitude
- `altBaro` (REAL) - Barometric altitude
- `accuracyH` (REAL) - Horizontal accuracy
- `accuracyV` (REAL) - Vertical accuracy
- `speed` (REAL) - Speed in m/s
- `bearing` (REAL) - Bearing in degrees
- `battery` (INTEGER) - Battery percentage
- `rawSensorsJson` (TEXT) - JSON string of sensor data

### 2. Data Models

**Files**: 
- `shared/src/commonMain/kotlin/com/via/himalaya/data/models/Trek.kt`
- `shared/src/commonMain/kotlin/com/via/himalaya/data/models/Point.kt`
- `shared/src/commonMain/kotlin/com/via/himalaya/data/models/RawSensors.kt`

#### Key Features:
- **iOS-friendly**: Simple data classes without complex generics
- **Serializable**: All models support JSON serialization
- **Type-safe**: Uses `kotlinx.datetime.Instant` for timestamps
- **Extensible**: RawSensors model supports various sensor types

### 3. Repository Layer

**File**: `shared/src/commonMain/kotlin/com/via/himalaya/data/repository/TrekRepository.kt`

#### Key Methods:

```kotlin
// Start a new trek and return its ID
suspend fun startNewTrek(name: String, guideId: String): String

// Save a GPS point linked to a trek
suspend fun savePoint(trekId: String, point: Point)

// Get unsynced treks with points in JSON format
suspend fun getUnsyncedTreks(): List<TrekWithPoints>

// Reactive data access
fun getTrekById(trekId: String): Flow<Trek?>
fun getPointsForTrek(trekId: String): Flow<List<Point>>
```

#### Features:
- **Reactive**: Uses Kotlin Flows for automatic UI updates
- **Coroutine-based**: All database operations are suspend functions
- **JSON serialization**: Handles RawSensors serialization automatically
- **Error handling**: Graceful handling of JSON parsing errors

### 4. Shared ViewModel

**File**: `shared/src/commonMain/kotlin/com/via/himalaya/presentation/viewmodel/TrekTrackingViewModel.kt`

#### State Management:

```kotlin
enum class TrackingState {
    Idle,
    Recording
}

data class TrekTrackingUiState(
    val trackingState: TrackingState = TrackingState.Idle,
    val currentTrek: Trek? = null,
    val currentTrekPoints: List<Point> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
```

#### Key Methods:

```kotlin
// Start tracking a new trek
fun startTracking(trekName: String, guideId: String)

// Stop the current trek tracking
fun stopTracking()

// Handle incoming location data
fun onLocationReceived(point: Point)

// Resume tracking for existing trek
fun resumeTracking(trekId: String)
```

#### Features:
- **iOS-compatible**: Uses simple CoroutineScope instead of complex ViewModel libraries
- **Reactive**: StateFlow-based UI state management
- **Error handling**: Comprehensive error handling with user-friendly messages
- **Lifecycle-aware**: Proper cleanup with `onCleared()` method

### 5. Dependency Injection

**Files**:
- `shared/src/commonMain/kotlin/com/via/himalaya/di/AppModule.kt`
- `shared/src/androidMain/kotlin/com/via/himalaya/di/AppModuleFactory.android.kt`
- `shared/src/iosMain/kotlin/com/via/himalaya/di/AppModuleFactory.ios.kt`

#### Simple DI Container:
```kotlin
class AppModule(private val databaseDriverFactory: DatabaseDriverFactory) {
    val trekRepository: TrekRepository by lazy {
        TrekRepository(databaseDriverFactory)
    }
    
    fun createTrekTrackingViewModel(): TrekTrackingViewModel {
        return TrekTrackingViewModel(trekRepository)
    }
}
```

## Platform-Specific Setup

### Android Setup

1. **Initialize in Application class**:
```kotlin
class ViaHimalayaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModuleFactory.initialize(this)
    }
}
```

2. **Use in Activity/Fragment**:
```kotlin
class MainActivity : ComponentActivity() {
    private val appModule = AppModuleFactory.create()
    private val viewModel = appModule.createTrekTrackingViewModel()
    
    // Use viewModel...
}
```

### iOS Setup

1. **Use in SwiftUI**:
```swift
struct ContentView: View {
    private let appModule = AppModuleFactory().create()
    private let viewModel: TrekTrackingViewModel
    
    init() {
        viewModel = appModule.createTrekTrackingViewModel()
    }
    
    // Use viewModel...
}
```

## Usage Example

### Starting Trek Recording

```kotlin
// Start tracking
viewModel.startTracking("Morning Hike", "guide-123")

// Observe state changes
viewModel.uiState.collect { state ->
    when (state.trackingState) {
        TrackingState.Recording -> {
            // Update UI to show recording state
            // Display current points count: state.currentTrekPoints.size
        }
        TrackingState.Idle -> {
            // Update UI to show idle state
        }
    }
}
```

### Handling Location Updates

```kotlin
// When GPS location is received
val point = Point(
    trekId = "", // Will be set by ViewModel
    lat = 27.9881,
    lon = 86.9250,
    timestamp = Clock.System.now(),
    altGps = 5364.0,
    battery = 85,
    rawSensors = RawSensors(
        accelerometerX = 0.1,
        accelerometerY = 0.2,
        accelerometerZ = 9.8,
        pressure = 540.0
    )
)

viewModel.onLocationReceived(point)
```

### Syncing Data

```kotlin
// Get unsynced treks for server upload
val unsyncedTreks = repository.getUnsyncedTreks()

unsyncedTreks.forEach { trekWithPoints ->
    // Upload to server
    val json = Json.encodeToString(trekWithPoints)
    uploadToServer(json)
    
    // Mark as synced
    repository.updateTrekSyncStatus(trekWithPoints.trek_meta.id, true)
}
```

## Key Features

### ✅ iOS Compatibility
- Simple data types without complex generics
- No dependency on Android-specific ViewModel libraries
- SKIE-compatible for smooth Swift interop

### ✅ Reactive UI Updates
- Kotlin Flows ensure automatic UI updates
- StateFlow-based state management
- Real-time point tracking

### ✅ Robust Data Handling
- SQLDelight for type-safe database operations
- JSON serialization for sensor data
- Foreign key constraints for data integrity

### ✅ Offline-First Design
- Local database storage
- Sync status tracking
- Batch upload capability

### ✅ Error Handling
- Comprehensive error handling throughout the stack
- User-friendly error messages
- Graceful degradation

## Dependencies

```toml
[versions]
sqldelight = "2.0.2"
kotlinx-coroutines = "1.8.1"
kotlinx-serialization = "1.7.1"
kotlinx-datetime = "0.6.0"
uuid = "0.8.4"
```

## Build Configuration

The project is configured with:
- SQLDelight plugin for code generation
- Kotlin Serialization for JSON handling
- SKIE for iOS compatibility
- Platform-specific database drivers

## Testing

The implementation can be tested using the provided example in:
`shared/src/commonMain/kotlin/com/via/himalaya/example/TrekTrackingExample.kt`

This example demonstrates:
- Starting and stopping trek recording
- Simulating GPS point reception
- Observing state changes
- Retrieving unsynced data

## Next Steps

1. **Location Services Integration**: Implement platform-specific location services
2. **Background Processing**: Add background location tracking
3. **Data Validation**: Implement point validation and filtering
4. **Sync Optimization**: Add incremental sync and conflict resolution
5. **Performance**: Implement database indexing and query optimization