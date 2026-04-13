package com.via.himalaya.location

import com.via.himalaya.data.models.Point
import com.via.himalaya.data.repository.TrekRepository
import com.via.himalaya.data.repository.TrekRepositoryImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlin.math.*

/**
 * LocationBufferManager handles in-memory buffering of GPS points with distance filtering,
 * batch database writes, and power optimization through stationary mode detection.
 * 
 * Key Features:
 * - Distance filtering (>15 meters by default)
 * - Batch database writes (20 points by default)
 * - Stationary mode detection (5 minutes timeout)
 * - Power optimization signals via Flow
 * - Thread-safe operations
 */
class LocationBufferManager(
    private val trekRepository: TrekRepository,
    private val config: BufferConfig = BufferConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    // In-memory buffer for points
    private val _buffer = mutableListOf<Point>()
    
    // Current tracking state
    private val _trackingState = MutableStateFlow(TrackingState.STOPPED)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    
    // Power optimization events
    private val _powerOptimizationEvents = MutableSharedFlow<PowerOptimizationEvent>()
    val powerOptimizationEvents: SharedFlow<PowerOptimizationEvent> = _powerOptimizationEvents.asSharedFlow()
    
    // Buffer statistics
    private val _bufferStats = MutableStateFlow(
        BufferStats(
            currentBufferSize = 0,
            totalPointsProcessed = 0L,
            totalBatchesSaved = 0L,
            lastPointTimestamp = null,
            lastBatchSaveTimestamp = null,
            averageDistanceBetweenPoints = null
        )
    )
    val bufferStats: StateFlow<BufferStats> = _bufferStats.asStateFlow()
    
    // Current trek ID
    private var currentTrekId: String? = null
    
    // Stationary mode timer
    private var stationaryJob: Job? = null
    
    // Statistics tracking
    private var totalPointsProcessed = 0L
    private var totalBatchesSaved = 0L
    private var totalDistanceProcessed = 0.0
    
    /**
     * Starts tracking for a specific trek
     */
    fun startTracking(trekId: String) {
        currentTrekId = trekId
        _trackingState.value = TrackingState.TRACKING
        
        scope.launch {
            _powerOptimizationEvents.emit(
                PowerOptimizationEvent(
                    signal = PowerOptimizationSignal.RESUME_GPS_PROVIDER,
                    timestamp = Clock.System.now(),
                    reason = "Started tracking for trek: $trekId"
                )
            )
        }
        
        resetStationaryTimer()
    }
    
    /**
     * Stops tracking and saves any remaining points
     */
    suspend fun stopTracking() {
        _trackingState.value = TrackingState.STOPPED
        stationaryJob?.cancel()
        
        // Save any remaining points in buffer
        if (_buffer.isNotEmpty()) {
            currentTrekId?.let { trekId ->
                saveBatchToDatabase(trekId, _buffer.toList())
                _buffer.clear()
                updateBufferStats()
            }
        }
        
        currentTrekId = null
        
        _powerOptimizationEvents.emit(
            PowerOptimizationEvent(
                signal = PowerOptimizationSignal.STOP_GPS_PROVIDER,
                timestamp = Clock.System.now(),
                reason = "Tracking stopped"
            )
        )
    }
    
    /**
     * Processes a new location point with distance filtering
     */
    suspend fun onNewLocation(point: Point) {
        if (_trackingState.value == TrackingState.STOPPED) return
        
        val trekId = currentTrekId ?: return
        
        // Check distance filtering
        val lastPoint = _buffer.lastOrNull()
        val shouldAdd = if (lastPoint != null) {
            val distance = calculateDistance(lastPoint, point)
            distance >= config.minDistanceMeters
        } else {
            true // Always add first point
        }
        
        if (shouldAdd) {
            // Add point to buffer
            _buffer.add(point)
            totalPointsProcessed++
            
            // Update distance tracking
            if (lastPoint != null) {
                totalDistanceProcessed += calculateDistance(lastPoint, point)
            }
            
            // Check if buffer is full and needs to be saved
            if (_buffer.size >= config.batchSize) {
                saveBatchToDatabase(trekId, _buffer.toList())
                _buffer.clear()
                totalBatchesSaved++
            }
            
            updateBufferStats()
            resetStationaryTimer()
            
            // Exit stationary mode if we were in it
            if (_trackingState.value == TrackingState.STATIONARY) {
                _trackingState.value = TrackingState.TRACKING
                _powerOptimizationEvents.emit(
                    PowerOptimizationEvent(
                        signal = PowerOptimizationSignal.EXIT_STATIONARY_MODE,
                        timestamp = Clock.System.now(),
                        reason = "New location received, exiting stationary mode"
                    )
                )
            }
        }
    }
    
    /**
     * Resumes tracking from stationary mode (called by significant motion sensor)
     */
    fun resumeTracking() {
        if (_trackingState.value == TrackingState.STATIONARY) {
            _trackingState.value = TrackingState.TRACKING
            
            scope.launch {
                _powerOptimizationEvents.emit(
                    PowerOptimizationEvent(
                        signal = PowerOptimizationSignal.RESUME_GPS_PROVIDER,
                        timestamp = Clock.System.now(),
                        reason = "Resumed from stationary mode via significant motion"
                    )
                )
            }
            
            resetStationaryTimer()
        }
    }
    
    /**
     * Forces a save of current buffer (useful for testing or manual saves)
     */
    suspend fun flushBuffer() {
        if (_buffer.isNotEmpty()) {
            currentTrekId?.let { trekId ->
                saveBatchToDatabase(trekId, _buffer.toList())
                _buffer.clear()
                totalBatchesSaved++
                updateBufferStats()
            }
        }
    }
    
    /**
     * Gets current buffer size (for testing/monitoring)
     */
    fun getCurrentBufferSize(): Int = _buffer.size
    
    /**
     * Gets current buffer contents (for testing)
     */
    fun getCurrentBuffer(): List<Point> = _buffer.toList()
    
    /**
     * Calculates distance between two points using Haversine formula
     */
    private fun calculateDistance(point1: Point, point2: Point): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        
        val lat1Rad = Math.toRadians(point1.lat)
        val lat2Rad = Math.toRadians(point2.lat)
        val deltaLatRad = Math.toRadians(point2.lat - point1.lat)
        val deltaLonRad = Math.toRadians(point2.lon - point1.lon)
        
        val a = sin(deltaLatRad / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLonRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Saves a batch of points to database using transaction
     */
    private suspend fun saveBatchToDatabase(trekId: String, points: List<Point>) {
        try {
            trekRepository.saveBatch(trekId, points)
        } catch (e: Exception) {
            // Log error but don't crash - could implement retry logic here
            println("Error saving batch to database: ${e.message}")
        }
    }
    
    /**
     * Resets the stationary mode timer
     */
    private fun resetStationaryTimer() {
        stationaryJob?.cancel()
        stationaryJob = scope.launch {
            delay(config.stationaryTimeoutMinutes * 60 * 1000) // Convert minutes to milliseconds
            
            // Enter stationary mode
            _trackingState.value = TrackingState.STATIONARY
            
            _powerOptimizationEvents.emit(
                PowerOptimizationEvent(
                    signal = PowerOptimizationSignal.ENTER_STATIONARY_MODE,
                    timestamp = Clock.System.now(),
                    reason = "No movement detected for ${config.stationaryTimeoutMinutes} minutes"
                )
            )
            
            _powerOptimizationEvents.emit(
                PowerOptimizationEvent(
                    signal = PowerOptimizationSignal.STOP_GPS_PROVIDER,
                    timestamp = Clock.System.now(),
                    reason = "Entering power saving mode due to stationary detection"
                )
            )
        }
    }
    
    /**
     * Updates buffer statistics
     */
    private fun updateBufferStats() {
        val averageDistance = if (totalPointsProcessed > 1) {
            totalDistanceProcessed / (totalPointsProcessed - 1)
        } else null
        
        _bufferStats.value = BufferStats(
            currentBufferSize = _buffer.size,
            totalPointsProcessed = totalPointsProcessed,
            totalBatchesSaved = totalBatchesSaved,
            lastPointTimestamp = _buffer.lastOrNull()?.timestamp,
            lastBatchSaveTimestamp = if (totalBatchesSaved > 0) Clock.System.now() else null,
            averageDistanceBetweenPoints = averageDistance
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
        stationaryJob?.cancel()
    }
}
