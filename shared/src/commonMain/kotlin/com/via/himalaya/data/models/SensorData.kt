package com.via.himalaya.data.models

data class SensorData (
    val accelerometer: FloatArray? = null,
    val gyroscope: FloatArray? = null,
    val magnetometer: FloatArray? = null,
    val pressure: Float? = null,
    val altBaro: Float? = null,
    val location: Loc? = null,
    val battery: Int
)