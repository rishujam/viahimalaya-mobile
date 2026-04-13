package com.via.himalaya.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Trek(
    val id: String,
    val name: String,
    val guideId: String,
    val startTime: Instant,
    val isSynced: Boolean = false
)

@Serializable
data class TrekWithPoints(
    val trek_meta: Trek,
    val points: List<Point>
)