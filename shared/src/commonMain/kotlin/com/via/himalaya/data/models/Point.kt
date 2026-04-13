package com.via.himalaya.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Point(
    val id: Long? = null,
    val trekId: String,
    val lat: Double,
    val lon: Double,
    val timestamp: Instant,
    val altGps: Double? = null,
    val altBaro: Double? = null,
    val accuracyH: Double? = null,
    val accuracyV: Double? = null,
    val speed: Double? = null,
    val bearing: Double? = null,
    val battery: Int? = null,
    val rawSensors: RawSensors? = null
)