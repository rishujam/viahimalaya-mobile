package com.via.himalaya.presentation.trekDetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.data.models.Loc
import com.via.himalaya.data.models.NavigatorTrek
import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.RawSensors
import com.via.himalaya.data.repository.FirebaseAuthRepository
import com.via.himalaya.domain.LocationEmitter
import com.via.himalaya.domain.SensorListener
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
import kotlinx.datetime.Clock

class TrekDetailViewModel(
    private val trekRepository: TrekRepository,
    private val locationEmitter: LocationEmitter,
    private val sensorListener: SensorListener,
    private val authRepository: FirebaseAuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrekDetailScreenUIState())
    val state: StateFlow<TrekDetailScreenUIState> = _state.asStateFlow()
    
    private var locationJob: Job? = null
    
    init {
        setInitialData()
    }
    
    private fun setInitialData() = viewModelScope.launch {
        val loc = locationEmitter.getLocation()
        val user = authRepository.getCurrentUser()
        _state.update { it.copy(currentLocation = loc, userEmail = user?.email) }
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
            checkLocationInBoundingBox()
            sensorListener.startListening()
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorState = trek.message
                )
            }
        }
    }
    
    private fun checkLocationInBoundingBox() {
        val currentLocation = _state.value.currentLocation ?: return
        val trek = _state.value.trek ?: return
        val boundingBox = trek.boundingBox
        
        if (boundingBox != null && boundingBox.size >= 4) {
            val isInBox = isLocationInBoundingBox(
                lat = currentLocation.lat,
                lon = currentLocation.lon,
                minLon = boundingBox[0],
                minLat = boundingBox[1],
                maxLon = boundingBox[2],
                maxLat = boundingBox[3]
            )
            
            println("TrekDetailViewModel: Test location check - " +
                "lat=${currentLocation.lat}, lon=${currentLocation.lon}, " +
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
    
    fun startTrekking(trekId: String){
        val geoData = _state.value.geoData ?: return
        val coordinates = geoData.geometry.getFlattenedCoordinates()
        
        if (coordinates.isEmpty()) return
        val currentLocation = _state.value.currentLocation
        if (currentLocation != null && !_state.value.isNearTrekStart) {
            return
        }

        _state.update { it.copy(isTrekking = true) }
        val user = _state.value.userEmail
        val navigatorTrekId = "$user/${Clock.System.now().toEpochMilliseconds()}"
        viewModelScope.launch {
            trekRepository.saveNavigatorTrek(
                NavigatorTrek(
                    id = navigatorTrekId,
                    trekId = trekId,
                    user = user.orEmpty()
                )
            )
        }
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationEmitter.getLiveLocationStream().collect { location ->
                _state.update {
                    it.copy(currentLocation = location)
                }
                val sensorData = sensorListener.getSensorData()
                val rawSensors = RawSensors(
                    accelerometerX = sensorData.accelerometer?.getOrNull(0)?.toDouble(),
                    accelerometerY = sensorData.accelerometer?.getOrNull(1)?.toDouble(),
                    accelerometerZ = sensorData.accelerometer?.getOrNull(2)?.toDouble(),
                    gyroscopeX = sensorData.gyroscope?.getOrNull(0)?.toDouble(),
                    gyroscopeY = sensorData.gyroscope?.getOrNull(1)?.toDouble(),
                    gyroscopeZ = sensorData.gyroscope?.getOrNull(2)?.toDouble(),
                    magnetometerX = sensorData.magnetometer?.getOrNull(0)?.toDouble(),
                    magnetometerY = sensorData.magnetometer?.getOrNull(1)?.toDouble(),
                    magnetometerZ = sensorData.magnetometer?.getOrNull(2)?.toDouble(),
                    pressure = sensorData.pressure?.toDouble()
                )
                trekRepository.updateNavigatorTrek(
                    navigatorTrekId,
                    listOf(
                        Point(
                            lat = location.lat,
                            lon = location.lon,
                            altBaro = sensorData.altBaro?.toDouble(),
                            altGps = location.altitude,
                            timestamp = Clock.System.now().toEpochMilliseconds(),
                            accuracyH = location.accH,
                            accuracyV = location.accV,
                            battery = sensorData.battery,
                            rawSensors = rawSensors,
                            speed = location.speed,
                            bearing = location.bearing
                        )
                    )
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
