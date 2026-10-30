package com.via.himalaya.presentation.addPoi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benasher44.uuid.uuid4
import com.via.himalaya.data.local.UserPreferences
import com.via.himalaya.data.models.CustomPoi
import com.via.himalaya.data.models.Loc
import com.via.himalaya.data.models.LocationSource
import com.via.himalaya.domain.LocationEmitter
import com.via.himalaya.domain.PhotoStore
import com.via.himalaya.domain.model.CustomPoiRules
import com.via.himalaya.domain.model.LocationResponse
import com.via.himalaya.domain.model.TrekArea
import com.via.himalaya.domain.repo.CustomPoiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

class AddPoiViewModel(
    private val customPoiRepository: CustomPoiRepository,
    private val locationEmitter: LocationEmitter,
    private val photoStore: PhotoStore,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private companion object {
        /**
         * The emitter waits for an accurate fix, and indoors or under cloud it
         * may never come. Without a limit the form would say "Getting your
         * location..." for good.
         */
        const val LOCATION_TIMEOUT_MS = 20_000L

        const val OUTSIDE_AREA =
            "You're outside the trekking area. Tap Edit to place the pin on the map."
        const val NO_LOCATION_PERMISSION =
            "Location is off for this app. Tap Edit to place the pin on the map."
        const val LOCATION_SWITCHED_OFF =
            "Location is switched off on this phone. Turn it on, or tap Edit to place the pin on the map."
        const val NO_FIX =
            "Couldn't get your location. Tap Edit to place the pin on the map."
    }

    private val _state = MutableStateFlow(AddPoiUiState())
    val state: StateFlow<AddPoiUiState> = _state.asStateFlow()

    /**
     * Read once at startup: it is only consulted when the form is opened, which
     * is always long after this has returned.
     */
    private var agreedToCurrentTerms = false

    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            agreedToCurrentTerms =
                userPreferences.getAcceptedContentTerms() == CustomPoiRules.CONTENT_TERMS_VERSION
        }
    }

    /**
     * Opens the form for [trekId].
     *
     * [knownLocation] is whatever fix the screen already has - it fills the
     * location at once, so the form is not empty while a fresh one is awaited.
     * [canUseLocation] is the location permission: the emitter does not check it
     * (it would throw on a request), so asking without it is not an option.
     */
    fun open(
        trekId: String,
        boundingBox: List<Double>,
        knownLocation: Loc?,
        canUseLocation: Boolean
    ) {
        if (_state.value.isOpen) return

        val usable = knownLocation?.takeIf { TrekArea.contains(it.lat, it.lon, boundingBox) }
        _state.value = AddPoiUiState(
            isOpen = true,
            trekId = trekId,
            boundingBox = boundingBox,
            location = usable?.toGpsLocation(),
            termsAgreedBefore = agreedToCurrentTerms,
            locationMessage = if (!canUseLocation && usable == null) NO_LOCATION_PERMISSION else null
        )
        if (canUseLocation) fetchCurrentLocation(explicit = false)
    }

    /** Throws away the draft. */
    fun dismiss() {
        locationJob?.cancel()
        _state.value = AddPoiUiState(termsAgreedBefore = agreedToCurrentTerms)
    }

    fun onPhotoChosen(uri: String) {
        _state.update { it.copy(photoUri = uri) }
    }

    fun onNoteChanged(value: String) {
        _state.update { it.copy(note = value.take(CustomPoiRules.MAX_NOTE_CHARS)) }
    }

    fun onTermsChecked(checked: Boolean) {
        _state.update { it.copy(termsChecked = checked) }
    }

    fun startPicking() {
        _state.update { if (it.isOpen) it.copy(isPicking = true) else it }
    }

    fun cancelPicking() {
        _state.update { it.copy(isPicking = false) }
    }

    /** The pin, confirmed. */
    fun onLocationPicked(lat: Double, lon: Double) {
        if (!TrekArea.contains(lat, lon, _state.value.boundingBox)) {
            // The picker does not let this through, so it is a guard, not a flow.
            _state.update { it.copy(isPicking = false, message = "That spot is outside the trekking area.") }
            return
        }
        // A fix that arrives after this must not undo what the user just chose.
        locationJob?.cancel()
        _state.update {
            it.copy(
                isPicking = false,
                isLocating = false,
                location = PoiLocation(lat, lon, LocationSource.MAP),
                locationMessage = null
            )
        }
    }

    /** "Use my current location": the way back from a pin the user moved. */
    fun useCurrentLocation(canUseLocation: Boolean) {
        if (!canUseLocation) {
            _state.update { it.copy(locationMessage = NO_LOCATION_PERMISSION) }
            return
        }
        fetchCurrentLocation(explicit = true)
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun save() {
        val form = _state.value
        val photoUri = form.photoUri
        val location = form.location
        val trekId = form.trekId
        if (!form.canSave || photoUri == null || location == null || trekId == null) return

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // Not cancellable: if the user leaves the screen while this runs, the
            // place they just took the trouble to add must still be written.
            withContext(NonCancellable) {
                var photoPath: String? = null
                try {
                    val id = uuid4().toString()
                    photoPath = photoStore.prepare(photoUri, id)
                    if (photoPath == null) {
                        _state.update {
                            it.copy(isSaving = false, message = "Couldn't read that photo. Please choose another.")
                        }
                        return@withContext
                    }
                    customPoiRepository.save(
                        CustomPoi(
                            id = id,
                            trekId = trekId,
                            lat = location.lat,
                            lon = location.lon,
                            locationSource = location.source,
                            accuracyM = location.accuracyM,
                            description = form.note.trim().ifEmpty { null },
                            photoPath = photoPath,
                            termsVersion = CustomPoiRules.CONTENT_TERMS_VERSION,
                            createdAt = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Nothing was written, so the copy of the photo is orphaned.
                    photoPath?.let { photoStore.delete(it) }
                    _state.update {
                        it.copy(isSaving = false, message = "Couldn't save this place. Please try again.")
                    }
                    return@withContext
                }

                locationJob?.cancel()
                agreedToCurrentTerms = true
                _state.value = AddPoiUiState(
                    termsAgreedBefore = true,
                    message = "Saved. It will upload when you're online."
                )
                customPoiRepository.requestSync()

                // After the place is safe: this only decides whether the checkbox
                // is shown next time, and failing to remember it costs a tick.
                try {
                    userPreferences.setAcceptedContentTerms(CustomPoiRules.CONTENT_TERMS_VERSION)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("AddPoiViewModel: could not remember the terms agreement: ${e.message}")
                }
            }
        }
    }

    /**
     * Asks the phone for a fresh fix.
     *
     * [explicit] means the user pressed "use my current location": it replaces a
     * pin they placed by hand and says so when it cannot. Without it (the quiet
     * refresh when the form opens) a hand-placed pin is never overwritten and a
     * failure is only reported if there is no location at all.
     */
    private fun fetchCurrentLocation(explicit: Boolean) {
        locationJob?.cancel()
        _state.update { it.copy(isLocating = true, locationMessage = if (explicit) null else it.locationMessage) }
        locationJob = viewModelScope.launch {
            val response = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<LocationResponse> { continuation ->
                    locationEmitter.getLocation { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
            }
            _state.update { it.copy(isLocating = false) }

            when (response) {
                is LocationResponse.Location -> applyFix(response.loc, explicit)
                is LocationResponse.SettingDisabled -> reportNoFix(LOCATION_SWITCHED_OFF, explicit)
                is LocationResponse.ErrorFetchingLocation, null -> reportNoFix(NO_FIX, explicit)
            }
        }
    }

    private fun applyFix(loc: Loc, explicit: Boolean) {
        val form = _state.value
        if (!TrekArea.contains(loc.lat, loc.lon, form.boundingBox)) {
            reportNoFix(OUTSIDE_AREA, explicit)
            return
        }
        if (!explicit && form.location?.source == LocationSource.MAP) return
        _state.update { it.copy(location = loc.toGpsLocation(), locationMessage = null) }
    }

    private fun reportNoFix(message: String, explicit: Boolean) {
        if (explicit || _state.value.location == null) {
            _state.update { it.copy(locationMessage = message) }
        }
    }

    private fun Loc.toGpsLocation() = PoiLocation(lat, lon, LocationSource.GPS, accH)

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }
}
