package com.via.himalaya.presentation.trekDetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.data.models.Loc
import com.via.himalaya.data.models.NavigatorTrek
import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.RawSensors
import com.via.himalaya.data.models.Trek
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.data.repository.FirebaseAuthRepository
import com.via.himalaya.domain.LocationEmitter
import com.via.himalaya.domain.SensorListener
import com.via.himalaya.domain.model.LocationResponse
import com.via.himalaya.domain.model.calculateBoundingBox
import com.via.himalaya.domain.model.getFlattenedCoordinates
import com.via.himalaya.domain.repo.TrekRepository
import com.via.himalaya.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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

    fun setInitialData(
        coordinatesUrl: String,
        trekId: String
    ) = viewModelScope.launch(Dispatchers.IO) {
        launch {
            val user = authRepository.getCurrentUser()
            _state.update {
                it.copy(userEmail = user?.email)
            }
        }
        launch {
            getTrekMeta(trekId)
        }
        launch {
            getCoordinates(coordinatesUrl, trekId)
        }
    }

    fun getInitialLocation() = viewModelScope.launch {
        locationEmitter.getLocation { locationResponse ->
            _state.update {
                it.copy(initialLocation = locationResponse)
            }
            if(locationResponse is LocationResponse.Location) {
                _state.update {
                    it.copy(liveLocation = locationResponse.loc)
                }
            }
            when(locationResponse) {
                is LocationResponse.Location -> {
//                    val coordinates = trekRepository.prepareSampleTrekCoordinates(
//                        locationResponse.loc.lat,
//                        locationResponse.loc.lon,
//                        "test"
//                    )
//                    val boundingBox = coordinates.geometry.calculateBoundingBox()
//                    val trek = TrekDetail(
//                        id = "test",
//                        name = "test",
//                        location = "India",
//                        distance = "0",
//                        elevation = "0",
//                        boundingBox = boundingBox,
//                        coordinateUrl = ""
//                    )
//                    _state.update {
//                        it.copy(
//                            trek = trek,
//                            geoData = coordinates
//                        )
//                    }
                    checkLocationInBoundingBox(locationResponse.loc)
                }
                is LocationResponse.ErrorFetchingLocation -> {
                    _state.update {
                        it.copy(
                            errorToast = locationResponse.error
                        )
                    }
                }
                else -> {}
            }
        }
    }

    private fun getTrekMeta(trekId: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val trek = trekRepository.getTrek(trekId)
        if (trek is Result.Success && trek.data != null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    trek = trek.data
                )
            }
            getPois(trek.data)
            val currentLocation = _state.value.initialLocation
            if(currentLocation is LocationResponse.Location) {
                checkLocationInBoundingBox(currentLocation.loc)
            }
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorToast = trek.message
                )
            }
        }
    }

    /**
     * POIs are supplementary, so a failure is logged and swallowed - the trail
     * still renders. Treks generated before the POI pipeline have no poiUrl.
     */
    private fun getPois(trek: TrekDetail) = viewModelScope.launch(Dispatchers.IO) {
        val poiUrl = trek.poiUrl
        if (poiUrl.isNullOrBlank()) {
            println("TrekDetailViewModel: No POI bundle for trek ${trek.id}")
            return@launch
        }
        when (val result = trekRepository.getTrekPois(poiUrl, trek.id, trek.poiUpdatedAt)) {
            is Result.Success -> {
                val pois = result.data?.pois.orEmpty()
                println("TrekDetailViewModel: Loaded ${pois.size} POIs for trek ${trek.id}")
                _state.update { it.copy(pois = pois) }
            }
            else -> println("TrekDetailViewModel: Failed to load POIs: ${result.message}")
        }
    }

    private fun checkLocationInBoundingBox(currentLocation: Loc) {
        val trek = _state.value.trek ?: return
        val boundingBox = trek.boundingBox
        if (boundingBox.size >= 4) {
            val isInBox = isLocationInBoundingBox(
                lat = currentLocation.lat,
                lon = currentLocation.lon,
                minLon = boundingBox[0],
                minLat = boundingBox[1],
                maxLon = boundingBox[2],
                maxLat = boundingBox[3]
            )
            println(
                "TrekDetailViewModel: Test location check - " +
                        "lat=${currentLocation.lat}, lon=${currentLocation.lon}, " +
                        "isInBox=$isInBox, boundingBox=$boundingBox"
            )
            _state.update {
                it.copy(isNearTrekStart = isInBox)
            }
        } else {
            _state.update {
                it.copy(isNearTrekStart = false)
            }
        }
    }

    private fun getCoordinates(url: String, trekId: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val coordinates = trekRepository.getTrekCoordinates(url, trekId)
        if (coordinates is Result.Success && coordinates.data != null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    geoData = coordinates.data
                )
            }
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorToast = coordinates.message
                )
            }
        }
    }

    fun startTrekking(trekId: String) {
        sensorListener.startListening()
        val geoData = _state.value.geoData ?: return
        val coordinates = geoData.geometry.getFlattenedCoordinates()

        if (coordinates.isEmpty()) return
        if (!_state.value.isNearTrekStart) return

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
                    it.copy(liveLocation = location)
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
                liveLocation = null
            )
        }
    }

    fun validateAndStartDownload(onValidationSuccess: (TrekDetail) -> Unit) =
        viewModelScope.launch {
            state.value.trek?.let { trek ->
                val downloadedTreks = trekRepository.getDownloadedTreks().data
                val downloadedTrekSize = downloadedTreks?.size
                when {
                    downloadedTreks?.any { it.id == trek.id } == true -> {
                        _state.update {
                            it.copy(errorToast = "Trek is already downloaded")
                        }
                    }

                    downloadedTrekSize != null && downloadedTrekSize >= 3 -> {
                        _state.update {
                            it.copy(errorToast = "Maximum 3 treks can be downloaded. Please delete a trek to download more.")
                        }
                    }

                    else -> {
                        onValidationSuccess(trek)
                    }
                }
            } ?: run {
                _state.update {
                    it.copy(errorToast = "Trek details not present yet...")
                }
            }
        }

    fun clearErrorToast() {
        _state.update { it.copy(errorToast = null) }
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }

}
