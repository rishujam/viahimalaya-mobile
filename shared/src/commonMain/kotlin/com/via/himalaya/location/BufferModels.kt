package com.via.himalaya.location

import kotlinx.datetime.Instant

/**
 * Represents the current state of location tracking
 */
enum class TrackingState {
    STOPPED,
    TRACKING,
    STATIONARY
}

/**
 * Represents different power optimization signals that can be emitted
 */
enum class PowerOptimizationSignal {
    STOP_GPS_PROVIDER,
    RESUME_GPS_PROVIDER,
    ENTER_STATIONARY_MODE,
    EXIT_STATIONARY_MODE
}

/**
 * Data class representing a power optimization event
 */
data class PowerOptimizationEvent(
    val signal: PowerOptimizationSignal,
    val timestamp: Instant,
    val reason: String
)

/**
 * Configuration for the LocationBufferManager
 */
data class BufferConfig(
    val minDistanceMeters: Double = 15.0,
    val batchSize: Int = 20,
    val stationaryTimeoutMinutes: Long = 5L
)

/**
 * Statistics about the buffer state
 */
data class BufferStats(
    val currentBufferSize: Int,
    val totalPointsProcessed: Long,
    val totalBatchesSaved: Long,
    val lastPointTimestamp: Instant?,
    val lastBatchSaveTimestamp: Instant?,
    val averageDistanceBetweenPoints: Double?
)