package com.via.himalaya.presentation.trekPlan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.data.models.PlannedDay
import com.via.himalaya.data.models.TrekPoi
import com.via.himalaya.domain.repo.TrekPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Saved plans for one trek.
 *
 * Separate from TrekDetailViewModel, which already carries the trek, its POIs,
 * geometry, live location, trekking session and download validation. Plan CRUD
 * has nothing to do with any of that, and the plan screen has its own back stack
 * entry, so both scope cleanly side by side.
 */
class TrekPlanViewModel(
    private val trekPlanRepository: TrekPlanRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrekPlanScreenUIState())
    val state: StateFlow<TrekPlanScreenUIState> = _state.asStateFlow()

    private var trekId: String? = null

    fun load(trekId: String) {
        this.trekId = trekId
        refresh()
    }

    private fun refresh() = viewModelScope.launch {
        val id = trekId ?: return@launch
        try {
            val plans = trekPlanRepository.getPlans(id)
            _state.update { it.copy(savedPlans = plans, isLoading = false) }
        } catch (e: Exception) {
            _state.update {
                it.copy(isLoading = false, errorToast = "Could not read saved plans")
            }
        }
    }

    /**
     * Stores the chosen camps as a plan.
     *
     * Only identity and position are kept per day. Distance and climb are left
     * out on purpose - both are derived from the trek's own data, and a stored
     * copy would keep describing the trek as it was. Reversing Rupin's direction
     * rewrote every dist_along_km in its bundle; a saved distance would have gone
     * on quietly reporting the old figures.
     */
    fun savePlan(camps: List<TrekPoi>) = viewModelScope.launch {
        val id = trekId ?: return@launch
        if (camps.isEmpty()) return@launch

        _state.update { it.copy(isSaving = true) }
        try {
            val planId = trekPlanRepository.createPlan(
                trekId = id,
                days = camps.map { camp ->
                    PlannedDay(
                        poiId = camp.id,
                        campName = camp.name,
                        lat = camp.lat,
                        lon = camp.lon
                    )
                }
            )
            _state.update { it.copy(isSaving = false, lastSavedPlanId = planId) }
            refresh()
        } catch (e: Exception) {
            _state.update {
                it.copy(isSaving = false, errorToast = "Could not save the plan")
            }
        }
    }

    /** Removes the plan only. Any downloaded copy of the trek is left alone. */
    fun deletePlan(planId: Long) = viewModelScope.launch {
        try {
            trekPlanRepository.deletePlan(planId)
            _state.update {
                // Clear the pointer if it referred to the plan just removed, so
                // the screen does not try to open a row that no longer exists.
                it.copy(lastSavedPlanId = it.lastSavedPlanId?.takeIf { saved -> saved != planId })
            }
            refresh()
        } catch (e: Exception) {
            _state.update { it.copy(errorToast = "Could not delete the plan") }
        }
    }

    fun clearErrorToast() {
        _state.update { it.copy(errorToast = null) }
    }
}
