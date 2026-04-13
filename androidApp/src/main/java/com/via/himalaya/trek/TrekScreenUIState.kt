package com.via.himalaya.trek

import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.Trek

data class TrekScreenUIState(
    val isRecording: Boolean = false,
    val currentTrek: Trek? = null,
    val currentTrekPoints: List<Point> = emptyList(),
)
