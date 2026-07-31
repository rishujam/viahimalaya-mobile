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
    val imageUrl: String? = null,
    val poiUrl: String? = null,
    /**
     * Bundle version last seen for this trek. The R2 key never changes, so this
     * is the only signal that a regenerated bundle needs re-downloading.
     */
    val poiUpdatedAt: String? = null,
    /** External write-up. Read from Room for downloaded treks, so it must live here. */
    val detailsUrl: String? = null
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
            imageUrl = imageUrl,
            poiUrl = poiUrl,
            poiUpdatedAt = poiUpdatedAt,
            detailsUrl = detailsUrl
        )
    }

}
