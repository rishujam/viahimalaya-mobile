package com.via.himalaya.presentation.trekDetail

sealed class TrekDetailScreenUIEvent {

    /** Start Hike, once location permission is granted. */
    data class OnStartHike(val trekId: String) : TrekDetailScreenUIEvent()

    data object OnStopHike : TrekDetailScreenUIEvent()

    data object ClearErrorToast : TrekDetailScreenUIEvent()

    /** The "not in the trekking area" prompt has been shown and is gone. */
    data object ClearAreaPrompt : TrekDetailScreenUIEvent()
}
