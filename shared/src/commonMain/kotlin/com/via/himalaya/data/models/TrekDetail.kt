package com.via.himalaya.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class TrekDetail(
    @PrimaryKey val id: String,
    val name: String,
    val location: String,
    val distance: String,
    val elevation: String,
    val boundingBox: List<Double>,
    val coordinateUrl: String,
    val imageUrl: String? = null
) {

    fun toTrek(): Trek {
        return Trek(
            id = id,
            name = name,
            location = location,
            distance = distance,
            elevation = elevation,
            coordinateUrl = coordinateUrl,
            boundingBox = boundingBox,
            imageUrl = imageUrl
        )
    }

}
