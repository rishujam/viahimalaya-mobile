package com.via.himalaya.presentation

import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.Trek
import com.via.himalaya.data.repository.TrekRepository
import com.via.himalaya.location.LocationBufferManager
import com.via.himalaya.location.PowerOptimizationEvent
import com.via.himalaya.location.BufferStats
import com.via.himalaya.location.TrackingState as BufferTrackingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Tracking state for the trek recording UI
 */
enum class TrekTrackingState {
    Idle,
    Recording
}

/**
 * UI state for the trek tracking screen
 */
data class TrekTrackingUiState(
    val trackingState: TrekTrackingState = TrekTrackingState.Idle,
    val currentTrek: Trek? = null,
    val currentTrekPoints: List<Point> = emptyList(),
    val bufferStats: BufferStats? = null,
    val bufferTrackingState: BufferTrackingState = BufferTrackingState.STOPPED,
    val lastPowerOptimizationEvent: PowerOptimizationEvent? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for managing trek tracking functionality with LocationBufferManager integration
 * iOS-friendly implementation using simple types and avoiding complex generics
 */
class TrekTrackingViewModel(
    private val trekRepository: TrekRepository,
    private val locationBufferManager: LocationBufferManager
){
    
    // Create our own coroutine scope for iOS compatibility
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(TrekTrackingUiState())
    val uiState: StateFlow<TrekTrackingUiState> = _uiState.asStateFlow()

    private var currentTrekId: String? = null

    /**
     * Starts tracking a new trek
     */
    fun startTracking(trekName: String, guideId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Create new trek
                val trekId = trekRepository.startNewTrek(trekName, guideId)
                currentTrekId = trekId
                
                // Start LocationBufferManager tracking
                locationBufferManager.startTracking(trekId)
                
                // Start observing the trek and its points
                observeTrekData(trekId)
                
                // Start observing buffer manager events
                observeBufferManagerEvents()
                
                _uiState.value = _uiState.value.copy(
                    trackingState = TrekTrackingState.Recording,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to start tracking: ${e.message}"
                )
            }
        }
    }

    /**
     * Stops the current trek tracking
     */
    fun stopTracking() {
        viewModelScope.launch {
            try {
                // Stop LocationBufferManager tracking
                locationBufferManager.stopTracking()
                
                _uiState.value = _uiState.value.copy(
                    trackingState = TrekTrackingState.Idle,
                    currentTrek = null,
                    currentTrekPoints = emptyList(),
                    bufferStats = null,
                    bufferTrackingState = BufferTrackingState.STOPPED,
                    errorMessage = null
                )
                currentTrekId = null
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to stop tracking: ${e.message}"
                )
            }
        }
    }

    /**
     * Handles incoming location data through LocationBufferManager
     */
    fun onLocationReceived(point: Point) {
        val trekId = currentTrekId
        if (trekId == null || _uiState.value.trackingState != TrekTrackingState.Recording) {
            return
        }

        viewModelScope.launch {
            try {
                // Create point with current trek ID and timestamp
                val pointWithTrekId = point.copy(
                    trekId = trekId,
                    timestamp = Clock.System.now()
                )
                
                // Use LocationBufferManager instead of direct repository save
                locationBufferManager.onNewLocation(pointWithTrekId)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to process location: ${e.message}"
                )
            }
        }
    }

    /**
     * Clears any error messages
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Gets the current tracking state
     */
    fun isRecording(): Boolean {
        return _uiState.value.trackingState == TrekTrackingState.Recording
    }

    /**
     * Gets the current trek points count
     */
    fun getCurrentPointsCount(): Int {
        return _uiState.value.currentTrekPoints.size
    }

    /**
     * Gets the latest point for the current trek
     */
    fun getLatestPoint(): Point? {
        return _uiState.value.currentTrekPoints.lastOrNull()
    }

    /**
     * Observes trek data and updates UI state reactively
     */
    private fun observeTrekData(trekId: String) {
        viewModelScope.launch {
            try {
                // Combine trek and points flows for reactive updates
                combine(
                    trekRepository.getTrekById(trekId),
                    trekRepository.getPointsForTrek(trekId)
                ) { trek, points ->
                    Pair(trek, points)
                }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to load trek data: ${e.message}"
                    )
                }
                .collect { (trek, points) ->
                    _uiState.value = _uiState.value.copy(
                        currentTrek = trek,
                        currentTrekPoints = points,
                        isLoading = false
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to observe trek data: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Observes LocationBufferManager events and updates UI state
     */
    private fun observeBufferManagerEvents() {
        viewModelScope.launch {
            // Observe buffer stats
            locationBufferManager.bufferStats.collect { stats ->
                _uiState.value = _uiState.value.copy(bufferStats = stats)
            }
        }
        
        viewModelScope.launch {
            // Observe buffer tracking state
            locationBufferManager.trackingState.collect { bufferState ->
                _uiState.value = _uiState.value.copy(bufferTrackingState = bufferState)
            }
        }
        
        viewModelScope.launch {
            // Observe power optimization events
            locationBufferManager.powerOptimizationEvents.collect { event ->
                _uiState.value = _uiState.value.copy(lastPowerOptimizationEvent = event)
            }
        }
    }

    /**
     * Resume tracking for an existing trek (useful for app restoration)
     */
    fun resumeTracking(trekId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                currentTrekId = trekId
                locationBufferManager.startTracking(trekId)
                observeTrekData(trekId)
                observeBufferManagerEvents()
                
                _uiState.value = _uiState.value.copy(
                    trackingState = TrekTrackingState.Recording,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to resume tracking: ${e.message}"
                )
            }
        }
    }

    /**
     * Resumes tracking from stationary mode (called by significant motion sensor)
     */
    fun resumeFromStationaryMode() {
        locationBufferManager.resumeTracking()
    }

    /**
     * Forces a flush of the current buffer (useful for testing or manual saves)
     */
    fun flushBuffer() {
        viewModelScope.launch {
            try {
                locationBufferManager.flushBuffer()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to flush buffer: ${e.message}"
                )
            }
        }
    }

    /**
     * Clean up resources when ViewModel is no longer needed
     */
    fun onCleared() {
        locationBufferManager.cleanup()
        viewModelScope.cancel()
    }
}