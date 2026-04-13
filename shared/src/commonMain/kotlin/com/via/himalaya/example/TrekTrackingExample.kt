package com.via.himalaya.example

import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.RawSensors
import com.via.himalaya.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Example usage of the Trek Tracking system
 * This demonstrates how to use the ViewModel and Repository for trek recording
 */
class TrekTrackingExample(private val appModule: AppModule) {
    
    private val viewModel = appModule.createTrekTrackingViewModel()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun demonstrateUsage() {
        // Observe UI state changes
        scope.launch {
            viewModel.uiState.collect { state ->
                println("Tracking State: ${state.trackingState}")
                println("Current Trek: ${state.currentTrek?.name}")
                println("Points Count: ${state.currentTrekPoints.size}")
                
                state.errorMessage?.let { error ->
                    println("Error: $error")
                    viewModel.clearError()
                }
            }
        }
        
        // Start tracking a new trek
        startNewTrek()
    }
    
    private fun startNewTrek() {
        println("Starting new trek...")
        viewModel.startTracking("Morning Hike to Base Camp", "guide-123")
        
        // Simulate receiving location updates
        simulateLocationUpdates()
    }
    
    private fun simulateLocationUpdates() {
        scope.launch {
            // Wait a bit for trek to be created
            kotlinx.coroutines.delay(1000)
            
            // Simulate GPS points
            val samplePoints = listOf(
                createSamplePoint(27.9881, 86.9250, 5364.0), // Everest Base Camp area
                createSamplePoint(27.9885, 86.9255, 5365.0),
                createSamplePoint(27.9890, 86.9260, 5366.0),
                createSamplePoint(27.9895, 86.9265, 5367.0),
                createSamplePoint(27.9900, 86.9270, 5368.0)
            )
            
            samplePoints.forEach { point ->
                println("Receiving location: ${point.lat}, ${point.lon}")
                viewModel.onLocationReceived(point)
                kotlinx.coroutines.delay(2000) // 2 seconds between points
            }
            
            // Stop tracking after receiving all points
            kotlinx.coroutines.delay(3000)
            stopTracking()
        }
    }
    
    private fun createSamplePoint(lat: Double, lon: Double, altitude: Double): Point {
        return Point(
            trekId = "", // Will be set by ViewModel
            lat = lat,
            lon = lon,
            timestamp = Clock.System.now(),
            altGps = altitude,
            altBaro = altitude - 2.0, // Simulate barometric altitude
            accuracyH = 3.5,
            accuracyV = 5.0,
            speed = 1.2, // m/s
            bearing = 45.0,
            battery = 85,
            rawSensors = RawSensors(
                accelerometerX = 0.1,
                accelerometerY = 0.2,
                accelerometerZ = 9.8,
                gyroscopeX = 0.01,
                gyroscopeY = 0.02,
                gyroscopeZ = 0.01,
                pressure = 540.0, // hPa at high altitude
                temperature = -5.0 // Celsius
            )
        )
    }
    
    private fun stopTracking() {
        println("Stopping trek tracking...")
        viewModel.stopTracking()
        
        // Demonstrate getting unsynced treks
        scope.launch {
            val unsyncedTreks = appModule.trekRepository.getUnsyncedTreks()
            println("Unsynced treks: ${unsyncedTreks.size}")
            
            unsyncedTreks.forEach { trekWithPoints ->
                println("Trek: ${trekWithPoints.trek_meta.name}")
                println("Points: ${trekWithPoints.points.size}")
                println("JSON format ready for sync: ${trekWithPoints}")
            }
        }
    }
    
    fun cleanup() {
        viewModel.onCleared()
    }
}