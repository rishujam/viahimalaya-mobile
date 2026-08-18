package com.via.himalaya.presentation.trekPlan

import com.via.himalaya.data.models.TrekPlan

data class TrekPlanScreenUIState(
    val savedPlans: List<TrekPlan> = emptyList(),
    /**
     * True until the first read finishes, so the screen can hold off on showing
     * the builder. Without it a trek with saved plans flashes an empty builder
     * before they arrive.
     */
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** Set right after a save so the screen can open that plan. */
    val lastSavedPlanId: Long? = null,
    val errorToast: String? = null
)
