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
    
    // Test locations for bounding box [83.8170309, 28.4063366, 83.9083462, 28.5310466]
    companion object {
        // Location INSIDE bounding box (near bottom-left corner, slightly inside)
        val TEST_LOCATION_INSIDE = com.via.himalaya.util.LocationCoordinate(
            latitude = 28.41,  // Just above minLat (28.4063366)
            longitude = 83.82  // Just right of minLon (83.8170309)
        )
        
        // Location OUTSIDE bounding box (below and left of the box)
        val TEST_LOCATION_OUTSIDE = com.via.himalaya.util.LocationCoordinate(
            latitude = 28.30,  // Below minLat
            longitude = 83.70  // Left of minLon
        )
        
        // Toggle this to test different scenarios
        private const val USE_TEST_LOCATION_INSIDE = false // Change to false to test outside
    }
    
    init {
        // Set initial test location for testing
        setInitialTestLocation()
    }
    
    private fun setInitialTestLocation() {
        val testLocation = if (USE_TEST_LOCATION_INSIDE) {
            TEST_LOCATION_INSIDE
        } else {
            TEST_LOCATION_OUTSIDE
        }
        
        _state.update { it.copy(currentLocation = testLocation) }
    }

    fun getTrekMeta(trekId: String) = viewModelScope.launch {
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
            // After trek is loaded, check if test location is in bounding box
            checkTestLocationInBoundingBox()
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorState = trek.message
                )
            }
        }
    }
    
    private fun checkTestLocationInBoundingBox() {
        val currentLocation = _state.value.currentLocation ?: return
        val trek = _state.value.trek ?: return
        val boundingBox = trek.boundingBox
        
        if (boundingBox != null && boundingBox.size >= 4) {
            val isInBox = isLocationInBoundingBox(
                lat = currentLocation.latitude,
                lon = currentLocation.longitude,
                minLon = boundingBox[0],
                minLat = boundingBox[1],
                maxLon = boundingBox[2],
                maxLat = boundingBox[3]
            )
            
            println("TrekDetailViewModel: Test location check - " +
                "lat=${currentLocation.latitude}, lon=${currentLocation.longitude}, " +
                "isInBox=$isInBox, boundingBox=$boundingBox")
            
            _state.update {
                it.copy(isNearTrekStart = isInBox)
            }
        }
    }

    fun getCoordinates(url: String, trekId: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val coordinates = trekRepository.getTrekCoordinates(url, trekId)
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
        val currentLocation = _state.value.currentLocation
        if (currentLocation != null && !_state.value.isNearTrekStart) {
            return
        }

        _state.update { it.copy(isTrekking = true) }
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            DummyLocationEmitter.emitLocations(coordinates).collect { location ->
                _state.update {
                    it.copy(currentLocation = location)
                }
            }
        }
    }
    
    fun updateCurrentLocation(location: com.via.himalaya.util.LocationCoordinate) {
        if (!_state.value.isTrekking) {
            val trek = _state.value.trek
            val boundingBox = trek?.boundingBox
            val isInBoundingBox = if (boundingBox != null && boundingBox.size >= 4) {
                isLocationInBoundingBox(
                    lat = location.latitude,
                    lon = location.longitude,
                    minLon = boundingBox[0],
                    minLat = boundingBox[1],
                    maxLon = boundingBox[2],
                    maxLat = boundingBox[3]
                )
            } else {
                true
            }
            
            _state.update {
                it.copy(
                    currentLocation = location,
                    isNearTrekStart = isInBoundingBox
                )
            }
        }
    }
    
    private fun isLocationInBoundingBox(
        lat: Double,
        lon: Double,
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double
    ): Boolean {
        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon
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

    fun downloadHike() = viewModelScope.launch {
        state.value.trek?.let { trek ->
            trekRepository.saveTrekMetaData(trek)
//            val result = trekRepository.downloadMap(
//                trekId = trek.id,
//                boundingBox = trek.boundingBox,
//                onProgress = { progress ->
//                    // Update UI with download progress
//                    _state.update { it.copy(downloadProgress = progress) }
//                }
//            )
//            if(result is Result.Success) {
//                println("Trek downloaded successfully for offline use")
//                _state.update { it.copy(isDownloaded = true) }
//            } else {
//                println("Failed to download trek: ${result.message}")
//            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }

}
