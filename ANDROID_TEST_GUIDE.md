# ViaHimalaya Android Database Test Guide

## 🚀 Quick Start Testing

### 1. Build and Install
```bash
cd viahimalaya-mobile
./gradlew androidApp:assembleDebug
# Install the APK on your device or use Android Studio
```

### 2. App Launch Logs
When you start the app, check the Android logcat for these messages:

```
D/ViaHimalaya: 🚀 ViaHimalaya Application Starting...
D/ViaHimalaya: 📊 Initializing Database...
D/ViaHimalaya: 🗃️ Database Tables Status:
D/ViaHimalaya:    📋 Treks Table: 0 records
D/ViaHimalaya:    📋 No treks found - database is empty
D/ViaHimalaya:    💡 Use 'Add Sample Trek' button to test the database
D/ViaHimalaya:    🔄 Unsynced Treks: 0
D/ViaHimalaya: ✅ Database initialization complete!
```

### 3. Test Interface Features

The app provides a comprehensive test interface with these buttons:

#### **"Add Sample Trek" Button**
- Creates a new trek: "Test Everest Base Camp Trek"
- Guide ID: "guide-sherpa-001"
- Adds 3 sample GPS points with realistic Himalayan coordinates
- Includes sensor data (accelerometer, pressure, temperature)

#### **"Load Points" Button**
- Displays all GPS points for the first trek
- Shows coordinates, altitude, speed, battery, and sensor data
- Updates in real-time using Kotlin Flows

#### **"Get Unsynced Treks" Button**
- Retrieves all unsynced treks in JSON format
- Prints detailed JSON to logcat for inspection
- Perfect for testing server sync functionality

### 4. Expected Test Flow

1. **Launch App** → Check logs for database initialization
2. **Tap "Add Sample Trek"** → Creates trek with 3 points
3. **Tap "Load Points"** → Displays the points in UI
4. **Tap "Get Unsynced Treks"** → Check logcat for JSON output

### 5. Logcat Commands

```bash
# Filter ViaHimalaya logs
adb logcat -s ViaHimalaya

# Or use Android Studio Logcat with filter: "ViaHimalaya"
```

## 📊 Expected Database Structure

### Treks Table
```sql
CREATE TABLE Trek (
    id TEXT PRIMARY KEY,           -- UUID
    name TEXT NOT NULL,            -- "Test Everest Base Camp Trek"
    guideId TEXT NOT NULL,         -- "guide-sherpa-001"
    startTime INTEGER NOT NULL,    -- Unix timestamp
    isSynced INTEGER DEFAULT 0     -- Boolean (0 = false)
);
```

### Points Table
```sql
CREATE TABLE Point (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trekId TEXT NOT NULL,          -- Foreign key to Trek.id
    lat REAL NOT NULL,             -- 27.9881 (Everest Base Camp area)
    lon REAL NOT NULL,             -- 86.9250
    timestamp INTEGER NOT NULL,    -- Unix timestamp
    altGps REAL,                   -- 5364.0 (GPS altitude in meters)
    altBaro REAL,                  -- 5362.0 (Barometric altitude)
    accuracyH REAL,                -- 3.5 (Horizontal accuracy in meters)
    accuracyV REAL,                -- 5.0 (Vertical accuracy in meters)
    speed REAL,                    -- 1.2 (Speed in m/s)
    bearing REAL,                  -- 45.0 (Bearing in degrees)
    battery INTEGER,               -- 85 (Battery percentage)
    rawSensorsJson TEXT            -- JSON string of sensor data
);
```

## 🔍 Sample Data Generated

### Trek Data
```json
{
  "id": "uuid-generated",
  "name": "Test Everest Base Camp Trek",
  "guideId": "guide-sherpa-001",
  "startTime": "2024-04-13T08:50:00Z",
  "isSynced": false
}
```

### Point Data (3 points created)
```json
{
  "id": 1,
  "trekId": "uuid-generated",
  "lat": 27.9881,
  "lon": 86.9250,
  "timestamp": "2024-04-13T08:50:00Z",
  "altGps": 5364.0,
  "altBaro": 5362.0,
  "accuracyH": 3.5,
  "accuracyV": 5.0,
  "speed": 1.2,
  "bearing": 45.0,
  "battery": 85,
  "rawSensorsJson": "{\"accelerometerX\":0.1,\"accelerometerY\":0.2,\"accelerometerZ\":9.8,\"gyroscopeX\":0.01,\"gyroscopeY\":0.02,\"gyroscopeZ\":0.01,\"pressure\":540.0,\"temperature\":-5.0}"
}
```

## 🧪 Testing Scenarios

### Scenario 1: Fresh Install
- App starts with empty database
- Logs show "0 records" for both tables
- UI shows empty state

### Scenario 2: Add Sample Data
- Tap "Add Sample Trek"
- Logs show trek creation and point insertion
- UI updates to show 1 trek and 3 points

### Scenario 3: Data Persistence
- Close and reopen app
- Data should persist
- Logs show existing data on startup

### Scenario 4: JSON Export
- Tap "Get Unsynced Treks"
- Check logcat for properly formatted JSON
- Verify all trek and point data is included

## 🐛 Troubleshooting

### Database Not Created
- Check if Application class is registered in AndroidManifest.xml
- Verify SQLDelight plugin is applied
- Look for initialization errors in logs

### No Data Showing
- Check if AppModuleFactory.initialize() is called
- Verify database driver factory is working
- Look for repository errors in logs

### JSON Format Issues
- Check rawSensorsJson serialization
- Verify all fields are properly mapped
- Look for serialization errors in logs

## 📱 Altitude Measurement on Android

### GPS Altitude
- Available on all Android devices
- Uses `Location.getAltitude()`
- Accuracy: ±10-20 meters

### Barometric Altitude
- Requires pressure sensor
- More accurate for relative changes
- Check availability: `sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)`

### Implementation Example
```kotlin
// Check for pressure sensor
val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
if (pressureSensor != null) {
    // Device supports barometric altitude
    val altitude = SensorManager.getAltitude(
        SensorManager.PRESSURE_STANDARD_ATMOSPHERE, 
        pressureValue
    )
}
```

## ✅ Success Criteria

- [ ] App launches without crashes
- [ ] Database tables are created successfully
- [ ] Sample trek and points are inserted
- [ ] Data persists across app restarts
- [ ] JSON export works correctly
- [ ] All logs show expected messages
- [ ] UI updates reactively with database changes

## 🔗 Next Steps

After successful testing:
1. Integrate real GPS location services
2. Add barometric pressure sensor support
3. Implement background location tracking
4. Add server sync functionality
5. Implement data validation and error handling