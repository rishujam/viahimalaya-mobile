package com.via.himalaya.domain.model

data class SensorData (
    val accelerometer: FloatArray? = null,
    val gyroscope: FloatArray? = null,
    val magnetometer: FloatArray? = null,
    val pressure: Float? = null,
    val altBaro: Float? = null,
    val battery: Int
)