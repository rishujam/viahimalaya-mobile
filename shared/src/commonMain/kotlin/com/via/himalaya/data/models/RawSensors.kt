package com.via.himalaya.data.models

import kotlinx.serialization.Serializable

@Serializable
data class RawSensors(
    val accelerometerX: Double? = null,
    val accelerometerY: Double? = null,
    val accelerometerZ: Double? = null,
    val gyroscopeX: Double? = null,
    val gyroscopeY: Double? = null,
    val gyroscopeZ: Double? = null,
    val magnetometerX: Double? = null,
    val magnetometerY: Double? = null,
    val magnetometerZ: Double? = null,
    val pressure: Double? = null
)