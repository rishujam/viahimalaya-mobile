package com.via.himalaya.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class NavigatorTrek(
    val id: String,
    val name: String,
    val guideId: String,
    val startTime: Instant,
    val isSynced: Boolean = false
)

@Serializable
data class TrekWithPoints(
    val trek_meta: NavigatorTrek,
    val points: List<Point>
)