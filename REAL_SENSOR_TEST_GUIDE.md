# 🛰️ ViaHimalaya Real Sensor Data Collection Test Guide

## 🚀 Overview

This guide covers testing the complete real sensor data collection system that gathers actual GPS location, accelerometer, gyroscope, magnetometer, pressure, temperature, humidity, and battery data to create Point model entries in the database.

## 📱 What's Implemented

### ✅ **Real Sensor Data Collection**
- **GPS Location**: Latitude, longitude, altitude, accuracy, speed, bearing
- **Accelerometer**: 3-axis motion detection (X, Y, Z)
- **Gyroscope**: 3-axis rotation detection (X, Y, Z)
- **Magnetometer**: 3-axis magnetic field (compass data)
- **Pressure Sensor**: Barometric pressure for altitude calculation
- **Temperature Sensor**: Ambient temperature
- **Humidity Sensor**: Relative humidity
- **Battery Level**: Current device battery percentage

### ✅ **Permission Management**
- **Location Permissions**: Fine and coarse location access
- **Background Location**: For continuous tracking (Android 10+)
- **Automatic Permission Requests**: User-friendly permission flow
- **Permission Status Display**: Real-time permission status

### ✅ **Real-Time Data Display**
- **Live Sensor Readings**: Real-time display of all sensor data
- **GPS Status**: Signal strength and provider information
- **Sensor Availability**: Shows which sensors are available on device
- **Auto-Save to Database**: Automatic Point creation and storage

## 🧪 Testing Steps

### 1. **Build and Install**
```bash
cd viahimalaya-mobile
./gradlew androidApp:assembleDebug
# Install APK on your device
```

### 2. **App Launch - Check Startup Logs**
When you start the app, check logcat for database initialization:
```bash
adb logcat -s ViaHimalaya
```

Expected logs:
```
D/ViaHimalaya: 🚀 ViaHimalaya Application Starting...
D/ViaHimalaya: 📊 Initializing Database...
D/ViaHimalaya: ✅ Database initialization complete!
```

### 3. **Permission Flow Testing**

#### **Initial State**
- App shows "❌ Location permission required"
- "Grant Permissions" button is visible
- Sensor collection is disabled

#### **Grant Permissions**
1. Tap "Grant Permissions"
2. Android system permission dialog appears
3. Grant "Allow while using app" or "Allow all the time"
4. Status changes to "✅ All permissions granted"

#### **Background Location (Android 10+)**
- After basic location permission, app may request background location
- This enables continuous tracking when app is in background

### 4. **Sensor Data Collection Testing**

#### **Start Sensor Collection**
1. Ensure permissions are granted
2. Tap "Start Sensors" button
3. Check logs for sensor registration:

```
D/SensorCollector: 🚀 Starting sensor data collection...
D/SensorCollector: ✅ Accelerometer registered
D/SensorCollector: ✅ Gyroscope registered
D/SensorCollector: ✅ Magnetometer registered
D/SensorCollector: ✅ Pressure sensor registered
D/SensorCollector: ✅ GPS location updates requested
```

#### **Verify Sensor Availability**
The app will show which sensors are available:
- **Always Available**: Accelerometer, GPS
- **Common on Modern Devices**: Gyroscope, Magnetometer, Pressure
- **Less Common**: Temperature, Humidity

#### **Real-Time Data Display**
Once sensors start, you'll see live data:
```
📊 Live Sensor Data
📍 Location: 37.7749, -122.4194
🏔️ GPS Alt: 15.2m
🏔️ Baro Alt: 14.8m
🏃 Speed: 0.0m/s
🧭 Bearing: 45.0°
🔋 Battery: 85%
🎯 Accuracy: 3.5m
📱 Accelerometer: 0.1, 0.2, 9.8
🌡️ Pressure: 1013.25 hPa
🌡️ Temperature: 22.5°C
```

### 5. **Trek Recording Testing**

#### **Start Trek Recording**
1. Ensure sensors are collecting data
2. Tap "Start Trek" button
3. App creates new trek in database
4. Points are automatically saved as sensor data updates

#### **Monitor Point Creation**
Check logs for automatic point saving:
```
D/SensorCollector: 📊 Complete Point Data:
D/SensorCollector:    📍 GPS: 37.7749, -122.4194
D/SensorCollector:    🏔️ Altitude GPS: 15.2m
D/SensorCollector:    🏔️ Altitude Baro: 14.8m
D/SensorCollector:    🎯 Accuracy H: 3.5m
D/SensorCollector:    🏃 Speed: 0.0m/s
D/SensorCollector:    🧭 Bearing: 45.0°
D/SensorCollector:    🔋 Battery: 85%
D/SensorCollector:    📱 Sensors: RawSensors(accelerometerX=0.1, ...)
```

#### **Stop Trek Recording**
1. Tap "Stop Trek" button
2. Trek is saved with all collected points
3. Data persists in database

### 6. **Data Verification**

#### **View Saved Treks**
- Saved treks appear in the list at bottom of screen
- Tap "View Points" to see point count for each trek
- Status message shows: "📍 Trek 'Real Sensor Trek' has X points"

#### **Database Persistence**
1. Close and reopen app
2. Saved treks should still be visible
3. Database initialization logs show existing data

## 📊 Expected Sensor Data

### **GPS Data**
- **Latitude/Longitude**: Your current location
- **Altitude**: Height above sea level (GPS) and barometric altitude
- **Accuracy**: Typically 3-10 meters outdoors, higher indoors
- **Speed**: 0 when stationary, actual speed when moving
- **Bearing**: Direction of movement (0-360 degrees)

### **Motion Sensors**
- **Accelerometer**: ~9.8 m/s² on Z-axis when device is flat (gravity)
- **Gyroscope**: Near 0 when device is stationary
- **Magnetometer**: Varies based on device orientation and magnetic field

### **Environmental Sensors**
- **Pressure**: ~1013 hPa at sea level, decreases with altitude
- **Temperature**: Ambient temperature (may be affected by device heat)
- **Humidity**: Relative humidity percentage

### **Device Data**
- **Battery**: Current battery percentage
- **Timestamp**: Precise time of each measurement

## 🏔️ Altitude Measurement Testing

### **GPS Altitude**
- Available on all devices with GPS
- Accuracy: ±10-20 meters
- Updates with GPS location changes

### **Barometric Altitude**
- Only on devices with pressure sensor
- More accurate for relative altitude changes
- Calculated using: `SensorManager.getAltitude(PRESSURE_STANDARD_ATMOSPHERE, pressure)`

### **Testing Altitude**
1. Start sensor collection outdoors
2. Note both GPS and barometric altitudes
3. Go up/down stairs or elevation change
4. Observe altitude changes in real-time

## 🔧 Troubleshooting

### **No GPS Signal**
- **Symptoms**: Location shows as unavailable
- **Solutions**: 
  - Go outdoors for better GPS reception
  - Enable high accuracy location mode
  - Wait 1-2 minutes for GPS lock

### **Missing Sensors**
- **Symptoms**: Some sensors show as unavailable
- **Explanation**: Not all devices have all sensors
- **Common Missing**: Temperature, humidity on older devices

### **Permission Issues**
- **Symptoms**: "Location permission required" persists
- **Solutions**:
  - Check app permissions in Android settings
  - Restart app after granting permissions
  - Ensure location services are enabled system-wide

### **No Data Updates**
- **Symptoms**: Sensor data not updating
- **Solutions**:
  - Check if sensors are started
  - Verify permissions are granted
  - Move device to trigger sensor updates

## 📱 Device Compatibility

### **Minimum Requirements**
- **Android 7.0+** (API 24+)
- **GPS capability** (all Android devices)
- **Accelerometer** (standard on all devices)

### **Enhanced Features**
- **Barometric Pressure**: Flagship devices (Samsung Galaxy, Google Pixel, etc.)
- **Gyroscope**: Most modern devices
- **Magnetometer**: Most devices with compass
- **Temperature/Humidity**: Limited availability

### **Testing on Different Devices**
- **Flagship Phones**: All sensors typically available
- **Mid-range Devices**: GPS, accelerometer, gyroscope usually available
- **Budget Devices**: GPS and accelerometer guaranteed

## 🎯 Success Criteria

### ✅ **Basic Functionality**
- [ ] App launches without crashes
- [ ] Permissions are requested and granted
- [ ] GPS location is acquired
- [ ] Basic sensors (accelerometer) are working
- [ ] Points are saved to database

### ✅ **Advanced Features**
- [ ] Barometric altitude calculation
- [ ] All available sensors are detected
- [ ] Real-time data updates in UI
- [ ] Trek recording and playback
- [ ] Data persistence across app restarts

### ✅ **Data Quality**
- [ ] GPS coordinates are accurate
- [ ] Sensor readings are reasonable
- [ ] Timestamps are correct
- [ ] Battery level is accurate
- [ ] All data is properly serialized to JSON

## 🚀 Next Steps

After successful testing:
1. **Background Tracking**: Implement foreground service for continuous tracking
2. **Data Sync**: Upload collected data to server
3. **Data Analysis**: Implement trek analysis and statistics
4. **Power Optimization**: Optimize battery usage for long treks
5. **Offline Maps**: Add offline map support for remote areas

## 📋 Test Checklist

```
□ App builds and installs successfully
□ Database initializes correctly
□ Permission flow works properly
□ GPS location is acquired
□ Sensor data is collected and displayed
□ Trek recording creates database entries
□ Points are automatically saved during tracking
□ Data persists across app restarts
□ All available sensors are detected and used
□ Real-time UI updates work correctly
□ Altitude calculation works (if pressure sensor available)
□ Battery level is accurately reported
□ JSON serialization of sensor data works
□ Trek list displays saved treks
□ Point count is accurate for each trek
```

This comprehensive real sensor data collection system is now ready for field testing and provides the foundation for a complete trek tracking application!