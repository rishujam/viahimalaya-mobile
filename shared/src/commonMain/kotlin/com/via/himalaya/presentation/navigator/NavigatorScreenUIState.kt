package com.via.himalaya.presentation.navigator

import com.via.himalaya.data.models.Point

data class NavigatorScreenUIState(
    val trekState: NavigatorState = NavigatorState.Idle,
    val trekId: String? = null,
    val pointsBuffer: List<Point> = emptyList(),
    val trekName: String = "",
    val allPoints: List<Point> = emptyList()
)

enum class NavigatorState {
    Idle,
    Paused,
    Recording
}
