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
import com.via.himalaya.domain.model.Feature
import com.via.himalaya.domain.model.LocationResponse
import com.via.himalaya.domain.model.RouteNetwork
import com.via.himalaya.domain.repo.AppConfigRepository
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
    private val authRepository: FirebaseAuthRepository,
    private val appConfigRepository: AppConfigRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrekDetailScreenUIState())
    val state: StateFlow<TrekDetailScreenUIState> = _state.asStateFlow()

    private var locationJob: Job? = null

    init {
        observeFeatures()
    }

    /**
     * Mirrors the remote feature switches into this screen. Config is fetched
     * once at launch and may arrive after the screen opens, so this keeps
     * collecting rather than reading once.
     */
    private fun observeFeatures() = viewModelScope.launch {
        appConfigRepository.config.collect { config ->
            _state.update { it.copy(isPlannerEnabled = config.isEnabled(Feature.PLANNER)) }
        }
    }

    fun setInitialData(trekId: String) = viewModelScope.launch(Dispatchers.IO) {
        launch {
            val user = authRepository.getCurrentUser()
            _state.update {
                it.copy(userEmail = user?.email)
            }
        }
        launch {
            getTrekMeta(trekId)
        }
        // A download saved before routes existed is redone now, if the background
        // job has not done it yet. Returns at once for anything else.
        launch {
            trekRepository.upgradeDownloadedTrek(trekId)
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
            getRoutes(trek.data)
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
                it.copy(
                    isNearTrekStart = isInBox,
                    showAreaPrompt = it.showAreaPrompt || (!isInBox && !it.isTrekking)
                )
            }
        } else {
            _state.update {
                it.copy(isNearTrekStart = false)
            }
        }
    }

    /**
     * The routes file, which replaced the coordinate file: the map, the slider and
     * every distance come from it. There is no fallback to the old file - a trek
     * without one cannot be drawn. That only happens to a download from before
     * routes, opened offline, and the screen says so.
     */
    private fun getRoutes(trek: TrekDetail) = viewModelScope.launch(Dispatchers.IO) {
        val routesUrl = trek.routesUrl
        if (routesUrl.isNullOrBlank()) {
            _state.update { it.copy(needsConnection = true) }
            return@launch
        }
        _state.update { it.copy(isLoading = true) }
        val result = trekRepository.getTrekRoutes(routesUrl, trek.id, trek.routesUpdatedAt)
        val network = result.data?.let(::RouteNetwork)
        val default = network?.defaultRoute()
        if (network == null || default == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorToast = if (network == null) result.message else "Could not read this trek's route"
                )
            }
            return@launch
        }
        _state.update {
            placed(
                it.copy(
                    isLoading = false,
                    needsConnection = false,
                    network = network,
                    routeView = RouteView.of(network, default)
                )
            )
        }
    }

    /** A tap on the map, near a path: add it to the route or swap it in. */
    private fun onMapTap(lon: Double, lat: Double, toleranceM: Double) {
        val current = _state.value
        val network = current.network ?: return
        val view = current.routeView ?: return
        // A light path first, so a tap near a junction picks the path the user
        // can see is tappable; then any path, so an added side trip can come off.
        val sectionId = network.nearestSection(lon, lat, toleranceM, view.others)
            ?: network.nearestSection(lon, lat, toleranceM, network.routes.sections)
            ?: return
        val steps = network.tap(view.steps, sectionId) ?: return
        _state.update { placed(it.copy(routeView = RouteView.of(network, steps))) }
    }

    /** [state] with its POIs measured along its route. */
    private fun placed(state: TrekDetailScreenUIState): TrekDetailScreenUIState {
        val network = state.network
        val view = state.routeView
        if (network == null || view == null) return state
        val (pois, offRoute) = placePois(network, view.steps)
        return state.copy(pois = pois, offRoutePoiIds = offRoute)
    }

    private fun startTrekking(trekId: String) {
        val line = _state.value.routeView?.line.orEmpty()
        if (line.isEmpty()) return
        if (!_state.value.isNearTrekStart) {
            _state.update { it.copy(showAreaPrompt = true) }
            return
        }
        // Only once the hike is really starting: it used to run before the
        // checks, so a press outside the area left the sensors listening.
        sensorListener.startListening()

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

    private fun stopTrekking() {
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

    fun onEvent(event: TrekDetailScreenUIEvent) {
        when (event) {
            is TrekDetailScreenUIEvent.OnStartHike -> startTrekking(event.trekId)
            is TrekDetailScreenUIEvent.OnStopHike -> stopTrekking()
            is TrekDetailScreenUIEvent.ClearErrorToast -> {
                _state.update { it.copy(errorToast = null) }
            }
            is TrekDetailScreenUIEvent.ClearAreaPrompt -> {
                _state.update { it.copy(showAreaPrompt = false) }
            }
            is TrekDetailScreenUIEvent.OnMapTap -> onMapTap(event.lon, event.lat, event.toleranceM)
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }

}
