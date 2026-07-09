package com.via.himalaya.data.models

data class Trek(
    val id: String,
    val name: String,
    val location: String,
    val distance: String,
    val elevation: String,
    val boundingBox: List<Double>,
    val coordinateUrl: String,
    val thumbnailUrl: String? = null
)
