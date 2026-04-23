package com.via.himalaya.data.models

data class Loc(
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val accH: Double? = null,
    val accV: Double? = null,
    val speed: Double? = null,
    val bearing: Double? = null
)
