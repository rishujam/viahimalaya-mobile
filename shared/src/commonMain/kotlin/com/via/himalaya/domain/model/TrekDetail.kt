package com.via.himalaya.domain.model

data class TrekDetail(
    val id: String,
    val name: String,
    val location: String,
    val distance: String,
    val elevation: String,
    val boundingBox: List<Double>,
    val coordinateUrl: String
)
