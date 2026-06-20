package com.via.himalaya.presentation.trekDetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.domain.model.getFlattenedCoordinates
import com.via.himalaya.domain.repo.TrekRepository
import com.via.himalaya.util.DummyLocationEmitter
import com.via.himalaya.util.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrekDetailViewModel(
    private val trekRepository: TrekRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrekDetailScreenUIState())
    val state: StateFlow<TrekDetailScreenUIState> = _state.asStateFlow()
    
    private var locationJob: Job? = null

    fun getTrek(trekId: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val trek = trekRepository.getTrek(trekId)
        if(trek is Result.Success && trek.data != null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    trek = trek.data,
                    errorState = null
                )
            }
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorState = trek.message
                )
            }
        }
    }

    fun getCoordinates(url: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val coordinates = trekRepository.getTrekCoordinates(url)
        if(coordinates is Result.Success && coordinates.data != null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    geoData = coordinates.data,
                    errorState = null
                )
            }
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorState = coordinates.message
                )
            }
        }
    }
    
    fun startTrekking() {
        val geoData = _state.value.geoData ?: return
        val coordinates = geoData.geometry.getFlattenedCoordinates()
        
        if (coordinates.isEmpty()) return
        
        // Check if user is near trek start point before starting
        val currentLocation = _state.value.currentLocation
        if (currentLocation != null) {
            val startPoint = coordinates.firstOrNull()
            val isNear = if (startPoint != null && startPoint.size >= 2) {
                DummyLocationEmitter.isNearTrekStart(
                    currentLat = currentLocation.latitude,
                    currentLon = currentLocation.longitude,
                    trekStartLat = startPoint[1],
                    trekStartLon = startPoint[0],
                    thresholdMeters = 100.0
                )
            } else {
                true // Allow if no start point defined
            }
            
            if (!isNear) {
                // User is not near start point, don't start trekking
                _state.update { it.copy(isNearTrekStart = false) }
                return
            }
        }
        
        // Update state to indicate trekking has started
        _state.update { it.copy(isTrekking = true, isNearTrekStart = true) }
        
        // Cancel any existing location job
        locationJob?.cancel()
        
        // Start emitting fake locations
        locationJob = viewModelScope.launch {
            DummyLocationEmitter.emitLocations(coordinates).collect { location ->
                _state.update {
                    it.copy(currentLocation = location)
                }
            }
        }
    }
    
    fun updateCurrentLocation(location: com.via.himalaya.util.LocationCoordinate) {
        // This can be called to update location before starting trek
        if (!_state.value.isTrekking) {
            val geoData = _state.value.geoData
            val coordinates = geoData?.geometry?.getFlattenedCoordinates()
            val startPoint = coordinates?.firstOrNull()
            
            val isNear = if (startPoint != null && startPoint.size >= 2) {
                DummyLocationEmitter.isNearTrekStart(
                    currentLat = location.latitude,
                    currentLon = location.longitude,
                    trekStartLat = startPoint[1],
                    trekStartLon = startPoint[0],
                    thresholdMeters = 100.0
                )
            } else {
                true
            }
            
            _state.update {
                it.copy(
                    currentLocation = location,
                    isNearTrekStart = isNear
                )
            }
        }
    }
    
    fun stopTrekking() {
        locationJob?.cancel()
        locationJob = null
        _state.update {
            it.copy(
                isTrekking = false,
                currentLocation = null,
                isNearTrekStart = false
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }

}
