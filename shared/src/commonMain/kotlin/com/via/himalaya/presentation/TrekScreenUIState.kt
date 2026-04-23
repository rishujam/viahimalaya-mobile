package com.via.himalaya.presentation

import com.via.himalaya.data.models.Point

data class TrekScreenUIState(
    val trekState: TrekState = TrekState.Idle,
    val trekId: String? = null,
    val pointsBuffer: List<Point> = emptyList(),
)

enum class TrekState {
    Idle,
    Paused,
    Recording
}
