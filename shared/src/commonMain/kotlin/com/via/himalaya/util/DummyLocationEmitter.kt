package com.via.himalaya.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Data class representing a location coordinate
 */
data class LocationCoordinate(
    val longitude: Double,
    val latitude: Double
)

/**
 * Utility class to emit fake location coordinates for testing trek tracking
 */
object DummyLocationEmitter {
    
    /**
     * Emits fake location coordinates along a predefined path every 1 second
     * This simulates a user moving along a trek path
     * 
     * @param coordinates List of coordinates representing the trek path
     * @return Flow of LocationCoordinate that emits every 1 second
     */
    fun emitLocations(coordinates: List<List<Double>>): Flow<LocationCoordinate> = flow {
        if (coordinates.isEmpty()) return@flow
        
        // Emit each coordinate with 1 second delay
        coordinates.forEach { coordinate ->
            if (coordinate.size >= 2) {
                val location = LocationCoordinate(
                    longitude = coordinate[0],
                    latitude = coordinate[1]
                )
                emit(location)
                delay(4000) // 1 second delay
            }
        }
        
        // After reaching the end, keep emitting the last coordinate
        if (coordinates.isNotEmpty() && coordinates.last().size >= 2) {
            val lastCoordinate = coordinates.last()
            val lastLocation = LocationCoordinate(
                longitude = lastCoordinate[0],
                latitude = lastCoordinate[1]
            )
            while (true) {
                emit(lastLocation)
                delay(1000)
            }
        }
    }
    
    /**
     * Calculate distance between two coordinates using Haversine formula
     * Returns distance in meters
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val sinDLat = kotlin.math.sin(dLat / 2)
        val sinDLon = kotlin.math.sin(dLon / 2)
        val cosLat1 = kotlin.math.cos(Math.toRadians(lat1))
        val cosLat2 = kotlin.math.cos(Math.toRadians(lat2))
        
        val a = sinDLat * sinDLat + cosLat1 * cosLat2 * sinDLon * sinDLon
        
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Check if current location is near the trek start point
     * Returns true if within threshold distance (default 100 meters)
     */
    fun isNearTrekStart(
        currentLat: Double,
        currentLon: Double,
        trekStartLat: Double,
        trekStartLon: Double,
        thresholdMeters: Double = 100.0
    ): Boolean {
        val distance = calculateDistance(currentLat, currentLon, trekStartLat, trekStartLon)
        return distance <= thresholdMeters
    }
}
